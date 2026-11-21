import Foundation
import Darwin
import os
import LiteRTLM

@MainActor
final class BenchmarkRunner: ObservableObject {
    static let shared = BenchmarkRunner()

    @Published var status: String = "Ready"
    @Published var logOutput: String = ""
    @Published var isFinished: Bool = false

    private var peakFootprintBytes: UInt64 = 0

    private let systemPrompt = "You are Computer, a concise, friendly voice assistant running fully on this iPad. Keep answers short and conversational. You have no tools, no internet and no way to look anything up or get back to the user later, so never say you will search, check or think about something. Answer right away from what you know, and if you do not know, say so plainly."

    private let turnPrompts: [String] = [
        "What is the capital of France?",
        "Tell me a short joke.",
        "How many legs does a spider have?",
        "What's a good name for a dog?",
        "Explain what a battery does in one sentence.",
        "What is seven times eight?",
        "Give me a tip for sleeping better.",
        "What's the weather like right now?",
        "Who wrote Romeo and Juliet?",
        "Thanks, that's all."
    ]

    private func log(_ message: String) {
        logOutput += message + "\n"
    }

    private func emitResult(_ dictionary: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dictionary, options: [.sortedKeys]),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let line = "SPIKE_RESULT \(jsonString)"
        print(line)
        fflush(stdout)
        log(line)
    }

    private func getResidentFootprintBytes() -> UInt64 {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info>.size / MemoryLayout<natural_t>.size)
        let kerr = withUnsafeMutablePointer(to: &info) { infoPtr in
            infoPtr.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { intPtr in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), intPtr, &count)
            }
        }
        return kerr == KERN_SUCCESS ? UInt64(info.phys_footprint) : 0
    }

    private func getAvailableMemoryBytes() -> UInt64 {
        return UInt64(os_proc_available_memory())
    }

    private func isIncreasedMemoryLimitGranted() -> Bool {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url),
              let string = String(data: data, encoding: .ascii) else {
            return false
        }
        return string.contains("com.apple.developer.kernel.increased-memory-limit")
    }

    func run() async {
        guard !isFinished else { return }
        status = "Scanning for model..."
        log("BenchmarkRunner started")

        ExperimentalFlags.optIntoExperimentalAPIs()
        ExperimentalFlags.enableBenchmark = true

        let docsUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let files = (try? FileManager.default.contentsOfDirectory(at: docsUrl, includingPropertiesForKeys: nil)) ?? []

        guard let modelUrl = files.first(where: {
            $0.pathExtension == "litertlm" || $0.pathExtension == "task" || $0.pathExtension == "gguf"
        }) else {
            let fileList = files.map { $0.lastPathComponent }
            emitResult([
                "event": "error",
                "error": "No model found in Documents directory",
                "files_in_documents": fileList
            ])
            status = "Error: No model found"
            isFinished = true
            return
        }

        let modelName = modelUrl.lastPathComponent
        status = "Loading model \(modelName)..."
        log("Found model: \(modelName)")

        let loadStart = DispatchTime.now()
        var engine: Engine? = nil
        var backendUsed = "gpu"

        do {
            log("Attempting initialization with GPU backend...")
            let config = try EngineConfig(
                modelPath: modelUrl.path,
                backend: .gpu,
                cacheDir: docsUrl.path
            )
            let e = Engine(engineConfig: config)
            try await e.initialize()
            engine = e
            backendUsed = "gpu"
        } catch {
            log("GPU backend failed: \(error). Falling back to CPU backend...")
            do {
                let config = try EngineConfig(
                    modelPath: modelUrl.path,
                    backend: .cpu(),
                    cacheDir: docsUrl.path
                )
                let e = Engine(engineConfig: config)
                try await e.initialize()
                engine = e
                backendUsed = "cpu"
            } catch {
                emitResult([
                    "event": "load_failure",
                    "model": modelName,
                    "error": error.localizedDescription
                ])
                status = "Error initializing model"
                isFinished = true
                return
            }
        }

        guard let validEngine = engine else { return }
        let loadEnd = DispatchTime.now()
        let loadDurationSec = Double(loadEnd.uptimeNanoseconds - loadStart.uptimeNanoseconds) / 1_000_000_000.0

        let initialFootprint = getResidentFootprintBytes()
        let initialAvailable = getAvailableMemoryBytes()
        peakFootprintBytes = max(peakFootprintBytes, initialFootprint)
        let entitlementGranted = isIncreasedMemoryLimitGranted()

        emitResult([
            "event": "init",
            "model": modelName,
            "runtime": "litert-lm",
            "backend": backendUsed,
            "load_duration_seconds": loadDurationSec,
            "increased_memory_limit_granted": entitlementGranted,
            "initial_available_memory_bytes": initialAvailable,
            "initial_resident_footprint_bytes": initialFootprint,
            "peak_footprint_bytes": peakFootprintBytes
        ])

        status = "Creating conversation..."
        let systemMessage = Message(systemPrompt, role: .system)
        let conversationConfig = ConversationConfig(systemMessage: systemMessage)

        let conversation: Conversation
        do {
            conversation = try await validEngine.createConversation(with: conversationConfig)
        } catch {
            emitResult([
                "event": "conversation_failure",
                "model": modelName,
                "error": error.localizedDescription
            ])
            status = "Error creating conversation"
            isFinished = true
            return
        }

        // Run ten turns in order
        var turnResults: [[String: Any]] = []

        for (idx, prompt) in turnPrompts.enumerated() {
            let turnIndex = idx + 1
            status = "Running turn \(turnIndex)/\(turnPrompts.count)..."
            log("Turn \(turnIndex): \(prompt)")

            let turnStart = DispatchTime.now()
            var firstTokenTime: DispatchTime? = nil
            var reply = ""
            var tokenCount = 0

            do {
                let stream = conversation.sendMessageStream(Message(prompt, role: .user))
                for try await chunk in stream {
                    if firstTokenTime == nil {
                        firstTokenTime = DispatchTime.now()
                    }
                    reply += chunk.toString
                    tokenCount += 1
                }
            } catch {
                emitResult([
                    "event": "turn_error",
                    "turn": turnIndex,
                    "prompt": prompt,
                    "error": error.localizedDescription
                ])
                status = "Turn \(turnIndex) failed"
                isFinished = true
                return
            }

            let turnEnd = DispatchTime.now()
            let ttftSec = firstTokenTime != nil
                ? Double(firstTokenTime!.uptimeNanoseconds - turnStart.uptimeNanoseconds) / 1_000_000_000.0
                : Double(turnEnd.uptimeNanoseconds - turnStart.uptimeNanoseconds) / 1_000_000_000.0

            let totalSec = Double(turnEnd.uptimeNanoseconds - turnStart.uptimeNanoseconds) / 1_000_000_000.0
            let decodeSec = firstTokenTime != nil
                ? Double(turnEnd.uptimeNanoseconds - firstTokenTime!.uptimeNanoseconds) / 1_000_000_000.0
                : totalSec

            var benchTokensPerSec: Double? = nil
            var benchTokenCount: Int? = nil
            if let bench = try? conversation.getBenchmarkInfo() {
                if bench.lastDecodeTokensPerSecond > 0 {
                    benchTokensPerSec = bench.lastDecodeTokensPerSecond
                }
                if bench.lastDecodeTokenCount > 0 {
                    benchTokenCount = bench.lastDecodeTokenCount
                }
            }

            let finalTokensPerSec = benchTokensPerSec ?? (decodeSec > 0 ? Double(tokenCount) / decodeSec : 0.0)
            let finalTokenCount = benchTokenCount ?? tokenCount

            let currentFootprint = getResidentFootprintBytes()
            let currentAvailable = getAvailableMemoryBytes()
            peakFootprintBytes = max(peakFootprintBytes, currentFootprint)

            let turnResult: [String: Any] = [
                "event": "turn",
                "turn": turnIndex,
                "prompt": prompt,
                "reply": reply.trimmingCharacters(in: .whitespacesAndNewlines),
                "time_to_first_token_seconds": ttftSec,
                "tokens_per_second": finalTokensPerSec,
                "token_count": finalTokenCount,
                "duration_seconds": totalSec,
                "available_memory_bytes": currentAvailable,
                "resident_footprint_bytes": currentFootprint,
                "peak_footprint_bytes": peakFootprintBytes
            ]

            turnResults.append(turnResult)
            emitResult(turnResult)
        }

        // Cancel test
        status = "Running cancel test..."
        log("Running cancel test...")
        let cancelPrompt = "Tell me a long story about a dragon."
        var cancelSentTime: DispatchTime? = nil
        var cancelFinishedTime: DispatchTime? = nil
        var tokensBeforeCancel = 0
        var tokensAfterCancel = 0

        let cancelTask = Task {
            try? await Task.sleep(nanoseconds: 1_000_000_000) // 1.0 s
            cancelSentTime = DispatchTime.now()
            log("Triggering conversation.cancel()...")
            try? conversation.cancel()
        }

        do {
            let stream = conversation.sendMessageStream(Message(cancelPrompt, role: .user))
            for try await _ in stream {
                if cancelSentTime == nil {
                    tokensBeforeCancel += 1
                } else {
                    tokensAfterCancel += 1
                }
            }
        } catch {
            log("Stream stopped: \(error.localizedDescription)")
        }
        cancelTask.cancel()
        cancelFinishedTime = DispatchTime.now()

        let timeUntilStopSec = (cancelSentTime != nil && cancelFinishedTime != nil)
            ? max(0.0, Double(cancelFinishedTime!.uptimeNanoseconds - cancelSentTime!.uptimeNanoseconds) / 1_000_000_000.0)
            : 0.0

        // Post-cancel follow-up question
        status = "Testing post-cancel query..."
        log("Asking post-cancel query: What is two plus two?")
        var postCancelReply = ""
        var postCancelAnswered = false
        var postCancelError: String? = nil

        do {
            let postStream = conversation.sendMessageStream(Message("What is two plus two?", role: .user))
            for try await chunk in postStream {
                postCancelReply += chunk.toString
            }
            postCancelAnswered = !postCancelReply.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        } catch {
            postCancelError = error.localizedDescription
            postCancelAnswered = false
        }

        let cancelResult: [String: Any] = [
            "event": "cancel_test",
            "cancel_prompt": cancelPrompt,
            "time_until_tokens_stop_seconds": timeUntilStopSec,
            "tokens_before_cancel": tokensBeforeCancel,
            "tokens_after_cancel": tokensAfterCancel,
            "post_cancel_prompt": "What is two plus two?",
            "post_cancel_answered": postCancelAnswered,
            "post_cancel_reply": postCancelReply.trimmingCharacters(in: .whitespacesAndNewlines),
            "post_cancel_error": postCancelError ?? ""
        ]
        emitResult(cancelResult)

        // Summary
        let turn1TTFT = (turnResults.first?["time_to_first_token_seconds"] as? Double) ?? 0.0
        let avgTokensPerSec = turnResults.compactMap { $0["tokens_per_second"] as? Double }.reduce(0.0, +) / Double(max(1, turnResults.count))

        let summaryResult: [String: Any] = [
            "event": "summary",
            "model": modelName,
            "runtime": "litert-lm",
            "backend": backendUsed,
            "turns_completed": turnResults.count,
            "turn_1_ttft_seconds": turn1TTFT,
            "average_tokens_per_second": avgTokensPerSec,
            "peak_footprint_bytes": peakFootprintBytes,
            "post_cancel_working": postCancelAnswered,
            "increased_memory_limit_granted": entitlementGranted,
            "meets_target": turn1TTFT < 1.5 && turnResults.count == 10
        ]
        emitResult(summaryResult)

        status = "Benchmark complete!"
        log("Benchmark finished successfully")
        isFinished = true
    }
}
