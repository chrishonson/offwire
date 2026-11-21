import Foundation
import ComposeApp
import onnxruntime

private let frameSamples = 512
private let contextSamples = 64
private let windowSamples = contextSamples + frameSamples
private let stateSize = 256
private let sampleRate: Int64 = 16000
private let intraOpThreads: Int32 = 1

/// Silero VAD through the onnxruntime C API, mirroring `SileroVad.kt` on Android: a 64 sample context
/// carried between 512 sample frames, a 2x1x128 recurrent state, and the raw speech probability as output.
/// Kotlin calls it from one thread at a time, so the lazily built session needs no lock.
final class SileroVadBridge: NSObject, VadBridge {
    private let api: OrtApi?
    private var env: OpaquePointer?
    private var options: OpaquePointer?
    private var session: OpaquePointer?
    private var memoryInfo: OpaquePointer?
    private var state = [Float](repeating: 0, count: stateSize)
    private var context = [Float](repeating: 0, count: contextSamples)
    private var window = [Float](repeating: 0, count: windowSamples)
    private var failed = false

    override init() {
        api = OrtGetApiBase()?.pointee.GetApi(UInt32(ORT_API_VERSION))?.pointee
        super.init()
    }

    deinit { close() }

    func speechProbability(frame: KotlinFloatArray) -> Float {
        guard prepareSession() else { return 0 }
        fillWindow(frame)
        return run()
    }

    func reset() {
        state = [Float](repeating: 0, count: stateSize)
        context = [Float](repeating: 0, count: contextSamples)
    }

    func close() {
        guard let api else { return }
        if let session { api.ReleaseSession(session) }
        if let options { api.ReleaseSessionOptions(options) }
        if let memoryInfo { api.ReleaseMemoryInfo(memoryInfo) }
        if let env { api.ReleaseEnv(env) }
        session = nil
        options = nil
        memoryInfo = nil
        env = nil
        failed = false
    }

    private func fillWindow(_ frame: KotlinFloatArray) {
        for i in 0..<contextSamples { window[i] = context[i] }
        let count = min(Int(frame.size), frameSamples)
        for i in 0..<frameSamples {
            window[contextSamples + i] = i < count ? frame.get(index: Int32(i)) : 0
        }
        for i in 0..<contextSamples { context[i] = window[windowSamples - contextSamples + i] }
    }

    private func prepareSession() -> Bool {
        if session != nil { return true }
        guard let api, !failed else { return false }
        guard let model = assetPath("silero_vad.onnx") else {
            NSLog("Offwire: silero_vad.onnx is missing from the app bundle")
            failed = true
            return false
        }
        let built = check(api.CreateEnv(ORT_LOGGING_LEVEL_WARNING, "silero-vad", &env), "CreateEnv")
            && check(api.CreateSessionOptions(&options), "CreateSessionOptions")
            && check(api.SetIntraOpNumThreads(options, intraOpThreads), "SetIntraOpNumThreads")
            && check(api.CreateSession(env, model, options, &session), "CreateSession")
            && check(api.CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memoryInfo), "CreateCpuMemoryInfo")
        if !built {
            close()
            failed = true
        }
        return built
    }

    private func run() -> Float {
        guard let api else { return 0 }
        var srValue = sampleRate
        let inputShape: [Int64] = [1, Int64(windowSamples)]
        let stateShape: [Int64] = [2, 1, 128]
        // The tensors borrow the array buffers, so the session must run while the pointers are still valid.
        return window.withUnsafeMutableBytes { input in
            state.withUnsafeMutableBytes { stateBytes in
                var inputTensor: OpaquePointer?
                var stateTensor: OpaquePointer?
                var srTensor: OpaquePointer?
                defer {
                    if let inputTensor { api.ReleaseValue(inputTensor) }
                    if let stateTensor { api.ReleaseValue(stateTensor) }
                    if let srTensor { api.ReleaseValue(srTensor) }
                }
                let created = check(api.CreateTensorWithDataAsOrtValue(
                    memoryInfo, input.baseAddress, input.count, inputShape, inputShape.count,
                    ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &inputTensor), "input tensor")
                    && check(api.CreateTensorWithDataAsOrtValue(
                        memoryInfo, stateBytes.baseAddress, stateBytes.count, stateShape, stateShape.count,
                        ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &stateTensor), "state tensor")
                    && check(api.CreateTensorWithDataAsOrtValue(
                        memoryInfo, &srValue, MemoryLayout<Int64>.size, nil, 0,
                        ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &srTensor), "sr tensor")
                guard created, let inputTensor, let stateTensor, let srTensor else { return 0 }
                return execute(inputs: [inputTensor, stateTensor, srTensor], stateOut: stateBytes)
            }
        }
    }

    private func execute(inputs: [OpaquePointer], stateOut: UnsafeMutableRawBufferPointer) -> Float {
        guard let api else { return 0 }
        let inputLabels: [String] = ["input", "state", "sr"]
        let outputLabels: [String] = ["output", "stateN"]
        let inputNames = inputLabels.map { strdup($0) }
        let outputNames = outputLabels.map { strdup($0) }
        defer {
            inputNames.forEach { free($0) }
            outputNames.forEach { free($0) }
        }
        var outputs: [OpaquePointer?] = [nil, nil]
        defer { outputs.forEach { if let value = $0 { api.ReleaseValue(value) } } }
        var inputValues: [OpaquePointer?] = inputs
        var inputNamePointers = inputNames.map { UnsafePointer<CChar>($0) }
        var outputNamePointers = outputNames.map { UnsafePointer<CChar>($0) }
        guard check(api.Run(session, nil, &inputNamePointers, &inputValues, inputValues.count,
                            &outputNamePointers, outputNamePointers.count, &outputs), "Run"),
              let probabilityValue = outputs[0], let stateValue = outputs[1] else { return 0 }
        var probabilityData: UnsafeMutableRawPointer?
        var stateData: UnsafeMutableRawPointer?
        guard check(api.GetTensorMutableData(probabilityValue, &probabilityData), "output data"),
              check(api.GetTensorMutableData(stateValue, &stateData), "stateN data"),
              let probabilityData, let stateData else { return 0 }
        stateOut.copyMemory(from: UnsafeRawBufferPointer(start: stateData, count: stateSize * 4))
        return probabilityData.load(as: Float.self)
    }

    private func check(_ status: OrtStatusPtr?, _ step: String) -> Bool {
        guard let status else { return true }
        guard let api else { return false }
        let message = api.GetErrorMessage(status).map { String(cString: $0) } ?? "unknown"
        NSLog("Offwire: Silero VAD %@ failed: %@", step, message)
        api.ReleaseStatus(status)
        return false
    }
}
