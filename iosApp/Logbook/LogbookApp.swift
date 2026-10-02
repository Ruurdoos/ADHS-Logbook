import SwiftUI
import Charts
import LogbookShared

@main
struct LogbookApp: App {
    @StateObject private var privacy = PrivacyController.shared
    @StateObject private var store = LogbookStore()
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
    func loggingActions(_ med: Med) -> some View {
        VStack {
            Button { linkedOccurrence = nil;entryEditor = newEntry(med) } label: {
                Text(l("Log dose")).fixedSize(horizontal: false,vertical: true).frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent).foregroundStyle(Color(.systemBackground)).controlSize(.large)
            Button(l("Log now · %s %s", number(med.usualDose), l(med.unit))) {
                store.attempt { try store.quick(med) }
            }.frame(minHeight: 44)
            if let id = store.undoID {
                Button(l("Dose logged")+" · "+l("Undo")) { store.attempt { try store.delete(id) } }.frame(minHeight: 44)
            }
        }.frame(maxWidth: .infinity).padding().background(.regularMaterial)
    }
    var body: some View {
        Group {
            if store.state.document.preferences["onboarded"] != "true" {
                ScrollView { VStack(alignment: .leading, spacing: 24) {
                    Text(l("A little clarity,\nevery day.")).font(.largeTitle.bold())
                    Text(l("Log your medication. Keep notes when you want to."))
                    Spacer();Text(l("Private. Stored on this device.")).font(.footnote)
                    Button { medEditor = blankMedication() } label: { Text(l("Get started")).fixedSize(horizontal: false,vertical: true) }.buttonStyle(.borderedProminent).foregroundStyle(Color(.systemBackground)).controlSize(.large)
                }.padding(24) }
            } else {
                TabView(selection: $tab) {
                    NavigationStack {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 24) {
                                if store.state.document.preferences["observations_enabled"] == "true" { Button(l("Add observation")) { observation = blankObservation() } }
                                if let med = medication, let supply = store.state.document.supplies.first(where: { $0.medicationId == med.id }), let balance = try? store.state.document.balance(med.id), balance.inconsistent || balance.remaining <= supply.lowThreshold || (supply.prescriptionDate ?? Int64.max) <= millis() {
                                    NavigationLink(l("Estimated remaining: %s %s",number(balance.remaining),supply.unitLabel)) { SupplyForm() }
                                }
                                if active.count > 1 { Picker(l("Medication"), selection: Binding(get: { medication?.id ?? 0 }, set: { selected = $0 })) { ForEach(active) { Text($0.name).tag($0.id) } }.pickerStyle(.menu) }
                                if let med = medication {
                                    if typeSize.isAccessibilitySize { loggingActions(med) }
                                    TimelineView(.periodic(from: .now, by: 30)) { _ in
                                        if let last = store.state.document.entries.first(where: { $0.medicationId == med.id }) {
                                            VStack(alignment: .leading, spacing: 8) {
                                                Text(l("Last logged")).font(.headline)
                                                Text("\(last.medicationName) · \(number(last.doseMg)) \(l(last.unit))")
                                                Text(date(last.timestamp), format: .dateTime.day().month().year().hour().minute())
                                                Text(date(last.timestamp), style: .relative).foregroundStyle(.secondary)
                                            }
                                        }
                                        if !store.state.document.entries.contains(where: { $0.medicationId == med.id && Calendar.current.isDateInToday(date($0.timestamp)) }) { Text(l("No dose logged today.")).foregroundStyle(.secondary) }
                                        EstimateView(med: med, document: store.state.document)
                                    }
                                } else { Button(l("Add medication")) { medEditor = blankMedication() } }
                            }.padding(24)
                        }.navigationTitle(l("Today"))
                            .safeAreaInset(edge: .bottom) {
                                if let med = medication, !typeSize.isAccessibilitySize {
                                    loggingActions(med)
                                }
                            }
                    }.tabItem { Label(l("Home"), systemImage: "house") }.tag(0)
                    NavigationStack { HistoryView { entryEditor = $0;linkedOccurrence = nil } }.tabItem { Label(l("History"), systemImage: "clock") }.tag(1)
                    NavigationStack { ExportView() }.tabItem { Label(l("Export"), systemImage: "square.and.arrow.up") }.tag(2)
                    NavigationStack { SettingsView(edit: { medEditor = $0 }) }.tabItem { Label(l("Settings"), systemImage: "gear") }.tag(3)
                }
            }
        }
        .sheet(item: $observation) { ObservationForm(value: $0) }
        .sheet(item: $medEditor) { MedicationForm(value: $0) }
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
    let med: Med;let document: Document
    @State var showInfo = false
    var points: [Double] { (try? decoded(NativeBridge.shared.curve(document: encoded(document), medicationId: med.id, now: millis()), as: [Double].self)) ?? [] }
    var phase: String {
        let key = (try? NativeBridge.shared.phase(document: encoded(document), medicationId: med.id, now: millis())) ?? "UNAVAILABLE"
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
            .alert(l("About this estimate"), isPresented: $showInfo) { Button(l("Got it")) {} } message: {
                Text(l("A simplified curve uses typical peak times and half-lives from product labels. It is not a validated prediction of your blood level, symptom control, or safe dosing.")+"\n"+l("Curves are scaled separately. They cannot compare medications or days.")+"\n"+l("Sources: DailyMed Ritalin, Ritalin LA (IR comparison) and Vyvanse capsule labels. Models: ritalin-ir-v1 / vyvanse-capsule-v1. Engine: relative-heuristic-v1."))
            }
    }
}
struct MedicationForm: View {
    @EnvironmentObject var store: LogbookStore
    @Environment(\.dismiss) var dismiss
    @State var value: Med
    @State var dose = ""
    @State var error = false
    var body: some View {
        NavigationStack {
            Form {
                Picker(l("Medication"), selection: $value.preset) { ForEach(presetOrder, id: \.self) { Text(l(presetTitles[$0]!)).tag($0) } }
                    .onChange(of: value.preset) { preset in value.name = preset == "CUSTOM" ? "" : presetTitles[preset]!;value.formulation = preset == "CUSTOM" ? "" : preset;value.modelId = model(preset);value.unit = "mg" }
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
                if error { Text(l("Enter a positive dose.")).foregroundStyle(.red) }
            }.navigationTitle(l(value.id == 0 ? "Add your medication" : "Edit medication"))
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { dismiss() } };ToolbarItem(placement: .confirmationAction) { Button(l("Save")) {
                    guard let parsed = parseNumber(dose), !value.name.trimmingCharacters(in: .whitespaces).isEmpty else { error = true;return }
                    value.usualDose = parsed;store.attempt { try store.save(value);dismiss() }
                } } }
        }.onAppear { dose = value.usualDose > 0 ? number(value.usualDose) : "" }
    }
}
struct EntryForm: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    @EnvironmentObject var store: LogbookStore
    @Environment(\.dismiss) var dismiss
    @State var value: Entry
    let occurrence: String?
    @State var supplyUnits = ""
    @State var nonUse: NonUseValue?
    @State var dose = ""
    @State var time = Date()
    @State var useNow = true
    @State var optional = false
    @State var error = false
    @State var confirmDelete = false
    @State var actionID = UUID().uuidString
    let moods = ["Very low","Low","Okay","Good","Very good"]
    var body: some View {
        NavigationStack {
            Form {
                if value.id == 0 { Picker(l("Medication"), selection: $value.medicationId) { ForEach(store.state.document.medications.filter(\.active)) { Text($0.name).tag($0.id) } }.onChange(of: value.medicationId) { id in
                    if let med = store.state.document.medications.first(where: { $0.id == id }) {
                        value.preset = med.preset;value.medicationName = med.name;value.formulation = med.formulation;value.unit = med.unit;value.strength = med.strength;value.modelId = med.modelId;dose = number(med.usualDose)
                    }
                } } else { Text(value.medicationName) }
                if value.id == 0 && typeSize.isAccessibilitySize { Text(value.medicationName) }
                TextField(l("Dose (%s)",l(value.unit)), text: $dose).keyboardType(.decimalPad)
                if store.state.document.supplies.contains(where: { $0.medicationId == value.medicationId }) {
                    TextField(l("Stock units used (optional)"),text: $supplyUnits).keyboardType(.decimalPad)
                    Text(l("Only for supply tracking; separate from dose amount.")).font(.footnote)
                }
                if let occurrence, value.id == 0 {
                    Button(l("Record not taken")) { var record = blankNonUse();record.medicationId = value.medicationId;record.occurrenceId = occurrence;nonUse = record }
                }
                if value.id == 0 { Toggle(l("Taken now"), isOn: $useNow) }
                if !useNow { DatePicker(l("Time"), selection: $time, in: ...Date()) }
                DisclosureGroup(l("How are you feeling?"), isExpanded: $optional) {
                    ForEach(0..<5, id: \.self) { i in Button { value.mood = value.mood == i ? nil : i } label: { HStack { Text(l(moods[i]));Spacer();if value.mood == i { Image(systemName: "checkmark") } } }.accessibilityAddTraits(value.mood == i ? .isSelected : []) }
                }
                TextField(l("Notes (optional)"), text: $value.notes, axis: .vertical).lineLimit(3...8)
                if error { Text(l("Enter a positive dose.")).foregroundStyle(.red) }
                if value.id != 0 { Button(l("Delete entry"), role: .destructive) { confirmDelete = true } }
            }.navigationTitle(l(value.id == 0 ? "Log dose" : "Edit entry"))
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { dismiss() } };ToolbarItem(placement: .confirmationAction) { Button(l("Save")) {
                    guard let parsed = parseNumber(dose), value.notes.count <= 5000 else { error = true;return }
                    let units = Double(supplyUnits.replacingOccurrences(of: ",",with: "."))
                    guard supplyUnits.isEmpty || units.map({ $0.isFinite && $0 >= 0 }) == true else { error = true;return }
                    value.supplyUnits = units
                    let actual = useNow ? Date() : time;value.doseMg = parsed
                    if value.id == 0 || millis(time) != value.timestamp { value.timestamp = millis(actual);value.zoneId = TimeZone.current.identifier;value.offset = zoneOffset(actual) }
                    store.attempt { try store.save(value, action: actionID, occurrence: occurrence);dismiss() }
                } } }
                .confirmationDialog(l("Delete this entry?"), isPresented: $confirmDelete, titleVisibility: .visible) { Button(l("Delete"), role: .destructive) { store.attempt { try store.delete(value.id);dismiss() } } } message: { Text(l("This removes the dose and its notes from your log. This cannot be undone.")) }
        }.sheet(item: $nonUse,onDismiss: { if let occurrence, store.state.document.nonUse.contains(where: { $0.occurrenceId == occurrence }) { dismiss() } }) { NonUseForm(value: $0) }
        .onAppear { supplyUnits = value.supplyUnits.map(number) ?? "";dose = number(value.doseMg);time = date(value.timestamp);useNow = value.id == 0;optional = value.mood != nil }
    }
}
struct HistoryView: View {
    @EnvironmentObject var store: LogbookStore
    var edit: (Entry) -> Void
    @State var filter = false
    @State var day = Date()
    var entries: [Entry] { store.state.document.entries.filter { !filter || Calendar.current.isDate(date($0.timestamp),inSameDayAs: day) } }
    var body: some View {
        List {
            if store.state.document.preferences["weekly_enabled"] == "true" { NavigationLink(l("Weekly overview")) { WeeklyView() } }
            NavigationLink(l("Observations & non-use")) { ObservationList() }
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
