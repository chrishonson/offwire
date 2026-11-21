import XCTest
import ComposeApp
@testable import Offwire

final class ParakeetSttBridgeTests: XCTestCase {
    func testTranscribesBundledWavExactly() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "computer", withExtension: "wav"))
        let wav = try Data(contentsOf: url)
        // A 16 kHz mono 16-bit WAV from afconvert: the samples start after the "data" chunk header.
        let marker = try XCTUnwrap(wav.range(of: Data("data".utf8)))
        let pcm = wav.suffix(from: marker.upperBound + 4)
        let bytes = KotlinByteArray(size: Int32(pcm.count))
        for (i, byte) in pcm.enumerated() {
            bytes.set(index: Int32(i), value: Int8(bitPattern: byte))
        }

        let text = ParakeetSttBridge().transcribe(pcm16: bytes, sampleRate: 16000)

        XCTAssertEqual(text.trimmingCharacters(in: .whitespaces).lowercased(), "computer, what time is it?")
    }
}
