import SwiftUI

func blankObservation() -> ObservationValue { ObservationValue(category: "focus",response: "",timestamp: millis(),createdAt: millis(),zoneId: TimeZone.current.identifier,offset: zoneOffset(Date())) }
func blankNonUse() -> NonUseValue { let now = millis();return NonUseValue(medicationId: 0,start: now,end: now,createdAt: now,zoneId: TimeZone.current.identifier,offset: zoneOffset(date(now))) }
struct ObservationForm: View {
    @EnvironmentObject var store: LogbookStore
    @Environment(\.dismiss) var dismiss
    @State var measurement: MeasurementValue?
    @State var value: ObservationValue
    @State var time = Date()
    @State var sleepDate = ""
    @State var deleting = false
    var existing: Bool { store.state.document.observations.contains { $0.id == value.id } }
    var body: some View {
        NavigationStack {
            Form {
                if store.state.document.preferences["measurements_enabled"] == "true" { Button(l("Add measurement")) { measurement = blankMeasurement() } }
                Picker(l("Category"),selection: $value.category) { ForEach(observationTypes,id: \.self) { Text(l(categoryName($0))).tag($0) } }
                    .onChange(of: value.category) { _ in value.response = "";value.value = nil }
                Picker(l("Response"),selection: $value.response) {
                    Text(l("Choose a response")).tag("");ForEach(observationResponses,id: \.self) { Text(l(responseName($0))).tag($0) }
                }
                if value.response == "rated" {
                    Text(l("0 = very low · 4 = very high"))
                    Picker(l("Rating"),selection: Binding(get: { value.value ?? -1 },set: { value.value = $0 })) {
                        Text(l("Choose a response")).tag(-1);ForEach(0..<5,id: \.self) { Text(String($0)).tag($0) }
                    }
                }
                DatePicker(l("Observation time"),selection: $time,in: ...Date())
                if value.category == "sleep" { TextField(l("Night starting on (YYYY-MM-DD)"),text: $sleepDate) }
                Picker(l("Optional dose link"),selection: Binding(get: { value.doseId ?? 0 },set: { value.doseId = $0 == 0 ? nil : $0 })) {
                    Text(l("No dose link")).tag(Int64(0));ForEach(store.state.document.entries) { Text($0.medicationName+" · "+date($0.timestamp).formatted(date: .abbreviated,time: .shortened)).tag($0.id) }
                }
                Text(l("A link records your context, not medication causation.")).font(.footnote)
                TextField(l("Notes (optional)"),text: $value.notes,axis: .vertical).lineLimit(2...8)
                if existing { Button(l("Delete record"),role: .destructive) { deleting = true } }
            }.navigationTitle(l(existing ? "Edit observation" : "Add observation"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button(l("Save")) {
                        if value.timestamp != millis(time) { value.zoneId = TimeZone.current.identifier;value.offset = zoneOffset(time) };value.timestamp = millis(time)
                        if value.response != "rated" { value.value = nil };value.sleepDate = value.category == "sleep" ? sleepDate : nil
                        store.attempt { try store.saveObservation(value);dismiss() }
                    }.disabled(value.response.isEmpty || value.notes.count > 5000 || (value.response == "rated" && (value.value ?? -1) < 0) || (value.category == "sleep" && !validDay(sleepDate))) }
                }
                .confirmationDialog(l("Delete record?"),isPresented: $deleting,titleVisibility: .visible) { Button(l("Delete"),role: .destructive) { store.attempt { try store.deleteObservation(value.id);dismiss() } } }
        }.sheet(item: $measurement) { MeasurementForm(original: $0) }.onAppear { time = date(value.timestamp);let f = DateFormatter();f.dateFormat = "yyyy-MM-dd";sleepDate = value.sleepDate ?? f.string(from: Calendar.current.date(byAdding: .day,value: -1,to: Date())!) }
    }
}
struct NonUseForm: View {
    @EnvironmentObject var store: LogbookStore
    @Environment(\.dismiss) var dismiss
    @State var value: NonUseValue
    @State var start = Date()
    @State var end = Date()
    @State var period = false
    @State var deleting = false
    var existing: Bool { store.state.document.nonUse.contains { $0.id == value.id } }
    var overlaps: Bool { store.state.document.entries.contains { $0.medicationId == value.medicationId && $0.timestamp >= millis(start) && $0.timestamp <= millis(period ? end : start) } }
    var body: some View {
        NavigationStack {
            Form {
                Text(l("Only record what you know. Pausing reminders does not record non-use."))
                Picker(l("Medication"),selection: $value.medicationId) { Text(l("Choose medication")).tag(Int64(0));ForEach(store.state.document.medications) { Text($0.name).tag($0.id) } }
                DatePicker(l("Start"),selection: $start,in: ...Date())
                Toggle(l("Record a period"),isOn: $period)
                if period { DatePicker(l("End"),selection: $end,in: ...Date()) }
                TextField(l("Notes (optional)"),text: $value.notes,axis: .vertical).lineLimit(2...8)
                if overlaps { Text(l("A dose is recorded in this non-use period. Correct one record first.")) }
                if existing { Button(l("Delete record"),role: .destructive) { deleting = true } }
            }.navigationTitle(l("Record not taken"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button(l("Save")) {
                        if value.start != millis(start) { value.zoneId = TimeZone.current.identifier;value.offset = zoneOffset(start) };value.start = millis(start);value.end = millis(period ? end : start)
                        store.attempt { try store.saveNonUse(value);dismiss() }
                    }.disabled(value.medicationId == 0 || overlaps || (period && end < start) || value.notes.count > 5000) }
                }.confirmationDialog(l("Delete record?"),isPresented: $deleting,titleVisibility: .visible) { Button(l("Delete"),role: .destructive) { store.attempt { try store.deleteNonUse(value.id);dismiss() } } }
        }.onAppear { start = date(value.start);end = date(value.end);period = value.end > value.start }
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
