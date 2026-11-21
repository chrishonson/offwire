// Prints the Apple Vision text of an image, one recognized line per output line.
// Usage: swift scripts/eval/ocr.swift <image path>
import Foundation
import Vision
import AppKit

guard CommandLine.arguments.count == 2 else {
    FileHandle.standardError.write(Data("usage: ocr.swift <image path>\n".utf8))
    exit(2)
}
guard let image = NSImage(contentsOfFile: CommandLine.arguments[1]),
      let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    FileHandle.standardError.write(Data("cannot read image\n".utf8))
    exit(1)
}

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = true
try VNImageRequestHandler(cgImage: cgImage, options: [:]).perform([request])
for observation in request.results ?? [] {
    if let line = observation.topCandidates(1).first?.string { print(line) }
}
