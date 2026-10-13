import SwiftUI

@MainActor
struct SettingsView: View {
    @ObservedObject var model: PocketModel
    @Environment(\.dismiss) private var dismiss
    @State private var advanced = false

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Decode settings").font(.body)) {
                    Picker("Entity", selection: $model.settings.entity) {
                        ForEach(IdentifierEntity.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    Picker("Format", selection: $model.settings.format) {
                        ForEach(IdentifierFormat.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    DisclosureGroup("Advanced", isExpanded: $advanced) {
                        Stepper("Human subject width: \(model.settings.humanSubjectWidth)", value: $model.settings.humanSubjectWidth, in: 1...18)
                        Stepper("Stub subject width: \(model.settings.stubSubjectWidth)", value: $model.settings.stubSubjectWidth, in: 1...10)
                        Label("Widths must match the identifiers’ codebook.", systemImage: "exclamationmark.triangle")
                            .font(.body).foregroundColor(PocketTheme.amber)
                    }
                }
                Section(header: Text("About").font(.body)) {
                    info("App", "ClarID-Pocket \(model.appVersion)")
                    Button { model.checkForUpdates() } label: {
                        HStack(spacing: 12) {
                            if model.checkingUpdates { ProgressView() }
                            else { Image(systemName: "arrow.triangle.2.circlepath") }
                            Text(model.checkingUpdates ? "Checking…" : "Check for updates")
                        }.font(.body.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                    }.disabled(model.checkingUpdates)
                    if let update = model.appUpdate {
                        Text(update.message).font(.body)
                        if let page = update.releasePage {
                            Link(destination: page) {
                                Label("View release", systemImage: "arrow.up.right.square").font(.body)
                                    .frame(minHeight: 44)
                            }
                        }
                    }
                    Text("Checks GitHub. Scanning stays offline.").font(.body).foregroundColor(.secondary)
                    info("ClarID", model.reference?.codebook.version ?? "0.04")
                    info("Codebook", model.reference?.codebook.localVersion ?? "—")
                    Label("Decoding works offline", systemImage: "checkmark.shield").font(.body)
                }
            }
            .font(.body).navigationTitle("Settings")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() }.font(.body.weight(.semibold)).frame(minHeight: 44) } }
        }
        .navigationViewStyle(.stack)
    }
    private func info(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).foregroundColor(.secondary)
            Text(value).textSelection(.enabled)
        }.font(.body).padding(.vertical, 4)
    }
}
