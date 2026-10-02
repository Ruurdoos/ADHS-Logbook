import SwiftUI
import LogbookShared

struct MeasurementForm: View {
    @EnvironmentObject var store: LogbookStore
    @Environment(\.dismiss) var dismiss
    @State var original: MeasurementValue
    @State var kind = "pressure"
    @State var value = ""
    @State var lower = ""
    @State var unit = "mmHg"
    @State var time = Date()
    @State var notes = ""
    @State var convert = false
    @State var deleting = false
    init(original: MeasurementValue) {
        _original = State(initialValue: original);_kind = State(initialValue: original.kind)
        _value = State(initialValue: original.value > 0 ? number(original.value) : "");_lower = State(initialValue: original.diastolic.map(number) ?? "")
        _unit = State(initialValue: original.unit);_time = State(initialValue: date(original.timestamp));_notes = State(initialValue: original.notes)
    }
    var existing: Bool { store.state.document.measurements.contains { $0.id == original.id } }
    var body: some View {
        NavigationStack {
            Form {
                Picker(l("Measurements"),selection: $kind) { ForEach(["pressure","pulse","weight"],id: \.self) { Text(l(measurementName($0))).tag($0) } }
                    .onChange(of: kind) { k in unit = k == "pressure" ? "mmHg" : k == "pulse" ? "bpm" : "kg";value = "";lower = "" }
                TextField(l(kind == "pressure" ? "Systolic (mmHg)" : kind == "pulse" ? "Pulse (bpm)" : "Weight value"),text: $value).keyboardType(.decimalPad)
                if kind == "pressure" { TextField(l("Diastolic (mmHg)"),text: $lower).keyboardType(.decimalPad) }
                if kind == "weight" {
                    Picker(l("Unit"),selection: $unit) { Text("kg").tag("kg");Text("lb").tag("lb") }
                    Toggle(l("Show converted value"),isOn: $convert)
                    if convert, let value = parseNumber(value), let converted = try? NativeBridge.shared.convertWeight(value: value,from: unit,to: unit == "kg" ? "lb" : "kg") { Text("≈ "+number(converted)+" "+(unit == "kg" ? "lb" : "kg")) }
                    Text(l("The entered value and unit are retained. Conversion is display only."))
                }
                DatePicker(l("Measured at"),selection: $time,in: ...Date())
                TextField(l("Notes (optional)"),text: $notes,axis: .vertical).lineLimit(2...8)
                Text(l("Manual records only. No clinical interpretation is provided."))
                if existing { Button(l("Delete record"),role: .destructive) { deleting = true } }
            }.navigationTitle(l("Measurements"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button(l("Save")) {
                        var m = original;m.kind = kind;m.value = parseNumber(value)!;m.diastolic = kind == "pressure" ? parseNumber(lower) : nil;m.unit = unit;m.notes = notes
                        if abs(m.timestamp-millis(time)) > 1 { m.zoneId = TimeZone.current.identifier;m.offset = zoneOffset(time) };m.timestamp = millis(time)
                        store.attempt { try store.saveMeasurement(m);dismiss() }
                    }.disabled(parseNumber(value) == nil || (kind == "pressure" && parseNumber(lower) == nil) || notes.count > 5000) }
                }
                .confirmationDialog(l("Delete record?"),isPresented: $deleting) { Button(l("Delete"),role: .destructive) { store.attempt { try store.deleteMeasurement(original.id);dismiss() } } }
        }
    }
}
struct WeeklyView: View {
    @EnvironmentObject var store: LogbookStore
    @State var selected = Date()
    @State private var week: WeeklyValue?
    var body: some View {
        List {
            DatePicker(l("Choose week"),selection: $selected,displayedComponents: .date)
            HStack { Button(l("Previous week")) { selected = Calendar.current.date(byAdding: .day,value: -7,to: selected)! };Spacer();Button(l("Next week")) { selected = Calendar.current.date(byAdding: .day,value: 7,to: selected)! } }
            if let week {
                Text(date(week.days[0].start).formatted(date: .abbreviated,time: .omitted)+" – "+date(week.days[6].start).formatted(date: .abbreviated,time: .omitted)+" · "+TimeZone.current.identifier)
                ForEach(Array(week.summary.lines.enumerated()),id: \.offset) { Text($0.element) }
                WeeklyDetails(week: week, medications: store.state.document.medications)

            }
        }.navigationTitle(l("Weekly overview"))
        .task(id: "\(store.revision)-\(selected)-\(TimeZone.current.identifier)") {
            let document = store.state.document,day = selected
            let result = await Task.detached { try? document.weekly(day) }.value
            guard !Task.isCancelled else { return };week = result
        }
    }
}
private struct WeeklyDetails: View {
    let week: WeeklyValue
    let medications: [Med]
    var body: some View {
        Section(l("Observation distributions")) {
            ForEach(Array(week.distributions.enumerated()),id: \.offset) { item in
                Text(distributionText(item.element))
            }
        }
        Section(l("Legacy dose mood (separate from observations)")) {
            ForEach(week.legacyMoods,id: \.value) { mood in
                Text(l(["Very low","Low","Okay","Good","Very good"][mood.value])+": \(mood.count)")
            }
        }
        ForEach(week.days,id: \.start) { day in WeeklyDaySection(day: day,medications: medications) }
    }
    private func distributionText(_ d: DistributionValue) -> String {
        let rating = d.value.map { " \($0) / 4" } ?? ""
        return l(categoryName(d.category))+" · "+l(ratingDescription(d.category,d.scaleVersion ?? 1))+" · "+l(responseName(d.response))+rating+": \(d.count)"
    }
}
private struct WeeklyDaySection: View {
    let day: WeekDayValue
    let medications: [Med]
    var body: some View {
        Section(date(day.start).formatted(date: .complete,time: .omitted)) {
            if day.doses.isEmpty && day.observations.isEmpty && day.nonUse.isEmpty && day.measurements.isEmpty { Text(l("No records. Status unknown.")) }
            ForEach(day.doses) { e in record(doseText(e),notes: e.notes) }
            ForEach(day.observations) { o in record(observationText(o),notes: o.notes) }
            ForEach(day.measurements) { m in record(time(m.timestamp)+" · "+measurementText(m),notes: m.notes) }
            ForEach(day.nonUse) { n in
                VStack(alignment: .leading) {
                    Text(l(nonUseName(n.recordKind))+" · "+(medications.first { $0.id == n.medicationId }?.name ?? ""))
                    Text(date(n.start).formatted()+" – "+date(n.end).formatted())
                    if !n.notes.isEmpty { Text(n.notes) }
                }
            }
        }
    }
    private func record(_ text: String,notes: String) -> some View {
        VStack(alignment: .leading) { Text(text);if !notes.isEmpty { Text(notes) } }
    }
    private func time(_ timestamp: Int64) -> String { date(timestamp).formatted(date: .omitted,time: .shortened) }
    private func doseText(_ e: Entry) -> String { [time(e.timestamp),e.medicationName,e.formulation,e.strength,number(e.doseMg)+" "+e.unit].joined(separator: " · ") }
    private func observationText(_ o: ObservationValue) -> String {
        let rating = o.value.map { " \($0) / 4" } ?? ""
        return time(o.timestamp)+" · "+l(categoryName(o.category))+" · "+l(responseName(o.response))+rating
    }
}
struct QuickAccessView: View {
    @EnvironmentObject var store: LogbookStore
    @State var enabled = UserDefaults.standard.bool(forKey: "quick.enabled")
    @State var selected: Int64 = 0
    @State var showLast = UserDefaults.standard.bool(forKey: "quick.last")
    @State var saved = false
    var body: some View {
        Form {
            Toggle(l("Enable quick access"),isOn: $enabled).disabled(WidgetFiles.directory == nil).onChange(of: enabled) { value in UserDefaults.standard.set(value,forKey: "quick.enabled");store.publishWidget() }
            if WidgetFiles.directory == nil { Text(l("Widget sharing is unavailable in this build.")) }
            Text(l("Shortcuts open review. Opening a link never saves a dose."))
            if enabled {
                Text(l("In Shortcuts, add Open dose editor. Leave the shortcut empty for generic review, or choose Configured medication after setup here."))
                Picker(l("Optional medication shortcut"),selection: $selected) { Text(l("Choose medication")).tag(Int64(0));ForEach(store.state.document.medications.filter(\.active)) { Text($0.name).tag($0.id) } }
                Button(l("Create review shortcut")) { if let med = store.state.document.medications.first(where: { $0.id == selected && $0.active }) { store.attempt { let config = QuickConfigurationValue(token: UUID().uuidString,medicationId: med.id,revision: med.revision);UserDefaults.standard.set(try JSONEncoder().encode(config),forKey: "quick.configuration");store.publishWidget();saved = true } } }.disabled(selected == 0)
                if saved { Text(l("Review shortcut configured.")) }
                Text(l("Add ADHS Logbook from the Lock Screen widget gallery. It opens review after unlocking."))
                Toggle(l("Show last-log timestamp on Lock Screen"),isOn: $showLast).onChange(of: showLast) { value in UserDefaults.standard.set(value,forKey: "quick.last");store.publishWidget() }
                Text(l("Off by default. App lock always hides the timestamp."))
                Text(l("Public labels stay generic. Changes to medication settings require review."))
            }
        }.navigationTitle(l("Quick access"))
            .onAppear { if let data = UserDefaults.standard.data(forKey: "quick.configuration"),let c = try? JSONDecoder().decode(QuickConfigurationValue.self,from: data) { selected = c.medicationId } }
    }
}
