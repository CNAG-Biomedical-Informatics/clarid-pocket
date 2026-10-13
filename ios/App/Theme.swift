import SwiftUI

enum PocketTheme {
    static let navy = Color(red: 0.08, green: 0.15, blue: 0.24)
    static let blue = Color(red: 0.14, green: 0.36, blue: 0.60)
    static let teal = Color(red: 0.07, green: 0.42, blue: 0.44)
    static let green = Color(red: 0.10, green: 0.46, blue: 0.30)
    static let amber = Color(red: 0.65, green: 0.37, blue: 0.04)
    static let surface = Color(uiColor: .secondarySystemGroupedBackground)
    static let background = Color(uiColor: .systemGroupedBackground)
}

struct PocketCard<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        content.padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(PocketTheme.surface).clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

struct PrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.title3.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 56)
            .foregroundColor(.white).background(PocketTheme.teal.opacity(configuration.isPressed ? 0.75 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
