import Foundation
import WidgetKit
import AppIntents

extension Notification.Name { static let widgetAction = Notification.Name("logbook.widgetAction") }
struct WidgetSnapshot: Codable {
    var medicationId: Int64 = 0;var revision: Int64 = 0;var generic = true
    var lockEnabled: Bool?;var lockLast: String?
    var name = "";var amount = "";var last = "";var token = UUID().uuidString
}
enum WidgetFiles {
    private static let signal = "com.adhs.logbook.widget-action"
    private static var listening = false
    @MainActor static func listen() {
        guard !listening else { return };listening = true
        CFNotificationCenterAddObserver(CFNotificationCenterGetDarwinNotifyCenter(),nil,{ _,_,_,_,_ in
            DispatchQueue.main.async { NotificationCenter.default.post(name: .widgetAction,object: nil) }
        },signal as CFString,nil,.deliverImmediately)
    }
    static let group = "group.com.adhs.logbook.shared"
    static var directory: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) }
    static func read(_ name: String) -> Data? {
        guard let url = directory?.appendingPathComponent(name) else { return nil }
        var data: Data?;var error: NSError?
        NSFileCoordinator().coordinate(readingItemAt: url,options: [],error: &error) { data = try? Data(contentsOf: $0) }
        return data
    }
    static func write(_ data: Data,name: String) throws {
        guard let url = directory?.appendingPathComponent(name) else { throw CocoaError(.fileNoSuchFile) }
        var failure: Error?;var error: NSError?
        NSFileCoordinator().coordinate(writingItemAt: url,options: .forReplacing,error: &error) { target in
            do { try data.write(to: target,options: [.atomic,.completeFileProtectionUntilFirstUserAuthentication]);var target = target;var values = URLResourceValues();values.isExcludedFromBackup = true;try target.setResourceValues(values) } catch { failure = error }
        }
        if let failure { throw failure };if let error { throw error }
    }
    static func snapshot() -> WidgetSnapshot { read("widget.json").flatMap { try? JSONDecoder().decode(WidgetSnapshot.self,from: $0) } ?? WidgetSnapshot() }
    static func publish(_ value: WidgetSnapshot) throws { try write(JSONEncoder().encode(value),name: "widget.json");WidgetCenter.shared.reloadAllTimelines() }
    static func redact() { var value = snapshot();value.generic = true;value.name = "";value.amount = "";value.last = "";value.lockLast = "";try? publish(value) }
    static func queue(_ token: String) throws { try write(Data(token.utf8),name: "pending.txt");NotificationCenter.default.post(name: .widgetAction,object: nil);CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(),CFNotificationName(signal as CFString),nil,nil,true) }
    static func takePending() -> String? {
        guard let url = directory?.appendingPathComponent("pending.txt") else { return nil }
        var token: String?;var error: NSError?
        NSFileCoordinator().coordinate(writingItemAt: url,options: .forReplacing,error: &error) { target in
            guard let data = try? Data(contentsOf: target), let value = String(data: data,encoding: .utf8), !value.isEmpty else { return }
            do { try Data().write(to: target,options: [.atomic,.completeFileProtectionUntilFirstUserAuthentication]);token = value } catch { }
        }
        return token
    }
}
struct LogWidgetIntent: AppIntent {
    static var isDiscoverable = false
    static var title: LocalizedStringResource = "Open logbook"
    static var openAppWhenRun = true
    @Parameter(title: "Action") var token: String
    init() { token = "" }
    init(token: String) { self.token = token }
    func perform() async throws -> some IntentResult { try WidgetFiles.queue(token);return .result() }
}
