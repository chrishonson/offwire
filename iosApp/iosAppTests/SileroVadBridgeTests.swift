import XCTest
import ComposeApp
@testable import Offwire

final class SileroVadBridgeTests: XCTestCase {
    private let frameSamples = 512

    func testSpeechReachesHighProbability() throws {
        let probabilities = try wavProbabilities()
        let maximum = try XCTUnwrap(probabilities.max())
        NSLog("SileroVadBridgeTests max probability on speech: %f", maximum)
        XCTAssertGreaterThan(maximum, 0.7)
    }

    func testSilenceStaysLow() {
        let bridge = SileroVadBridge()
        bridge.reset()
        let zeros = KotlinFloatArray(size: Int32(frameSamples))
        let probabilities = (0..<20).map { _ in bridge.speechProbability(frame: zeros) }
        NSLog("SileroVadBridgeTests max probability on silence: %f", probabilities.max() ?? -1)
        XCTAssertEqual(probabilities.count, 20)
        XCTAssertTrue(probabilities.allSatisfy { $0 < 0.3 }, "\(probabilities)")
    }

    func testProbabilitiesAreNotABinaryDecision() throws {
        let probabilities = try wavProbabilities()
        XCTAssertGreaterThan(Set(probabilities).count, 2)
        XCTAssertTrue(probabilities.contains { $0 > 0.01 && $0 < 0.99 })
    }

    private func wavProbabilities() throws -> [Float] {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "computer", withExtension: "wav"))
        let wav = try Data(contentsOf: url)
        let marker = try XCTUnwrap(wav.range(of: Data("data".utf8)))
        let pcm = Array(wav.suffix(from: marker.upperBound + 4))
        let samples = (0..<(pcm.count / 2)).map { i in
            Float(Int16(bitPattern: UInt16(pcm[2 * i]) | (UInt16(pcm[2 * i + 1]) << 8))) / 32768
        }
        let bridge = SileroVadBridge()
        bridge.reset()
        return stride(from: 0, to: samples.count - frameSamples + 1, by: frameSamples).map { start in
            let frame = KotlinFloatArray(size: Int32(frameSamples))
            for i in 0..<frameSamples { frame.set(index: Int32(i), value: samples[start + i]) }
            return bridge.speechProbability(frame: frame)
        }
    }
}
