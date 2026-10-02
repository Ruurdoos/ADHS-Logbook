import AppIntents
import Foundation

// Public discovery exposes review only. No intent accepts an amount or saves a dose.
struct ConfiguredReview: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Review shortcut"
    static var defaultQuery = ReviewQuery()
    var id: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "Configured medication") }
}
struct ReviewQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [ConfiguredReview] { try await suggestedEntities().filter { identifiers.contains($0.id) } }
    func suggestedEntities() async throws -> [ConfiguredReview] {
        guard UserDefaults.standard.bool(forKey: "quick.enabled"), let data = UserDefaults.standard.data(forKey: "quick.configuration"),let value = try? JSONDecoder().decode(QuickConfigurationValue.self,from: data) else { return [] }
        return [ConfiguredReview(id: value.token)]
    }
}
struct OpenDoseEditorIntent: AppIntent {
    static var title: LocalizedStringResource = "Open dose editor"
    static var description = IntentDescription("Opens review. Saving always requires a tap in the app.")
    static var openAppWhenRun = true
    @Parameter(title: "Review shortcut") var shortcut: ConfiguredReview?
    func perform() async throws -> some IntentResult { try WidgetFiles.queue("review:"+(shortcut?.id ?? "generic"));return .result() }
}
