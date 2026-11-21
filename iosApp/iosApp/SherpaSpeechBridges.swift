import Foundation
import ComposeApp
import SherpaOnnx

private let featureDim = 80
private let numThreads = 2
private let pcm16Scale: Float = 32768

/// The models ship as the `assets` folder reference of the app target, which points at the Android assets.
func assetPath(_ name: String) -> String? {
    let path = (Bundle.main.resourcePath ?? "") + "/assets/" + name
    return FileManager.default.fileExists(atPath: path) ? path : nil
}

/// Parakeet TDT 110M int8 through sherpa-onnx, with the same model config as the Android recognizer.
/// Kotlin calls it one request at a time, so the lazily built recognizer needs no lock.
final class ParakeetSttBridge: NSObject, SttBridge {
    private var recognizer: SherpaOnnxOfflineRecognizer?

    func transcribe(pcm16: KotlinByteArray, sampleRate: Int32) -> String {
        guard let recognizer = recognizer ?? makeRecognizer() else { return "" }
        self.recognizer = recognizer
        let samples = Self.floats(from: pcm16)
        return recognizer.decode(samples: samples, sampleRate: Int(sampleRate)).text
    }

    func close() {
        recognizer = nil
    }

    static func floats(from pcm16: KotlinByteArray) -> [Float] {
        let count = Int(pcm16.size) / 2
        return (0..<count).map { i in
            let low = UInt16(UInt8(bitPattern: pcm16.get(index: Int32(2 * i))))
            let high = UInt16(UInt8(bitPattern: pcm16.get(index: Int32(2 * i + 1))))
            return Float(Int16(bitPattern: low | (high << 8))) / pcm16Scale
        }
    }

    private func makeRecognizer() -> SherpaOnnxOfflineRecognizer? {
        guard let encoder = assetPath("parakeet/encoder.int8.onnx"),
              let decoder = assetPath("parakeet/decoder.int8.onnx"),
              let joiner = assetPath("parakeet/joiner.int8.onnx"),
              let tokens = assetPath("parakeet/tokens.txt") else {
            NSLog("Offwire: Parakeet model files are missing from the app bundle")
            return nil
        }
        var config = sherpaOnnxOfflineRecognizerConfig(
            featConfig: sherpaOnnxFeatureConfig(sampleRate: 16000, featureDim: featureDim),
            modelConfig: sherpaOnnxOfflineModelConfig(
                tokens: tokens,
                transducer: sherpaOnnxOfflineTransducerModelConfig(
                    encoder: encoder,
                    decoder: decoder,
                    joiner: joiner
                ),
                numThreads: numThreads,
                provider: "cpu",
                debug: 0,
                modelType: "nemo_transducer"
            )
        )
        return SherpaOnnxOfflineRecognizer(config: &config)
    }
}
