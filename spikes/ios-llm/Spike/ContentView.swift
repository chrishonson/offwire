import SwiftUI

struct ContentView: View {
    @StateObject private var runner = BenchmarkRunner.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Gemma iOS LLM Spike")
                .font(.title2)
                .bold()

            Text("Status: \(runner.status)")
                .font(.headline)
                .foregroundColor(runner.isFinished ? .green : .blue)

            ScrollView {
                Text(runner.logOutput)
                    .font(.system(.caption, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(.systemGray6))
            .cornerRadius(8)
        }
        .padding()
        .task {
            await runner.run()
        }
    }
}
