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
