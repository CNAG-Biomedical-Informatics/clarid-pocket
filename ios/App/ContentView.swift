import SwiftUI
import UIKit

@MainActor
struct ContentView: View {
    @ObservedObject var model: PocketModel
    @State private var showSettings = false
    @State private var showScanner = false

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Identifier decoding").font(.largeTitle.weight(.bold))
                        Text("Scan a ClarID QR code to view its details.").font(.body).foregroundColor(.secondary)
                    }
                    if model.loading {
                        PocketCard { HStack(spacing: 12) { ProgressView(); Text("Loading codebook…") } }
                    } else if let error = model.startupError {
                        errorCard(error)
                    } else {
                        Button {
                            model.clearResult()
                            showScanner = true
                        } label: { Label("Scan QR", systemImage: "qrcode.viewfinder") }
                            .buttonStyle(PrimaryButton())
                        if let result = model.result {
                            ResultView(result: result, identifier: model.identifier ?? "", settings: model.settings)
                        } else if let error = model.decodeError {
                            errorCard(error)
                        } else {
                            PocketCard {
                                VStack(alignment: .leading, spacing: 14) {
                                    Label("Ready to scan", systemImage: "qrcode").font(.title3.weight(.semibold)).foregroundColor(PocketTheme.teal)
                                    Text("Subjects and biosamples are detected automatically.").font(.body)
                                    Label("ClarID \(model.reference?.codebook.version ?? "0.04")", systemImage: "info.circle")
                                        .font(.body).foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
                .padding(20).frame(maxWidth: 760).frame(maxWidth: .infinity)
            }
            .background(PocketTheme.background)
        }
        .sheet(isPresented: $showSettings) { SettingsView(model: model) }
        .fullScreenCover(isPresented: $showScanner) {
            ScannerView { text in
                let success = model.decode(text)
                UINotificationFeedbackGenerator().notificationOccurred(success ? .success : .error)
                showScanner = false
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "qrcode").font(.title).foregroundColor(Color(red: 0.43, green: 0.85, blue: 0.77)).accessibilityHidden(true)
            Text("ClarID-Pocket").font(.title2.weight(.bold)).foregroundColor(.white)
            Spacer(minLength: 0)
            Button { showSettings = true } label: {
                Image(systemName: "gearshape.fill").font(.title2)
                    .frame(width: 56, height: 56).background(Color.white.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .foregroundColor(.white).accessibilityLabel("Settings")
        }
        .padding(.horizontal, 20).padding(.vertical, 12)
        .background(PocketTheme.navy.ignoresSafeArea(edges: .top))
    }

    private func errorCard(_ message: String) -> some View {
        PocketCard {
            VStack(alignment: .leading, spacing: 12) {
                Label("Unable to decode", systemImage: "exclamationmark.triangle.fill")
                    .font(.title3.weight(.semibold)).foregroundColor(PocketTheme.amber)
                Text(message).font(.body)
                Button("Open settings") { showSettings = true }.font(.body.weight(.semibold)).frame(minHeight: 44)
            }
        }
    }
}
