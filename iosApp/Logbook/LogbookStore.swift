import Foundation
import UserNotifications
import LogbookShared

@MainActor
final class LogbookStore: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    @Published private(set) var state = DiskState()
    @Published var error: String?
    @Published var openOccurrence: String?
    @Published var widgetReview: Int64?
    @Published var undoID: Int64?
    @Published var scheduledThrough: Date?
    private let location: URL
    private var readable = true
    private let notifications: Bool
    private var supplyGeneration = 0
    private let center = UNUserNotificationCenter.current()

    init(directory: URL? = nil, notifications: Bool = true) {
        self.notifications = notifications
        let folder = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("ADHSLogbook", isDirectory: true)
        location = folder.appendingPathComponent("logbook.json")
        super.init()
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            var url = folder; var values = URLResourceValues(); values.isExcludedFromBackup = true; try url.setResourceValues(values)
            if FileManager.default.fileExists(atPath: location.path) {
                let loaded = try JSONDecoder().decode(DiskState.self, from: Data(contentsOf: location))
                guard loaded.schema == 1 else { throw AppError.invalid }
                _ = try loaded.document.validated(); state = loaded
            }
        } catch { readable = false; self.error = l("Could not save or load data. Please try again.") }
        guard notifications else { return }
        WidgetFiles.listen()
        center.delegate = self
        let log = UNNotificationAction(identifier: "LOG", title: l("Log now"), options: [.foreground,.authenticationRequired])
        let snooze = UNNotificationAction(identifier: "SNOOZE", title: l("Snooze 10 min"))
        let open = UNNotificationAction(identifier: "OPEN", title: l("Open"), options: [.foreground, .authenticationRequired])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: "DOSE", actions: [log, snooze, open], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "GENERIC", actions: [snooze, open], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: "LOCKED", actions: [open], intentIdentifiers: [], options: [])
        ])
    }
    func change(_ edit: (inout DiskState) throws -> Void) throws {
        guard readable else { throw AppError.invalid }
        var next = state; try edit(&next); next.document = try decoded(NativeBridge.shared.reconcile(document: encoded(next.document)),as: Document.self).validated()
        let data = try JSONEncoder().encode(next)
        try data.write(to: location, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        state = next
        if notifications { publishWidget() }
    }
    func attempt(_ action: () throws -> Void) { do { try action() } catch { self.error = l("Could not save or load data. Please try again.") } }
    func nextID() -> Int64 { max(state.document.medications.map(\.id).max() ?? 0, state.document.entries.map(\.id).max() ?? 0, state.actions.values.max() ?? 0) + 1 }
    func save(_ medication: Med) throws {
        var med = medication
        if med.id == 0 { med.id = nextID() }
        med.revision = (state.document.medications.first { $0.id == med.id }?.revision ?? 0) + 1
        try change { s in s.document.medications.removeAll { $0.id == med.id };s.document.medications.append(med);s.document.preferences["onboarded"] = "true" }
        schedule()
    }
    @discardableResult
    func save(_ entry: Entry, action: String, occurrence: String? = nil) throws -> Int64 {
        if let existing = state.actions[action], entry.id == 0 { return existing }
        var value = entry; if value.id == 0 { value.id = nextID() }
        try change { s in
            s.document.entries.removeAll { $0.id == value.id };s.document.entries.append(value)
            s.document.entries.sort { $0.timestamp == $1.timestamp ? $0.id > $1.id : $0.timestamp > $1.timestamp }
            s.actions[action] = value.id
            if let occurrence, let index = s.occurrences.firstIndex(where: { $0.id == occurrence && $0.state == "pending" }),
               let reminder = s.document.reminders.first(where: { $0.id == s.occurrences[index].reminderId }),
               reminder.medicationId == nil || reminder.medicationId == value.medicationId,
               try NativeBridge.shared.canAct(occurrence: encoded(s.occurrences[index]), reminder: encoded(reminder), medication: try s.document.medications.first(where: { $0.id == reminder.medicationId }).map(encoded), now: millis()) {
                s.occurrences[index].state = "logged";s.occurrences[index].entryId = value.id
            }
        }
        if let occurrence, state.occurrences.contains(where: { $0.id == occurrence && $0.state == "logged" }) { clear(occurrence) }
        undoID = value.id; schedule();return value.id
    }
    func quick(_ med: Med, action: String = UUID().uuidString, occurrence: String? = nil) throws {
        let now = Date()
        let entry = try decoded(NativeBridge.shared.quickEntry(medication: encoded(med), now: millis(now), zone: TimeZone.current.identifier, offset: zoneOffset(now)), as: Entry.self)
        try save(entry, action: action, occurrence: occurrence)
    }
    func delete(_ id: Int64) throws {
        try change { s in s.document.entries.removeAll { $0.id == id };for i in s.document.observations.indices where s.document.observations[i].doseId == id { s.document.observations[i].doseId = nil };for i in s.occurrences.indices where s.occurrences[i].entryId == id { s.occurrences[i].state = "undone" } }
        if undoID == id { undoID = nil };schedule()
    }
    func saveReminder(_ value: ReminderValue) throws {
        var r = value
        if r.id == 0 { r.id = (state.document.reminders.map(\.id).max() ?? 0) + 1 }
        r.revision = (state.document.reminders.first { $0.id == r.id }?.revision ?? 0) + 1
        try change { s in s.document.reminders.removeAll { $0.id == r.id };s.document.reminders.append(r) };schedule()
    }
    func enableReminders(_ enabled: Bool) {
        if !enabled { attempt { try change { $0.remindersEnabled = false } };schedule();return }
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in Task { @MainActor in
            self.attempt { try self.change { $0.remindersEnabled = true } }
            if !granted { self.error = l("Notifications are turned off. Your reminder times are saved.") }
            self.schedule()
        } }
    }
    func clear(_ id: String) {
        center.removePendingNotificationRequests(withIdentifiers: [id, id+"-follow", id+"-snooze"])
        center.removeDeliveredNotifications(withIdentifiers: [id, id+"-follow", id+"-snooze"])
    }
    func schedule() {
        guard readable && notifications else { return }
        let now = Date(), stamp = millis(now)
        do {
            try change { s in
                for i in s.occurrences.indices {
                    let o = s.occurrences[i], r = s.document.reminders.first { $0.id == o.reminderId }
                    let m = s.document.medications.first { $0.id == r?.medicationId }
                    if o.state != "pending" || s.document.pause.active(stamp) || !s.remindersEnabled || r == nil || r!.revision != o.revision || o.expires <= stamp || (r?.medicationId != nil && (m?.active != true || m?.revision != o.medicationRevision)) {
                        if o.state == "pending" { s.occurrences[i].state = "expired" }; clear(o.id)
                    }
                }
                // Local iOS notifications have a finite pending-request budget. Replenish on
                // launch, foregrounding and every explicit action; expose coverage in Settings.
                if s.remindersEnabled {
                    for r in s.document.reminders {
                        let med = s.document.medications.first { $0.id == r.medicationId }
                        if r.medicationId != nil && med?.active != true { continue }
                        let futureTimes: [Int64] = (0..<14).compactMap { day in
                            let base = Calendar.current.date(byAdding: .day, value: day, to: now)!
                            return Calendar.current.date(bySettingHour: r.hour, minute: r.minute, second: 0, of: base).flatMap { $0 > now && !s.document.pause.active(millis($0)) ? millis($0) : nil }
                        }
                        for i in s.occurrences.indices where s.occurrences[i].reminderId == r.id && s.occurrences[i].scheduled > stamp && !futureTimes.contains(s.occurrences[i].scheduled) { s.occurrences[i].state = "expired" }
                        for time in futureTimes where !s.occurrences.contains(where: { $0.reminderId == r.id && $0.scheduled == time && $0.state == "pending" }) {
                            s.occurrences.append(OccurrenceValue(id: UUID().uuidString, reminderId: r.id, scheduled: time, expires: time + Int64(r.cutoffMinutes)*60000, revision: r.revision, medicationRevision: med?.revision, nextAlert: time))
                        }
                    }
                }
                s.occurrences.removeAll { $0.expires < stamp - 7*86400000 }
            }
            center.removePendingNotificationRequests(withIdentifiers: state.occurrences.flatMap { [$0.id,$0.id+"-follow",$0.id+"-snooze"] })
            var jobs: [(OccurrenceValue, ReminderValue, Int64, String)] = []
            for o in state.occurrences where o.state == "pending" {
                guard let r = state.document.reminders.first(where: { $0.id == o.reminderId }) else { continue }
                if o.nextAlert > stamp && o.nextAlert < o.expires { jobs.append((o,r,o.nextAlert,o.id)) }
                if r.followUp && !o.followedUp && o.nextAlert == o.scheduled && o.scheduled+1800000 > stamp && o.scheduled+1800000 < o.expires {
                    jobs.append((o,r,o.scheduled+1800000,o.id+"-follow"))
                }
            }
            jobs.sort { $0.2 < $1.2 }
            let selected = Array(jobs.prefix(48));scheduledThrough = selected.last.map { date($0.2) }
            scheduleSupply(now: now)
            for (o,r,time,id) in selected {
                let content = UNMutableNotificationContent();content.title = l("A moment for your log");content.body = l("Add an entry when you are ready.")
                content.categoryIdentifier = PrivacyController.shared.enabled ? "LOCKED" : (r.medicationId == nil ? "GENERIC" : "DOSE");content.userInfo = ["occurrence":o.id];content.sound = .default
                // Relative trigger preserves the explicitly calculated absolute occurrence.
                center.add(UNNotificationRequest(identifier: id, content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1, Double(time-stamp)/1000), repeats: false))) { error in
                    if error != nil { Task { @MainActor in self.error = l("Notifications are turned off. Your reminder times are saved.") } }
                }
            }
        } catch { self.error = l("Could not save or load data. Please try again.") }
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        Task { @MainActor in
            defer { completionHandler() }
            guard let id = response.notification.request.content.userInfo["occurrence"] as? String,
                  let o = self.state.occurrences.first(where: { $0.id == id }),
                  let r = self.state.document.reminders.first(where: { $0.id == o.reminderId }) else { return }
            do {
                if PrivacyController.shared.enabled && !PrivacyController.shared.unlocked {
                    self.openOccurrence = id;return
                }
                let med = self.state.document.medications.first { $0.id == r.medicationId }
                guard self.state.remindersEnabled, !self.state.document.pause.active(), try NativeBridge.shared.canAct(occurrence: encoded(o), reminder: encoded(r), medication: try med.map(encoded), now: millis()) else {
                    self.error = l("This reminder expired. Open your log to review.");self.clear(id);return
                }
                switch response.actionIdentifier {
                case "LOG": if let med { try self.quick(med, action: "reminder:"+id, occurrence: id) }
                case "SNOOZE":
                    try self.change { s in if let i = s.occurrences.firstIndex(where: { $0.id == id }) {
                        s.occurrences[i].nextAlert = min(millis()+600000,o.expires);s.occurrences[i].followedUp = true
                    } };self.clear(id);self.schedule()
                default: self.openOccurrence = id
                }
            } catch { self.error = l("Could not save or load data. Please try again.") }
        }
    }
    func restore(_ document: Document) throws {
        let valid = try document.validated()
        try change { s in s.recovery = s.document;s.document = valid;s.remindersEnabled = false;s.actions = [:];s.occurrences = [];s.supplyAlerts = [:];s.supplyNotificationsEnabled = false }
        center.removeAllPendingNotificationRequests();center.removeAllDeliveredNotifications();undoID = nil
        for key in ["quick.enabled","quick.configuration","quick.last"] { UserDefaults.standard.removeObject(forKey: key) };invalidateWidget()
    }

    func saveMeasurement(_ value: MeasurementValue) throws { try change { s in s.document.measurements.removeAll { $0.id == value.id };s.document.measurements.append(value) } }
    func deleteMeasurement(_ id: String) throws { try change { $0.document.measurements.removeAll { $0.id == id } } }
    func reviewShortcut(_ token: String) {
        guard !PrivacyController.shared.enabled || PrivacyController.shared.unlocked else { return }
        attempt {
            let config = UserDefaults.standard.data(forKey: "quick.configuration").flatMap { String(data: $0,encoding: .utf8) }
            let route = try decoded(NativeBridge.shared.review(token: token,enabled: UserDefaults.standard.bool(forKey: "quick.enabled"),configuration: config,document: encoded(state.document)),as: ReviewRouteValue.self)
            guard !route.unavailable, let med = state.document.medications.first(where: { $0.active && (route.medicationId == nil || $0.id == route.medicationId) }) else { error = l("Quick access is unavailable. Open Settings to configure it.");return }
            widgetReview = med.id
            if route.changed { error = l("Medication settings changed. Review the current amount.") }
        }
    }
    func saveObservation(_ value: ObservationValue) throws {
        try change { s in s.document.observations.removeAll { $0.id == value.id };s.document.observations.append(value) }
    }
    func deleteObservation(_ id: String) throws { try change { $0.document.observations.removeAll { $0.id == id } } }
    func saveNonUse(_ value: NonUseValue) throws {
        try change { s in
            if let occurrence = value.occurrenceId, let i = s.occurrences.firstIndex(where: { $0.id == occurrence }) {
                guard ["pending","not_taken"].contains(s.occurrences[i].state), let reminder = s.document.reminders.first(where: { $0.id == s.occurrences[i].reminderId }), reminder.medicationId == nil || reminder.medicationId == value.medicationId else { throw AppError.invalid }
                s.occurrences[i].state = "not_taken"
            }
            s.document.nonUse.removeAll { $0.id == value.id };s.document.nonUse.append(value)
        };schedule()
    }
    func deleteNonUse(_ id: String) throws {
        try change { s in
            if let occurrence = s.document.nonUse.first(where: { $0.id == id })?.occurrenceId, let i = s.occurrences.firstIndex(where: { $0.id == occurrence }) { s.occurrences[i].state = "expired" }
            s.document.nonUse.removeAll { $0.id == id }
        };schedule()
    }
    func pause(_ value: PauseValue) throws { try change { $0.document.pause = value };schedule() }
    func saveSupply(_ value: SupplyValue,count: Double,action: String) throws {
        guard !state.document.stock.contains(where: { $0.id == action }) else { return }
        try change { s in s.document.supplies.removeAll { $0.medicationId == value.medicationId };s.document.supplies.append(value)
            s.document.stock.append(MovementValue(id: action,medicationId: value.medicationId,kind: "count",timestamp: value.countedAt,units: count))
        };schedule()
    }
    func restock(_ med: Int64,units: Double,action: String) throws {
        guard units.isFinite && units > 0 else { throw AppError.invalid }
        guard !state.document.stock.contains(where: { $0.id == action }) else { return }
        try change { $0.document.stock.append(MovementValue(id: action,medicationId: med,kind: "restock",timestamp: millis(),units: units)) };schedule()
    }
    func removeSupply(_ med: Int64) throws { try change { s in s.document.supplies.removeAll { $0.medicationId == med };s.document.stock.removeAll { $0.medicationId == med } };schedule() }
    func configureWidget(_ med: Med,generic: Bool) throws {
        guard WidgetFiles.directory != nil else { throw AppError.invalid }
        UserDefaults.standard.set(med.id,forKey: "widget.med");UserDefaults.standard.set(med.revision,forKey: "widget.revision");UserDefaults.standard.set(generic,forKey: "widget.generic")
        invalidateWidget()
    }
    func invalidateWidget() { UserDefaults.standard.set(UUID().uuidString,forKey: "widget.token");publishWidget() }
    func publishWidget() {
        guard WidgetFiles.directory != nil else { return }
        let settings = UserDefaults.standard
        if let data = settings.data(forKey: "quick.configuration"),let config = try? JSONDecoder().decode(QuickConfigurationValue.self,from: data),!state.document.medications.contains(where: { $0.id == config.medicationId && $0.active }) { settings.removeObject(forKey: "quick.configuration") }
        var snapshot = WidgetSnapshot();snapshot.medicationId = Int64(settings.integer(forKey: "widget.med"));snapshot.revision = Int64(settings.integer(forKey: "widget.revision"))
        snapshot.generic = settings.object(forKey: "widget.generic") == nil || settings.bool(forKey: "widget.generic") || PrivacyController.shared.enabled
        snapshot.lockEnabled = settings.bool(forKey: "quick.enabled")
        snapshot.lockLast = ""
        if snapshot.lockEnabled == true && settings.bool(forKey: "quick.last") && !PrivacyController.shared.enabled,
           let data = settings.data(forKey: "quick.configuration"),let config = try? JSONDecoder().decode(QuickConfigurationValue.self,from: data),
           state.document.medications.contains(where: { $0.id == config.medicationId && $0.active }),let last = state.document.entries.first(where: { $0.medicationId == config.medicationId }) { snapshot.lockLast = date(last.timestamp).formatted(date: .abbreviated,time: .shortened) }
        snapshot.token = settings.string(forKey: "widget.token") ?? ""
        if !snapshot.generic, let med = state.document.medications.first(where: { $0.id == snapshot.medicationId && $0.active }) {
            snapshot.name = med.name;snapshot.amount = number(med.usualDose)+" "+l(med.unit)
            if let last = state.document.entries.first(where: { $0.medicationId == med.id }) { snapshot.last = date(last.timestamp).formatted(date: .abbreviated,time: .shortened) }
        }
        try? WidgetFiles.publish(snapshot)
    }
    func processWidget() {
        guard !PrivacyController.shared.enabled || PrivacyController.shared.unlocked, let rawToken = WidgetFiles.takePending() else { return }
        if rawToken.hasPrefix("review:") { reviewShortcut(String(rawToken.dropFirst(7)));return }
        let reviewOnly = rawToken.hasPrefix("widget-review:")
        let token = reviewOnly ? String(rawToken.dropFirst(14)) : rawToken
        let prefs = UserDefaults.standard
        guard let med = state.document.medications.first(where: { $0.id == Int64(prefs.integer(forKey: "widget.med")) && $0.active }) else { error = l("Widget changed. Review the medication and amount in the app.");return }
        guard token == prefs.string(forKey: "widget.token"), med.revision == Int64(prefs.integer(forKey: "widget.revision")), !prefs.bool(forKey: "widget.generic"), !reviewOnly else { widgetReview = med.id;return }
        attempt { try quick(med,action: "widget:"+token);invalidateWidget() }
    }
    func enableSupplyNotifications(_ enabled: Bool) {
        attempt { try change { $0.supplyNotificationsEnabled = enabled } }
        if enabled {
            center.requestAuthorization(options: [.alert,.sound]) { granted,_ in Task { @MainActor in
                if !granted { self.error = l("Notifications are turned off. Your reminder times are saved.") };self.schedule()
            } }
        } else { schedule() }
    }
    func scheduleSupply(now: Date) {
        supplyGeneration += 1;let generation = supplyGeneration
        center.getPendingNotificationRequests { requests in Task { @MainActor in
            guard self.supplyGeneration == generation else { return }
            self.reconcileSupply(now: now,pending: requests)
        } }
    }
    private func reconcileSupply(now: Date,pending: [UNNotificationRequest]) {
        let stamp = millis(now), pause = state.document.pause
        var alerts = state.supplyAlerts ?? [:]
        let active = state.document.supplies.filter { supply in state.document.medications.contains { $0.id == supply.medicationId && $0.active } }
        let current = Set(active.map { String($0.medicationId) })
        let previous = Set((alerts["ids"] ?? "").split(separator: ",").map(String.init))
        for key in previous.union(current) where !current.contains(key) || state.supplyNotificationsEnabled != true {
            center.removePendingNotificationRequests(withIdentifiers: ["stock:"+key,"rx:"+key]);center.removeDeliveredNotifications(withIdentifiers: ["stock:"+key,"rx:"+key])
            alerts.removeValue(forKey: "low:"+key)
        }
        alerts["ids"] = current.sorted().joined(separator: ",")
        var jobs: [(String,Int64,String,String)] = []
        if state.supplyNotificationsEnabled == true {
            for supply in active {
                guard let balance = try? state.document.balance(supply.medicationId) else { continue }
                let key = String(supply.medicationId), low = balance.remaining <= supply.lowThreshold
                if !low || pause.active(stamp) {
                    center.removePendingNotificationRequests(withIdentifiers: ["stock:"+key]);center.removeDeliveredNotifications(withIdentifiers: ["stock:"+key]);alerts.removeValue(forKey: "low:"+key)
                } else if alerts["low:"+key] == nil {
                    jobs.append(("stock:"+key,stamp+1000,"low:"+key,"true"))
                }
                if let rx = supply.prescriptionDate {
                    if pause.active(stamp) && (pause.until == nil || rx <= pause.until!) {
                        center.removePendingNotificationRequests(withIdentifiers: ["rx:"+key]);alerts["rx:"+key] = String(rx)
                    } else if rx > stamp || alerts["rx:"+key] != String(rx) {
                        jobs.append(("rx:"+key,max(stamp+1000,rx),"rx:"+key,String(rx)))
                    }
                } else { center.removePendingNotificationRequests(withIdentifiers: ["rx:"+key]);alerts.removeValue(forKey: "rx:"+key) }
            }
        }
        // Include queued low alerts in the same finite budget; dose rescheduling must not erase them.
        for request in pending where request.identifier.hasPrefix("stock:") {
            let key = String(request.identifier.dropFirst(6))
            if state.supplyNotificationsEnabled == true && !pause.active(stamp) && current.contains(key) && alerts["low:"+key] != nil {
                jobs.append((request.identifier,millis((request.trigger as? UNTimeIntervalNotificationTrigger)?.nextTriggerDate() ?? now),"low:"+key,"true"))
            }
        }
        let selected = Array(jobs.sorted { $0.1 == $1.1 ? $0.0 < $1.0 : $0.1 < $1.1 }.prefix(12))
        let selectedIDs = Set(selected.map { $0.0 })
        for request in pending where (request.identifier.hasPrefix("stock:") || request.identifier.hasPrefix("rx:")) && !selectedIDs.contains(request.identifier) {
            center.removePendingNotificationRequests(withIdentifiers: [request.identifier])
            let key = request.identifier.hasPrefix("stock:") ? "low:"+String(request.identifier.dropFirst(6)) : request.identifier
            if jobs.contains(where: { $0.0 == request.identifier }) { alerts.removeValue(forKey: key) }
        }
        for (id,at,key,value) in selected {
            let content = UNMutableNotificationContent();content.title = l("Supply reminder")
            content.body = l(id.hasPrefix("rx:") ? "Your selected prescription request date has arrived." : "Review your estimated supply in the app.");content.sound = .default
            center.add(UNNotificationRequest(identifier: id,content: content,trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1,Double(at-stamp)/1000),repeats: false))) { error in
                if error != nil { Task { @MainActor in self.attempt { try self.change { $0.supplyAlerts?.removeValue(forKey: key) } };self.error = l("Notifications are turned off. Your reminder times are saved.") } }
            }
            alerts[key] = value
        }
        if alerts != state.supplyAlerts { attempt { try change { $0.supplyAlerts = alerts } } }
    }
}
