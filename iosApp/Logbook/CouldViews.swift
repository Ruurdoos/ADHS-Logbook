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
