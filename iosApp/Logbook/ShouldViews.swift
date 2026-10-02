import SwiftUI

struct ObservationList: View {
    @EnvironmentObject var store: LogbookStore
    @State var editing: ObservationValue?
    @State var measurement: MeasurementValue?
    @State var nonUse: NonUseValue?
    var body: some View {
        List {
            Button(l("Add observation")) { editing = blankObservation() }
            if store.state.document.preferences["measurements_enabled"] == "true" { Button(l("Add measurement")) { measurement = blankMeasurement() } }
            Button(l("Record not taken")) { nonUse = blankNonUse() }
            if store.state.document.observations.isEmpty && store.state.document.nonUse.isEmpty && store.state.document.measurements.isEmpty { Text(l("No records yet. Unrecorded days remain unknown.")) }
            if store.state.document.preferences["measurements_enabled"] == "true" || !store.state.document.measurements.isEmpty { Section(l("Measurements")) { ForEach(store.state.document.measurements.sorted { $0.timestamp > $1.timestamp }) { m in Button { measurement = m } label: { VStack(alignment: .leading) { Text(measurementText(m));Text(date(m.timestamp).formatted(date: .abbreviated,time: .shortened)) } } } } }
            Section(l("Observations")) {
                ForEach(store.state.document.observations.sorted { $0.timestamp > $1.timestamp }) { record in
                    Button { editing = record } label: { VStack(alignment: .leading) { Text(l(categoryName(record.category)));Text(date(record.timestamp),format: .dateTime.day().month().year().hour().minute()).font(.caption) } }
                }
            }
            Section(l("Not taken")) {
                ForEach(store.state.document.nonUse.sorted { $0.start > $1.start }) { record in
                    Button { nonUse = record } label: { VStack(alignment: .leading) { Text(l(nonUseName(record.recordKind))+" · "+(store.state.document.medications.first { $0.id == record.medicationId }?.name ?? ""));Text(date(record.start),format: .dateTime.day().month().year().hour().minute()).font(.caption) } }
                }
            }
        }.navigationTitle(l("Observations"))
            .sheet(item: $editing) { ObservationForm(value: $0) }
            .sheet(item: $measurement) { MeasurementForm(original: $0) }
            .sheet(item: $nonUse) { NonUseForm(value: $0) }
    }
}
func blankObservation() -> ObservationValue { ObservationValue(category: "focus",response: "",timestamp: millis(),createdAt: millis(),zoneId: TimeZone.current.identifier,offset: zoneOffset(Date())) }
func blankNonUse() -> NonUseValue { let day = Calendar.current.startOfDay(for: Calendar.current.date(byAdding: .day,value: -1,to: Date())!);return NonUseValue(medicationId: 0,start: millis(day),end: millis(Calendar.current.date(byAdding: .day,value: 1,to: day)!),createdAt: millis(),zoneId: TimeZone.current.identifier,offset: zoneOffset(day),kind: "day") }
private let dayFormatter: DateFormatter = { let f = DateFormatter();f.locale = Locale(identifier: "en_US_POSIX");f.dateFormat = "yyyy-MM-dd";return f }()
struct ObservationForm: View {
    @EnvironmentObject var store: LogbookStore
    @State private var discarding = false
    @Environment(\.dismiss) var dismiss
    @State var measurement: MeasurementValue?
    @State var value: ObservationValue
    @State var time = Date()
    @State var sleepDate = ""
    @State private var initialized = false
    @State var deleting = false
    var existing: Bool { store.state.document.observations.contains { $0.id == value.id } }
    var body: some View {
        NavigationStack {
            Form {
                if store.state.document.preferences["measurements_enabled"] == "true" { Button(l("Add measurement")) { measurement = blankMeasurement() } }
                Picker(l("Category"),selection: $value.category) { ForEach(observationTypes,id: \.self) { Text(l(categoryName($0))).tag($0) } }
                    .onChange(of: value.category) { _ in value.response = "";value.value = nil;value.scaleVersion = value.category == "sleep" ? 2 : 1 }
                Picker(l("Response"),selection: $value.response) {
                    Text(l("Choose a response")).tag("");ForEach(observationResponses,id: \.self) { Text(l(responseName($0))).tag($0) }
                }
                if value.response == "rated" {
                    Text(l(ratingDescription(value.category,value.scaleVersion)))
                    Picker(l("Rating"),selection: Binding(get: { value.value ?? -1 },set: { value.value = $0 })) {
                        Text(l("Choose a response")).tag(-1);ForEach(0..<5,id: \.self) { Text(value.category == "sleep" && value.scaleVersion == 2 ? "\($0): \(l(sleepQualityLabels[$0]))" : String($0)).tag($0) }
                    }
                }
                DatePicker(l("Observation time"),selection: $time,in: ...Date())
                if value.category == "sleep" { DatePicker(l("Night starting on"),selection: Binding(get: { dayFormatter.date(from: sleepDate) ?? Date() },set: { sleepDate = dayFormatter.string(from: $0) }),in: ...Date(),displayedComponents: .date) }
                Picker(l("Optional dose link"),selection: Binding(get: { value.doseId ?? 0 },set: { value.doseId = $0 == 0 ? nil : $0 })) {
                    Text(l("No dose link")).tag(Int64(0));ForEach(store.state.document.entries) { Text($0.medicationName+" · "+date($0.timestamp).formatted(date: .abbreviated,time: .shortened)).tag($0.id) }
                }
                Text(l("A link records your context, not medication causation.")).font(.footnote)
                TextField(l("Notes (optional)"),text: $value.notes,axis: .vertical).lineLimit(2...8)
                if existing { Button(l("Delete record"),role: .destructive) { deleting = true } }
            }.navigationTitle(l(existing ? "Edit observation" : "Add observation"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { discarding = true } }
                    ToolbarItem(placement: .confirmationAction) { Button(l("Save")) { guard !store.busy else { return };
                        if value.timestamp != millis(time) { value.zoneId = TimeZone.current.identifier;value.offset = zoneOffset(time) };value.timestamp = millis(time)
                        if value.response != "rated" { value.value = nil };value.sleepDate = value.category == "sleep" ? sleepDate : nil
                        store.attempt { try await store.saveObservation(value);dismiss() }
                    }.disabled(value.response.isEmpty || value.notes.count > 5000 || (value.response == "rated" && (value.value ?? -1) < 0) || (value.category == "sleep" && !validDay(sleepDate))) }
                }
                .confirmationDialog(l("Delete record?"),isPresented: $deleting,titleVisibility: .visible) { Button(l("Delete"),role: .destructive) { store.attempt { try await store.deleteObservation(value.id);dismiss() } } }
        }.disabled(store.busy).interactiveDismissDisabled()
        .confirmationDialog(l("Discard changes?"),isPresented: $discarding,titleVisibility: .visible) { Button(l("Discard"),role: .destructive) { dismiss() };Button(l("Keep editing"),role: .cancel) {} }
        .sheet(item: $measurement) { MeasurementForm(original: $0) }.onAppear { guard !initialized else { return };initialized = true;time = date(value.timestamp);let f = DateFormatter();f.dateFormat = "yyyy-MM-dd";sleepDate = value.sleepDate ?? f.string(from: Calendar.current.date(byAdding: .day,value: -1,to: Date())!) }
    }
}
struct NonUseForm: View {
    @EnvironmentObject var store: LogbookStore
    @State private var discarding = false
    @Environment(\.dismiss) var dismiss
    @State var value: NonUseValue
    @State var start = Date()
    @State var end = Date()
    @State var deleting = false
    var existing: Bool { store.state.document.nonUse.contains { $0.id == value.id } }
    var from: Date { value.recordKind == "day" ? Calendar.current.startOfDay(for: start) : start }
    var until: Date { value.recordKind == "day" ? Calendar.current.date(byAdding: .day,value: 1,to: from)! : value.recordKind == "scheduled" ? start : end }
    var overlaps: Bool { var candidate = value;candidate.start = millis(from);candidate.end = millis(until);return store.state.document.entries.contains { $0.medicationId == value.medicationId && candidate.contains($0.timestamp) } }
    var body: some View {
        NavigationStack {
            Form {
                Text(l("Only record what you know. Pausing reminders does not record non-use."))
                Picker(l("Medication"),selection: $value.medicationId) { Text(l("Choose medication")).tag(Int64(0));ForEach(store.state.document.medications) { Text($0.name).tag($0.id) } }
                if !existing && value.occurrenceId == nil { Picker(l("Record type"),selection: Binding(get: { value.recordKind },set: { value.kind = $0 })) { Text(l(nonUseName("day"))).tag("day");Text(l(nonUseName("period"))).tag("period") } }
                else { Text(l(nonUseName(value.recordKind))) }
                if value.recordKind == "day" { Text(l("Choose a completed day. For today, record a period ending now."));DatePicker(l("Choose day"),selection: $start,in: ...Calendar.current.date(byAdding: .day,value: -1,to: Date())!,displayedComponents: .date) }
                else if value.recordKind == "scheduled" { Text(start.formatted()) }
                else { DatePicker(l("Start"),selection: $start,in: ...Date());DatePicker(l("End"),selection: $end,in: ...Date()) }
                TextField(l("Notes (optional)"),text: $value.notes,axis: .vertical).lineLimit(2...8)
                if overlaps { Text(l("A dose is recorded in this non-use period. Correct one record first.")) }
                if existing { Button(l("Delete record"),role: .destructive) { deleting = true } }
            }.navigationTitle(l("Record not taken"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { discarding = true } }
                    ToolbarItem(placement: .confirmationAction) { Button(l("Save")) { guard !store.busy else { return };
                        if value.start != millis(from) { value.zoneId = TimeZone.current.identifier;value.offset = zoneOffset(from) };value.start = millis(from);value.end = millis(until)
                        store.attempt { try await store.saveNonUse(value);dismiss() }
                    }.disabled(value.medicationId == 0 || overlaps || (until < from || until > Date() || value.recordKind == "period" && until == from) || value.notes.count > 5000) }
                }.confirmationDialog(l("Delete record?"),isPresented: $deleting,titleVisibility: .visible) { Button(l("Delete"),role: .destructive) { store.attempt { try await store.deleteNonUse(value.id);dismiss() } } }
        }.disabled(store.busy).interactiveDismissDisabled()
        .confirmationDialog(l("Discard changes?"),isPresented: $discarding,titleVisibility: .visible) { Button(l("Discard"),role: .destructive) { dismiss() };Button(l("Keep editing"),role: .cancel) {} }
        .onAppear { start = date(value.start);end = date(value.end) }
    }
}
struct PauseForm: View {
    @EnvironmentObject var store: LogbookStore
    @State var paused = false
    @State var timed = false
    @State var until = Date().addingTimeInterval(86400)
    var body: some View {
        Form {
            Toggle(l("Pause reminders"),isOn: $paused)
            Text(l("This pauses notifications only. It does not record medication use or non-use."))
            if paused { Toggle(l("Resume at a chosen time"),isOn: $timed);if timed { DatePicker(l("Resume at"),selection: $until,in: Date()...) } }
            Button(l("Save")) { store.attempt { try store.pause(PauseValue(paused: paused,until: paused && timed ? millis(until) : nil)) } }
        }.navigationTitle(l("Pause reminders"))
            .onAppear { paused = store.state.document.pause.active();timed = store.state.document.pause.until != nil;if let end = store.state.document.pause.until { until = date(end) } }
    }
}
struct SupplyForm: View {
    @EnvironmentObject var store: LogbookStore
    @State var medId: Int64 = 0
    @State var unit = ""
    @State var count = ""
    @State var threshold = ""
    @State var mapping = ""
    @State var rx = false
    @State var rxDate = Date().addingTimeInterval(7*86400)
    @State var restock = ""
    @State var action = UUID().uuidString
    @State var deleting = false
    var existing: SupplyValue? { store.state.document.supplies.first { $0.medicationId == medId } }
    func nonnegative(_ input: String) -> Double? { Double(input.replacingOccurrences(of: ",",with: ".")).flatMap { $0.isFinite && $0 >= 0 ? $0 : nil } }
    var body: some View {
        Form {
            Toggle(l("Supply notifications"),isOn: Binding(get: { store.state.supplyNotificationsEnabled == true },set: { store.enableSupplyNotifications($0) }))
            Text(l("After restore, notifications stay off until you enable them here.")).font(.footnote)
            Picker(l("Medication"),selection: $medId) { Text(l("Choose medication")).tag(Int64(0));ForEach(store.state.document.medications.filter(\.active)) { Text($0.name).tag($0.id) } }
                .onChange(of: medId) { _ in unit = existing?.unitLabel ?? "";count = "";threshold = existing.map { number($0.lowThreshold) } ?? "";mapping = existing?.dosePerUnit.map(number) ?? "";rx = existing?.prescriptionDate != nil;rxDate = existing?.prescriptionDate.map(date) ?? Date().addingTimeInterval(7*86400);action = UUID().uuidString }
            if medId != 0 {
                if let supply = existing, let balance = try? store.state.document.balance(medId) {
                    Text(l("Estimated remaining: %s %s",number(balance.remaining),supply.unitLabel))
                    if balance.inconsistent { Text(l("Count may be incomplete. Review uncounted logs or recount.")) }
                    TextField(l("Restock units"),text: $restock).keyboardType(.decimalPad)
                    Button(l("Add restock")) { store.attempt { try store.restock(medId,units: parseNumber(restock)!,action: action);restock = "";action = UUID().uuidString } }.disabled(parseNumber(restock) == nil)
                }
                Text(l("Use a physical count. Package units are separate from the logged dose."))
                TextField(l("Package unit label"),text: $unit)
                TextField(l("Counted stock now"),text: $count).keyboardType(.decimalPad)
                TextField(l("Low-stock threshold"),text: $threshold).keyboardType(.decimalPad)
                TextField(l("Dose amount per package unit (optional)"),text: $mapping).keyboardType(.decimalPad)
                Text(l("Mapping uses the medication's logging unit. Leave blank to record stock units explicitly in each dose. No conversion is inferred.")).font(.footnote)
                Toggle(l("Prescription request reminder"),isOn: $rx)
                if rx { DatePicker(l("Prescription request date"),selection: $rxDate,displayedComponents: [.date,.hourAndMinute]) }
                Button(l("Save count and settings")) { if let med = store.state.document.medications.first(where: { $0.id == medId }) {
                    let value = SupplyValue(medicationId: med.id,unitLabel: unit,countedAt: millis(),lowThreshold: nonnegative(threshold)!,dosePerUnit: parseNumber(mapping),doseUnit: med.unit,prescriptionDate: rx ? millis(rxDate) : nil,revision: (existing?.revision ?? 0)+1)
                    store.attempt { try store.saveSupply(value,count: nonnegative(count)!,action: action);count = "";action = UUID().uuidString }
                } }.disabled(unit.trimmingCharacters(in: .whitespaces).isEmpty || unit.count > 80 || nonnegative(count) == nil || nonnegative(threshold) == nil || (!mapping.isEmpty && parseNumber(mapping) == nil))
                if existing != nil { Button(l("Delete record"),role: .destructive) { deleting = true } }
            }
        }.navigationTitle(l("Supply"))
            .confirmationDialog(l("Delete record?"),isPresented: $deleting,titleVisibility: .visible) { Button(l("Delete"),role: .destructive) { store.attempt { try store.removeSupply(medId) } } }
    }
}
struct WidgetSettings: View {
    @EnvironmentObject var store: LogbookStore
    @State var medId: Int64 = 0
    @State var generic = true
    @State var saved = false
    var body: some View {
        Form {
            Picker(l("Medication"),selection: $medId) { Text(l("Choose medication")).tag(Int64(0));ForEach(store.state.document.medications.filter(\.active)) { Text($0.name).tag($0.id) } }
            Toggle(l("Generic widget content"),isOn: $generic)
            Text(l("App lock always hides widget details. Changed medication settings require review in the app."))
            Button(l("Save widget configuration")) { if let med = store.state.document.medications.first(where: { $0.id == medId }) { store.attempt { try store.configureWidget(med,generic: generic);saved = true } } }.disabled(medId == 0 || WidgetFiles.directory == nil)
            if WidgetFiles.directory == nil { Text(l("Widget sharing is unavailable in this build.")) }
            if saved { Text(l("Widget configured. Add it from your home screen's widget gallery if needed.")) }
        }.navigationTitle(l("Home-screen widget"))
            .onAppear { medId = Int64(UserDefaults.standard.integer(forKey: "widget.med"));generic = UserDefaults.standard.object(forKey: "widget.generic") == nil || UserDefaults.standard.bool(forKey: "widget.generic") }
    }
}
