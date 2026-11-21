import Foundation
import ComposeApp
import LiteRTLM

private let topK = 64
private let topP: Float = 0.95
private let temperature: Float = 1.0

/// Runs Gemma through LiteRT-LM for the Kotlin `IosLlmEngine`. Kotlin calls every method on the main thread,
/// and every callback goes back on the main queue, so the state below is only touched there.
final class LiteRtLlmBridge: NSObject, LlmBridge {
    private var engine: Engine?
    private var conversation: Conversation?
    private var sendTask: Task<Void, Never>?

    func load(modelPath: String, cacheDir: String, maxNumTokens: Int32, onDone: @escaping (String?) -> Void) {
        Task {
            do {
                let loaded = try await Self.makeEngine(modelPath: modelPath, cacheDir: cacheDir, maxNumTokens: Int(maxNumTokens))
                DispatchQueue.main.async {
                    self.engine = loaded
                    onDone(nil)
                }
            } catch {
                DispatchQueue.main.async { onDone(error.localizedDescription) }
            }
        }
    }

    func startConversation(
        systemInstruction: String,
        history: [String],
        saveNote: ((String) -> String)?,
        onDone: @escaping (String?) -> Void
    ) {
        guard let engine else {
            onDone("The model is not loaded")
            return
        }
        let pending = sendTask
        let messages = history.enumerated().map { index, text in
            Message(text, role: index % 2 == 0 ? .user : .model)
        }
        SaveNoteTool.save = saveNote
        let config = ConversationConfig(
            systemMessage: Message(systemInstruction, role: .system),
            initialMessages: messages,
            tools: saveNote == nil ? [] : [SaveNoteTool()],
            samplerConfig: try? SamplerConfig(topK: topK, topP: topP, temperature: temperature, seed: 0)
        )
        Task {
            // A cancelled reply may still be unwinding, and the engine runs one conversation at a time.
            await pending?.value
            do {
                let fresh = try await engine.createConversation(with: config)
                DispatchQueue.main.async {
                    self.conversation = fresh
                    onDone(nil)
                }
            } catch {
                DispatchQueue.main.async { onDone(error.localizedDescription) }
            }
        }
    }

    func send(text: String, thinking: Bool, onChunk: @escaping (String) -> Void, onDone: @escaping (String?) -> Void) {
        guard let conversation else {
            onDone("There is no conversation")
            return
        }
        sendTask = Task {
            do {
                for try await chunk in conversation.sendMessageStream(
                    Message(text, role: .user), thinkingConfig: thinking ? ThinkingConfig() : nil
                ) {
                    if Task.isCancelled { break }
                    let piece = chunk.toString
                    DispatchQueue.main.async { onChunk(piece) }
                }
                DispatchQueue.main.async { onDone(nil) }
            } catch {
                DispatchQueue.main.async { onDone(error.localizedDescription) }
            }
        }
    }

    /// A cancelled conversation rejects every later request, so Kotlin starts a new one for the next turn.
    func cancel() {
        sendTask?.cancel()
        try? conversation?.cancel()
    }

    func close() {
        cancel()
        conversation = nil
        engine = nil
        sendTask = nil
    }

    private static func makeEngine(modelPath: String, cacheDir: String, maxNumTokens: Int) async throws -> Engine {
        do {
            return try await initializedEngine(modelPath: modelPath, cacheDir: cacheDir, maxNumTokens: maxNumTokens, backend: .gpu)
        } catch {
            return try await initializedEngine(modelPath: modelPath, cacheDir: cacheDir, maxNumTokens: maxNumTokens, backend: .cpu())
        }
    }

    private static func initializedEngine(
        modelPath: String, cacheDir: String, maxNumTokens: Int, backend: Backend
    ) async throws -> Engine {
        let config = try EngineConfig(modelPath: modelPath, backend: backend, maxNumTokens: maxNumTokens, cacheDir: cacheDir)
        let engine = Engine(engineConfig: config)
        try await engine.initialize()
        return engine
    }
}

/// The model's save_note tool. LiteRT-LM builds a fresh instance for every call, so the callback into Kotlin is
/// static, set by `startConversation` on the main thread and only read there.
private struct SaveNoteTool: Tool {
    static let name = SaveNoteKt.SAVE_NOTE_NAME
    static let description = SaveNoteKt.SAVE_NOTE_DESCRIPTION
    static var save: ((String) -> String)?

    @ToolParam(description: SaveNoteKt.SAVE_NOTE_TEXT_DESCRIPTION)
    var text: String

    func run() async throws -> Any {
        await MainActor.run { Self.save?(text) ?? "" }
    }
}
