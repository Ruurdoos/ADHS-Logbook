import SwiftUI
import LocalAuthentication
import Security

@MainActor final class PrivacyController: ObservableObject {
    static let shared = PrivacyController()
    @Published var enabled = false
    @Published var unlocked = false
    @Published var message: String?
    private var shield: UIWindow?
    private weak var previous: UIWindow?
    private var context: LAContext?
    private var generation = 0
    private let service = "com.adhs.logbook.privacy"
    init() {
        var result: CFTypeRef?
        let status = SecItemCopyMatching([kSecClass:kSecClassGenericPassword,kSecAttrService:service,kSecAttrAccount:"enabled",kSecReturnData:true] as CFDictionary,&result)
        enabled = status == errSecSuccess ? (result as? Data) == Data([1]) : status != errSecItemNotFound
        NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification,object: nil,queue: .main) { _ in Task { @MainActor in self.cover() } }
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification,object: nil,queue: .main) { _ in Task { @MainActor in self.generation += 1;self.context?.invalidate();self.context = nil;self.cover() } }
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification,object: nil,queue: .main) { _ in Task { @MainActor in if self.enabled && !self.unlocked { self.showShield() } } }
    }
    func cover() { if enabled { unlocked = false;showShield() } }
    func showShield() {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { return }
        if shield == nil {
            previous = scene.windows.first { $0.isKeyWindow }
            let window = UIWindow(windowScene: scene);window.windowLevel = .alert + 1
            let host = UIHostingController(rootView: LockScreen().environmentObject(self));host.view.accessibilityViewIsModal = true
            window.rootViewController = host;shield = window
        }
        shield?.makeKeyAndVisible()
    }
    func unlock(change: Bool? = nil) {
        generation += 1;let generation = self.generation
        let context = LAContext();self.context = context
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication,error: &error) else { message = l("Set a device screen lock first.");return }
        context.evaluatePolicy(.deviceOwnerAuthentication,localizedReason: l("Unlock logbook")) { success,error in
            Task { @MainActor in
                guard self.generation == generation else { return }
                self.context = nil
                guard success else { self.message = error?.localizedDescription;return }
                do {
                    if let change { try self.setEnabled(change) }
                    self.unlocked = true;self.message = nil;self.shield?.isHidden = true;self.previous?.makeKey()
                    NotificationCenter.default.post(name: .widgetAction,object: nil)
                } catch { self.message = l("Could not save or load data. Please try again.") }
            }
        }
    }
    private func setEnabled(_ value: Bool) throws {
        let query = [kSecClass:kSecClassGenericPassword,kSecAttrService:service,kSecAttrAccount:"enabled"] as [CFString:Any]
        let data = Data([value ? 1 : 0]);let update = SecItemUpdate(query as CFDictionary,[kSecValueData:data] as CFDictionary)
        if update == errSecItemNotFound {
            var insert = query;insert[kSecValueData] = data;insert[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(insert as CFDictionary,nil) == errSecSuccess else { throw AppError.invalid }
        } else if update != errSecSuccess { throw AppError.invalid }
        enabled = value
        WidgetFiles.redact()
    }
}
struct LockScreen: View {
    @EnvironmentObject var privacy: PrivacyController
    var body: some View { VStack(spacing: 20) { Text(l("Logbook locked")).font(.title);Button(l("Unlock logbook")) { privacy.unlock() };if let message = privacy.message { Text(message) } }.padding().frame(maxWidth: .infinity,maxHeight: .infinity).background(Color(.systemBackground)) }
}
struct PrivacySettings: View {
    @ObservedObject var privacy = PrivacyController.shared
    var body: some View {
        Toggle(l("App lock"),isOn: Binding(get: { privacy.enabled },set: { privacy.unlock(change: $0) }))
        Text(l("Relocks when you leave the app. Exported files remain outside this lock; this is not whole-database encryption.")).font(.footnote)
        if let message = privacy.message { Text(message) }
    }
}
