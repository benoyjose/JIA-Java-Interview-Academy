import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Accessible, data-driven lesson block presentation shared by native iOS screens.
public struct JIALessonReaderView: View {
    public let content: JIALessonContent

    public init(content: JIALessonContent) {
        self.content = content
    }

    public var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                ForEach(Array(content.blocks.enumerated()), id: \.offset) { _, block in
                    JIALessonBlockView(block: block)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
    }
}

private struct JIALessonBlockView: View {
    let block: JIALessonBlock

    var body: some View {
        if block.isSupported {
            VStack(alignment: .leading, spacing: 8) {
                if let title = block.title, !title.isEmpty {
                    Text(title).font(.headline).accessibilityAddTraits(.isHeader)
                }
                switch block.type {
                case "code":
                    codeBlock
                case "resource", "media", "diagram":
                    if let url = block.url {
                        Link(block.text ?? block.title ?? url.absoluteString, destination: url)
                            .font(.body)
                    } else if let text = block.text {
                        Text(text).font(.body)
                    }
                default:
                    if let text = block.text, !text.isEmpty {
                        Text(text).font(.body).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    private var codeBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let language = block.language, !language.isEmpty {
                Text(language.uppercased()).font(.caption).foregroundStyle(.secondary)
            }
            ScrollView(.horizontal) {
                Text(block.code ?? block.text ?? "")
                    .font(.system(.body, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
            if let code = block.code, !code.isEmpty {
                Button("Copy code") {
                    #if canImport(UIKit)
                    UIPasteboard.general.string = code
                    #endif
                }
                .font(.caption)
            }
        }
    }
}
