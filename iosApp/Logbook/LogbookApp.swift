import SwiftUI
import Charts
import LogbookShared

@main
struct LogbookApp: App {
    @StateObject private var privacy = PrivacyController.shared
    @StateObject private var store = LogbookApp.makeStore()
    private static func makeStore() -> LogbookStore {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "--ui-test-data"),args.indices.contains(i+1),UUID(uuidString: args[i+1]) != nil {
            return LogbookStore(directory: FileManager.default.temporaryDirectory.appendingPathComponent("uitest-"+args[i+1]),notifications: false)
        }
        #endif
        return LogbookStore()
    }
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store)
                .opacity(privacy.enabled && !privacy.unlocked ? 0 : 1)
                .accessibilityHidden(privacy.enabled && !privacy.unlocked)
                .tint(Color(uiColor: UIColor { traits in
                    traits.userInterfaceStyle == .dark ? UIColor(red: 0.66,green: 0.84,blue: 0.72,alpha: 1) : UIColor(red: 0.255,green: 0.42,blue: 0.35,alpha: 1)
                }))
                .onAppear { if privacy.enabled && !privacy.unlocked { privacy.showShield() };store.schedule();store.processWidget() }
                .onChange(of: scenePhase) { phase in if phase == .active { store.schedule();store.processWidget() } else { privacy.cover() } }
                .onReceive(NotificationCenter.default.publisher(for: .widgetAction)) { _ in store.processWidget() }
                .onOpenURL { url in if url.scheme == "adhslogbook" && url.host == "widget" { try? WidgetFiles.queue("widget-review:"+url.lastPathComponent);store.processWidget() } else if url.scheme == "adhslogbook" && url.host == "review" { try? WidgetFiles.queue("review:"+url.lastPathComponent);store.processWidget() } }
                .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name.NSSystemTimeZoneDidChange)) { _ in store.schedule() }
        }
    }
}
struct RootView: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    @EnvironmentObject var store: LogbookStore
    @State var observation: ObservationValue?
    @State var medEditor: Med?
    @State var entryEditor: Entry?
    @State var selected: Int64?
    @State var tab = 0
    @State var linkedOccurrence: String?
    var active: [Med] { store.state.document.medications.filter(\.active) }
    var medication: Med? { active.first { $0.id == selected } ?? active.first }
    func newEntry(_ med: Med) -> Entry {
        Entry(id: 0, medicationId: med.id, preset: med.preset, doseMg: med.usualDose, timestamp: millis(), zoneId: TimeZone.current.identifier, offset: zoneOffset(Date()), notes: "", medicationName: med.name, formulation: med.formulation, strength: med.strength, unit: med.unit, modelId: med.modelId)
    }
    @State private var restoring = false
    @State private var measurement: MeasurementValue?
    @State private var nonUse: NonUseValue?
    @State private var recording = false
    @State private var pendingRecord: (() -> Void)?
    var measurementsEnabled: Bool { store.state.document.preferences["measurements_enabled"] == "true" }
    func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 16, content: content).frame(maxWidth: .infinity, alignment: .leading).padding()
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }
    /// Not taken from Home covers today so far; a completed day can still be chosen in the form.
    func notTakenToday(_ med: Med) -> NonUseValue {
        let now = Date(), day = Calendar.current.startOfDay(for: now)
        return NonUseValue(medicationId: med.id, start: millis(day), end: millis(now), createdAt: millis(), zoneId: TimeZone.current.identifier, offset: zoneOffset(day), kind: "period")
    }
    func medicationCard(_ med: Med) -> some View {
        card {
            VStack(alignment: .leading, spacing: 4) {
                Text(med.name).font(.title3.weight(.semibold))
                Text(l("Usual amount: %s %s", number(med.usualDose), l(med.unit))).font(.footnote).foregroundStyle(.secondary)
            }
            TimelineView(.periodic(from: .now, by: 30)) { _ in
                VStack(alignment: .leading, spacing: 4) {
                    if let last = store.state.document.entries.first(where: { $0.medicationId == med.id }) {
                        Text(l("Last logged")).font(.headline)
                        Text("\(number(last.doseMg)) \(l(last.unit)) · ") + Text(date(last.timestamp), format: .dateTime.day().month().year().hour().minute())
                        Text(date(last.timestamp), style: .relative).foregroundStyle(.secondary)
                    }
                    if !store.state.document.entries.contains(where: { $0.medicationId == med.id && Calendar.current.isDateInToday(date($0.timestamp)) }) { Text(l("No dose logged today.")).foregroundStyle(.secondary) }
                }
            }
            Button {
                guard !store.busy else { return };store.attempt { try await store.quick(med) }
            } label: {
                Label(l("Taken · %s %s", number(med.usualDose), l(med.unit)), systemImage: "checkmark").fixedSize(horizontal: false,vertical: true).frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).foregroundStyle(Color(.systemBackground)).controlSize(.large)
            Button { nonUse = notTakenToday(med) } label: { Text(l("Not taken")).fixedSize(horizontal: false,vertical: true).frame(maxWidth: .infinity) }
                .buttonStyle(.bordered).buttonBorderShape(.capsule).controlSize(.large)
        }
    }
    @ViewBuilder var undoBanner: some View {
        if let id = store.undoID, let entry = store.state.document.entries.first(where: { $0.id == id }) {
            let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading)) : AnyLayout(HStackLayout())
            layout {
                Text(l("Dose logged")+" · "+number(entry.doseMg)+" "+l(entry.unit)).frame(maxWidth: .infinity, alignment: .leading)
                Button(l("Change")) { store.undoID = nil;linkedOccurrence = nil;entryEditor = entry }.frame(minHeight: 44)
                Button(l("Undo")) { store.attempt { try await store.delete(id) } }.frame(minHeight: 44)
            }.padding(.horizontal).padding(.vertical, 8).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                .task(id: id) {
                    guard !UIAccessibility.isVoiceOverRunning else { return }
                    try? await Task.sleep(nanoseconds: 10_000_000_000)
                    if !Task.isCancelled && store.undoID == id { store.undoID = nil }
                }
        }
    }
    var recordButton: some View {
        Button { recording = true } label: {
            Text(l("＋ Record")).fixedSize(horizontal: false,vertical: true).frame(maxWidth: .infinity).padding(.vertical,14)
                .overlay(Capsule().strokeBorder(Color.accentColor,lineWidth: 2)).contentShape(Capsule())
        }.buttonStyle(.plain).foregroundStyle(Color.accentColor).font(.body.weight(.semibold))
    }
    struct TodayRow: Identifiable { let id: String;let time: Int64;let icon: String;let title: String;let open: () -> Void }
    var todayRows: [TodayRow] {
        let document = store.state.document
        let from = millis(Calendar.current.startOfDay(for: Date())), until = millis(Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: Date()))!)
        var rows = document.entries.filter { $0.timestamp >= from && $0.timestamp < until }.map { entry in
            TodayRow(id: "dose:\(entry.id)", time: entry.timestamp, icon: "pills", title: "\(entry.medicationName) · \(number(entry.doseMg)) \(l(entry.unit))") { linkedOccurrence = nil;entryEditor = entry }
        }
        rows += document.observations.filter { $0.timestamp >= from && $0.timestamp < until }.map { record in
            let answer = record.response == "rated" && record.value != nil ? "\(record.value!) / 4" : l(responseName(record.response))
            return TodayRow(id: "observation:"+record.id, time: record.timestamp, icon: "square.and.pencil", title: l(observationShortName(record.category))+" · "+answer) { observation = record }
        }
        rows += document.measurements.filter { $0.timestamp >= from && $0.timestamp < until }.map { record in
            TodayRow(id: "measurement:"+record.id, time: record.timestamp, icon: "waveform.path.ecg", title: measurementText(record)) { measurement = record }
        }
        rows += document.nonUse.filter { $0.intersects(from, until) }.map { record in
            TodayRow(id: "nonuse:"+record.id, time: record.start, icon: "minus.circle", title: l(nonUseName(record.recordKind))+(document.medications.first { $0.id == record.medicationId }.map { " · "+$0.name } ?? "")) { nonUse = record }
        }
        return rows.sorted { $0.time > $1.time }
    }
    var todaySection: some View {
        let today = Calendar.current.startOfDay(for: Date())
        return VStack(alignment: .leading, spacing: 8) {
            Text(l("Recorded today")).font(.headline)
            let rows = todayRows
            if rows.isEmpty { Text(l("Nothing recorded today")).foregroundStyle(.secondary) }
            ForEach(rows) { row in
                Button(action: row.open) {
                    HStack(spacing: 16) {
                        Image(systemName: row.icon).foregroundStyle(Color.accentColor).accessibilityHidden(true)
                        VStack(alignment: .leading) {
                            Text(row.title).foregroundStyle(.primary)
                            Text(date(row.time), format: date(row.time) >= today ? .dateTime.hour().minute() : .dateTime.day().month().hour().minute()).font(.caption).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "chevron.right").foregroundStyle(.secondary).accessibilityHidden(true)
                    }.padding(.horizontal).padding(.vertical, 10).frame(minHeight: 56).contentShape(Rectangle())
                }.buttonStyle(.plain).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
            }
            NavigationLink(l("All observations & non-use")) { ObservationList() }.frame(minHeight: 44)
        }
    }
    func recordOption(_ icon: String, _ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button { pendingRecord = action;recording = false } label: {
            HStack(spacing: 16) {
                Image(systemName: icon).foregroundStyle(Color.accentColor).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) { Text(l(title)).font(.headline).foregroundStyle(.primary);Text(l(subtitle)).font(.footnote).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").foregroundStyle(.secondary).accessibilityHidden(true)
            }.padding().frame(minHeight: 64).contentShape(Rectangle())
        }.buttonStyle(.plain).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }
    /// Single entry point for everything other than the usual dose taken now.
    var recordSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let med = medication { recordOption("pills", "Medication", "Different amount or time") { linkedOccurrence = nil;entryEditor = newEntry(med) } }
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 16) {
                            Image(systemName: "square.and.pencil").foregroundStyle(Color.accentColor).accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 2) { Text(l("How am I doing?")).font(.headline);Text(l("Choose what you noticed.")).font(.footnote).foregroundStyle(.secondary) }
                        }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: typeSize.isAccessibilitySize ? 240 : 110), spacing: 8)], alignment: .leading, spacing: 8) {
                            ForEach(observationTypes, id: \.self) { category in
                                Button { pendingRecord = { observation = blankObservation(category) };recording = false } label: { Text(l(observationShortName(category))).frame(maxWidth: .infinity, minHeight: 32) }
                                    .buttonStyle(.bordered).buttonBorderShape(.capsule)
                            }
                        }
                    }.padding().frame(maxWidth: .infinity, alignment: .leading).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                    if let med = medication { recordOption("minus.circle", "Not taken", "A missed dose or a time without medication") { nonUse = notTakenToday(med) } }
                    if medication == nil || measurementsEnabled { recordOption("waveform.path.ecg", "Measurement", "Blood pressure, pulse or weight") { measurement = blankMeasurement() } }
                }.padding()
            }.background(Color(.systemGroupedBackground))
                .navigationTitle(l("What do you want to record?")).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Close")) { recording = false } } }
        }.presentationDetents([.medium, .large])
    }
    var body: some View {
        Group {
            if store.loading { ProgressView(l("Working…")) } else if !store.readable { VStack(spacing: 20) { Text(l("Your log could not be opened. Restore a backup to recover your records."));Button(l("Restore backup")) { restoring = true } } } else if store.state.document.preferences["onboarded"] != "true" {
                ScrollView { VStack(alignment: .leading, spacing: 24) {
                    Text(l("A little clarity,\nevery day.")).font(.largeTitle.bold())
                    Text(l("Record observations and measurements, with or without medication."))
                    Spacer();Text(l("Private. Stored on this device.")).font(.footnote)
                    VStack(spacing: 8) {
                        Button { medEditor = blankMedication() } label: { Text(l("Get started")).fixedSize(horizontal: false,vertical: true).frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).foregroundStyle(Color(.systemBackground)).controlSize(.large)
                        RestoreBackupButton().frame(minHeight: 44)
                    }
                }.padding(24) }
            } else {
                TabView(selection: $tab) {
                    NavigationStack {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 24) {
                                if let med = medication, let supply = store.state.document.supplies.first(where: { $0.medicationId == med.id }), let balance = try? store.state.document.balance(med.id), balance.inconsistent || balance.remaining <= supply.lowThreshold || (supply.prescriptionDate ?? Int64.max) <= millis() {
                                    NavigationLink(l("Estimated remaining: %s %s",number(balance.remaining),supply.unitLabel)) { SupplyForm(medId: med.id) }
                                }
                                if typeSize.isAccessibilitySize { undoBanner }
                                if active.count > 1, let current = medication {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack {
                                            ForEach(active) { med in
                                                Button { selected = med.id } label: { Group { if med.id == current.id { Label(med.name, systemImage: "checkmark") } else { Text(med.name) } }.frame(minHeight: 32) }
                                                    .buttonStyle(.bordered).buttonBorderShape(.capsule).tint(med.id == current.id ? .accentColor : .secondary)
                                                    .accessibilityAddTraits(med.id == current.id ? .isSelected : [])
                                            }
                                        }
                                    }.accessibilityLabel(l("Medication"))
                                }
                                if let med = medication {
                                    medicationCard(med)
                                    EstimateView(med: med, document: store.state.document, revision: store.revision)
                                } else {
                                    card {
                                        Text(l("Your log starts here.")).font(.headline)
                                        Text(l("Record observations and measurements, with or without medication.")).font(.footnote)
                                        Button { medEditor = blankMedication() } label: { Text(l("Add medication")).frame(maxWidth: .infinity) }.buttonStyle(.bordered).controlSize(.large)
                                    }
                                }
                                todaySection
                                if typeSize.isAccessibilitySize { recordButton }
                            }.padding(24)
                        }.navigationTitle(l("Today"))
                            .safeAreaInset(edge: .bottom) {
                                if !typeSize.isAccessibilitySize {
                                    VStack(spacing: 8) { undoBanner;recordButton }.frame(maxWidth: .infinity).padding().background(.regularMaterial)
                                }
                            }
                    }.tabItem { Label(l("Home"), systemImage: "house") }.tag(0)
                    NavigationStack { HistoryView { entryEditor = $0;linkedOccurrence = nil } }.tabItem { Label(l("History"), systemImage: "clock") }.tag(1)
                    NavigationStack { ExportView() }.tabItem { Label(l("Export"), systemImage: "square.and.arrow.up") }.tag(2)
                    NavigationStack { SettingsView(edit: { medEditor = $0 }) }.tabItem { Label(l("Settings"), systemImage: "gear") }.tag(3)
                }
            }
        }
        .disabled(store.busy)
        .sheet(isPresented: $restoring) { NavigationStack { BackupView().toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Close")) { restoring = false } } } } }
        .sheet(item: $measurement) { MeasurementForm(original: $0) }
        .sheet(item: $observation) { ObservationForm(value: $0) }
        .sheet(item: $nonUse) { NonUseForm(value: $0) }
        .sheet(isPresented: $recording, onDismiss: { pendingRecord?();pendingRecord = nil }) { recordSheet }
        .sheet(item: $medEditor) { MedicationForm(value: $0, onboarding: store.state.document.preferences["onboarded"] != "true") }
        .sheet(item: $entryEditor) { EntryForm(value: $0, occurrence: linkedOccurrence) }
        .alert(l("Something went wrong"), isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button(l("Close")) { store.error = nil } } message: { Text(store.error ?? "") }
        .onChange(of: store.widgetReview) { id in
            if let med = active.first(where: { $0.id == id }) { entryEditor = newEntry(med);linkedOccurrence = nil };store.widgetReview = nil
        }
        .onChange(of: store.openOccurrence) { id in
            if let o = store.state.occurrences.first(where: { $0.id == id }), let r = store.state.document.reminders.first(where: { $0.id == o.reminderId }), let med = active.first(where: { $0.id == r.medicationId }) ?? medication {
                linkedOccurrence = id;entryEditor = newEntry(med)
            };store.openOccurrence = nil
        }
    }
}
func blankMedication() -> Med { Med(id: 0, preset: "METHYLPHENIDATE_IR", usualDose: 0, name: "Methylphenidate IR", formulation: "METHYLPHENIDATE_IR", modelId: model("METHYLPHENIDATE_IR")) }
struct EstimateView: View {
    let med: Med;let document: Document;let revision: Int
    @State var showInfo = false
    @State private var points: [Double] = []
    @State private var phaseKey = "UNAVAILABLE"
    var phase: String {
        let key = phaseKey
        return l(["UNAVAILABLE":"Estimate unavailable", "NO_LOGS":"No doses logged", "RECENT":"Recently logged", "LOW":"Low estimated level", "PEAK":"Near estimated peak", "RISING":"Estimate increasing", "DECREASING":"Estimate decreasing"][key] ?? "Estimate unavailable")
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(l("Estimated level")).font(.headline)
            Text(phase).font(.subheadline)
            if med.modelId == nil { Text(l("Estimate unavailable")) }
            else if document.entries.contains(where: { $0.medicationId == med.id }) {
                Chart {
                    ForEach(Array(points.enumerated()), id: \.offset) { item in LineMark(x: .value("Time", item.offset), y: .value("Relative",item.element)) }
                    RuleMark(x: .value("Time",144)).lineStyle(StrokeStyle(dash: [3]))
                }.chartYAxis(.hidden).chartXAxis(.hidden).frame(height: 150)
                    .accessibilityLabel(l("Relative estimate for the last 24 hours. %s. Rightmost point: now.",phase))
                HStack { Text(l("24h"));Spacer();Text(l("Now")) }.font(.caption)
            } else { Text(l("Log a dose to see your estimate.")) }
            Text(l("Rough relative estimate, not a medical measurement. Do not use it to decide when to take a dose.")).font(.footnote)
            Button(l("About this estimate")) { showInfo = true }
        }.padding().background(Color(.secondarySystemGroupedBackground),in: RoundedRectangle(cornerRadius: 12))
            .task(id: "\(med.id)-\(revision)-\(millis()/30000)") {
                let snapshot = document,medID = med.id,now = millis()
                let result = await Task.detached { () -> ([Double],String) in
                    guard let json = try? encoded(snapshot) else { return ([],"UNAVAILABLE") }
                    return ((try? decoded(NativeBridge.shared.curve(document: json,medicationId: medID,now: now),as: [Double].self)) ?? [],(try? NativeBridge.shared.phase(document: json,medicationId: medID,now: now)) ?? "UNAVAILABLE")
                }.value
                guard !Task.isCancelled else { return };points = result.0;phaseKey = result.1
            }
            .alert(l("About this estimate"), isPresented: $showInfo) { Button(l("Got it")) {} } message: {
                Text(l("A simplified curve uses typical peak times and half-lives from product labels. It is not a validated prediction of your blood level, symptom control, or safe dosing.")+"\n"+l("Curves are scaled separately. They cannot compare medications or days.")+"\n"+l("Sources: DailyMed Ritalin, Ritalin LA (IR comparison) and Vyvanse capsule labels. Models: ritalin-ir-v1 / vyvanse-capsule-v1. Engine: relative-heuristic-v1."))
            }
    }
}
struct MedicationForm: View {
    @EnvironmentObject var store: LogbookStore
    @State private var discarding = false
    @Environment(\.dismiss) var dismiss
    @State var value: Med
    var onboarding = false
    @State var dose = ""
    @State var error: String?
    var noMedication: Bool { value.preset == "NONE" }
    var body: some View {
        NavigationStack {
            Form {
                Picker(l("Medication"), selection: $value.preset) {
                    ForEach(presetOrder, id: \.self) { Text(l(presetTitles[$0]!)).tag($0) }
                    if onboarding { Text(l("No medication")).tag("NONE") }
                }
                    .onChange(of: value.preset) { preset in guard preset != "NONE" else { error = nil;return };value.name = preset == "CUSTOM" ? "" : presetTitles[preset]!;value.formulation = preset == "CUSTOM" ? "" : preset;value.modelId = model(preset);value.unit = "mg" }
                if noMedication {
                    Text(l("Log observations and measurements without medication. You can add a medication later.")).font(.footnote)
                } else {
                    if value.preset == "CUSTOM" {
                        TextField(l("Medication name"), text: $value.name)
                        TextField(l("Formulation (optional)"), text: $value.formulation)
                        TextField(l("Strength with unit (optional)"), text: $value.strength)
                        Text(l("For your records only; no dose conversion.")).font(.footnote)
                        Picker(l("Dose"), selection: $value.unit) { ForEach(["mg","ml","unit"], id: \.self) { Text(l($0)).tag($0) } }
                    }
                    TextField(l("Your usual dose (%s)",l(value.unit)), text: $dose).keyboardType(.decimalPad)
                    Text(l("Enter your prescribed dose. You can add another medication later.")).font(.footnote)
                    if value.modelId == nil { Text(l("Logging is available. No supported estimate is available for this formulation.")).font(.footnote) }
                }
                if let error { Text(l(error)).foregroundStyle(.red).accessibilityAddTraits(.isStaticText) }
            }.navigationTitle(l(value.id == 0 ? "Add your medication" : "Edit medication"))
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { discarding = true } };ToolbarItem(placement: .confirmationAction) { Button(l(onboarding ? "Start" : "Save")) { guard !store.busy else { return }
                    if noMedication { store.attempt { try await store.change { $0.document.preferences["onboarded"] = "true";$0.document.preferences["observations_enabled"] = "true";$0.document.preferences["measurements_enabled"] = "true" };dismiss() };return }
                    guard !value.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { error = "Enter a medication name.";return };guard value.name.count <= 200 && value.formulation.count <= 200 && value.strength.count <= 100 else { error = "Medication details are too long. Shorten the name, formulation or strength.";return };guard let parsed = parseNumber(dose) else { error = "Enter a positive dose.";return }
                    value.usualDose = parsed;store.attempt { try await store.save(value);dismiss() }
                } } }
        }.disabled(store.busy).interactiveDismissDisabled()
        .confirmationDialog(l("Discard changes?"),isPresented: $discarding,titleVisibility: .visible) { Button(l("Discard"),role: .destructive) { dismiss() };Button(l("Keep editing"),role: .cancel) {} }
        .onAppear { dose = value.usualDose > 0 ? number(value.usualDose) : "" }
    }
}
struct EntryForm: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    @EnvironmentObject var store: LogbookStore
    @State private var discarding = false
    @Environment(\.dismiss) var dismiss
    @State var value: Entry
    let occurrence: String?
    @State var supplyUnits = ""
    @State var nonUse: NonUseValue?
    @State var dose = ""
    @State var time = Date()
    @State var useNow = true
    @State var optional = false
    @State var error: String?
    @State var confirmDelete = false
    @State var actionID = UUID().uuidString
    @State private var snapshot: Entry?
    let moods = ["Very low","Low","Okay","Good","Very good"]
    var body: some View {
        NavigationStack {
            Form {
                Picker(l("Medication"), selection: $value.medicationId) { ForEach(store.state.document.medications.filter { $0.active || $0.id == value.medicationId }) { Text($0.name).tag($0.id) } }.onChange(of: value.medicationId) { id in
                    if let original = snapshot,original.medicationId == id {
                        value.preset = original.preset;value.medicationName = original.medicationName;value.formulation = original.formulation;value.unit = original.unit;value.strength = original.strength;value.modelId = original.modelId;dose = number(original.doseMg);supplyUnits = original.supplyUnits.map(number) ?? ""
                    } else if let med = store.state.document.medications.first(where: { $0.id == id }) {
                        value.preset = med.preset;value.medicationName = med.name;value.formulation = med.formulation;value.unit = med.unit;value.strength = med.strength;value.modelId = med.modelId;dose = value.id == 0 ? number(med.usualDose) : "";supplyUnits = ""
                    }
                }
                if value.id != 0 { Text(l("When changing medication, enter the amount again and check its unit.")) }
                if value.id == 0 && typeSize.isAccessibilitySize { Text(value.medicationName) }
                Text(l("Dose (%s)",l(value.unit))).font(.headline)
                TextField(l("Dose (%s)",l(value.unit)), text: $dose).keyboardType(.decimalPad)
                if store.state.document.supplies.contains(where: { $0.medicationId == value.medicationId }) {
                    TextField(l("Stock units used (optional)"),text: $supplyUnits).keyboardType(.decimalPad)
                    Text(l("Only for supply tracking; separate from dose amount.")).font(.footnote)
                }
                if let occurrence, value.id == 0 {
                    Button(l("Record not taken")) { var record = blankNonUse();record.medicationId = value.medicationId;record.occurrenceId = occurrence;record.kind = "scheduled";if let scheduled = store.state.occurrences.first(where: { $0.id == occurrence })?.scheduled { record.start = scheduled;record.end = scheduled;record.offset = zoneOffset(date(scheduled)) };nonUse = record }
                }
                if value.id == 0 { Toggle(l("Taken now"), isOn: $useNow) }
                if !useNow { DatePicker(l("Time"), selection: $time, in: ...Date()) }
                if snapshot?.mood != nil {
                    DisclosureGroup(l("How are you feeling?"), isExpanded: $optional) {
                        ForEach(0..<5, id: \.self) { i in Button { value.mood = value.mood == i ? nil : i } label: { HStack { Text(l(moods[i]));Spacer();if value.mood == i { Image(systemName: "checkmark") } } }.accessibilityAddTraits(value.mood == i ? .isSelected : []) }
                    }
                }
                TextField(l("Notes (optional)"), text: $value.notes, axis: .vertical).lineLimit(3...8)
                if let error { Text(l(error)).foregroundStyle(.red).accessibilityAddTraits(.isStaticText) }
                if value.id != 0 { Button(l("Delete entry"), role: .destructive) { confirmDelete = true } }
            }.navigationTitle(l(value.id == 0 ? "Log dose" : "Edit entry"))
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { discarding = true } };ToolbarItem(placement: .confirmationAction) { Button(l("Save")) { guard !store.busy else { return };
                    guard let parsed = parseNumber(dose) else { error = "Enter a positive dose.";return };guard value.notes.count <= 5000 else { error = "Notes must be 5,000 characters or fewer.";return }
                    let units = Double(supplyUnits.replacingOccurrences(of: ",",with: "."))
                    guard supplyUnits.isEmpty || units.map({ $0.isFinite && $0 >= 0 }) == true else { error = "Enter zero or a positive stock amount.";return }
                    value.supplyUnits = units
                    let actual = useNow ? Date() : time;value.doseMg = parsed
                    if value.id == 0 || millis(time) != value.timestamp { value.timestamp = millis(actual);value.zoneId = TimeZone.current.identifier;value.offset = zoneOffset(actual) }
                    store.attempt { try await store.save(value, action: actionID, occurrence: occurrence);dismiss() }
                } } }
                .confirmationDialog(l("Delete this entry?"), isPresented: $confirmDelete, titleVisibility: .visible) { Button(l("Delete"), role: .destructive) { store.attempt { try await store.delete(value.id);dismiss() } } } message: { Text(l("This removes the dose and its notes from your log. This cannot be undone.")) }
        }.disabled(store.busy).interactiveDismissDisabled()
        .confirmationDialog(l("Discard changes?"),isPresented: $discarding,titleVisibility: .visible) { Button(l("Discard"),role: .destructive) { dismiss() };Button(l("Keep editing"),role: .cancel) {} }
        .sheet(item: $nonUse,onDismiss: { if let occurrence, store.state.document.nonUse.contains(where: { $0.occurrenceId == occurrence }) { dismiss() } }) { NonUseForm(value: $0) }
        .onAppear { guard snapshot == nil else { return };snapshot = value;supplyUnits = value.supplyUnits.map(number) ?? "";dose = number(value.doseMg);time = date(value.timestamp);useNow = value.id == 0;optional = value.mood != nil }
    }
}
struct HistoryView: View {
    @EnvironmentObject var store: LogbookStore
    var edit: (Entry) -> Void
    @State var filter = false
    @State var day = Date()
    @State var query = ""
    @State var medicationFilter: Int64 = 0
    var entries: [Entry] { store.state.document.entries.filter { (!filter || Calendar.current.isDate(date($0.timestamp),inSameDayAs: day)) && (medicationFilter == 0 || $0.medicationId == medicationFilter) && (query.isEmpty || $0.medicationName.localizedCaseInsensitiveContains(query) || $0.notes.localizedCaseInsensitiveContains(query)) } }
    var body: some View {
        List {
            if store.state.document.preferences["weekly_enabled"] == "true" { NavigationLink(l("Weekly overview")) { WeeklyView() } }
            NavigationLink(l("Observations & non-use")) { ObservationList() }
            TextField(l("Search medications and notes"),text: $query)
            Picker(l("Medication"),selection: $medicationFilter) { Text(l("All medications")).tag(Int64(0));ForEach(store.state.document.medications) { Text($0.name).tag($0.id) } }
            Toggle(l("Choose day"), isOn: $filter)
            if filter { DatePicker(l("Choose day"), selection: $day, displayedComponents: .date) }
            if entries.isEmpty { Text(l("No doses logged for this day.")) }
            ForEach(Dictionary(grouping: entries, by: { Calendar.current.startOfDay(for: date($0.timestamp)) }).keys.sorted(by: >), id: \.self) { day in
                Section(day.formatted(date: .abbreviated,time: .omitted)) {
                    ForEach(entries.filter { Calendar.current.isDate(date($0.timestamp),inSameDayAs: day) }) { entry in
                        Button { edit(entry) } label: { VStack(alignment: .leading, spacing: 6) { Text(entry.medicationName);Text("\(number(entry.doseMg)) \(l(entry.unit)) · \(date(entry.timestamp).formatted(date: .omitted,time: .shortened))").font(.subheadline).foregroundStyle(.secondary) } }.padding(.vertical,6)
                    }
                }
            }
        }.navigationTitle(l("History"))
    }
}
