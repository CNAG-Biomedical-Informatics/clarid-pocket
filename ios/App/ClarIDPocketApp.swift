import SwiftUI

@main
@MainActor
struct ClarIDPocketApp: App {
    @StateObject private var model = PocketModel()
    var body: some Scene {
        WindowGroup { ContentView(model: model) }
    }
}
