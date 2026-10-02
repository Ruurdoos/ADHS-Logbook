import Foundation
import LogbookShared

let observationTypes = ["focus","mood","appetite","sleep","symptom","benefit","fading"]
let observationResponses = ["rated","none","unsure","recorded"]
func categoryName(_ key: String) -> String { ["focus":"Focus / everyday functioning","mood":"Mood","appetite":"Appetite","sleep":"Sleep","symptom":"Symptom / suspected side effect","benefit":"User-noticed benefit","fading":"User-noticed fading"][key] ?? key }
func responseName(_ key: String) -> String { ["rated":"Rating","none":"None","unsure":"Unsure","recorded":"Recorded time"][key] ?? key }
struct ObservationValue: Codable, Identifiable {
    var id = UUID().uuidString;var category: String;var response: String;var value: Int?
    var timestamp: Int64;var createdAt: Int64;var zoneId: String;var offset: String
    var scaleVersion = 1;var sleepDate: String?;var doseId: Int64?;var notes = ""
}
struct NonUseValue: Codable, Identifiable {
    var id = UUID().uuidString;var medicationId: Int64;var start: Int64;var end: Int64
    var createdAt: Int64;var zoneId: String;var offset: String;var notes = "";var occurrenceId: String?
}
struct PauseValue: Codable { var paused = false;var until: Int64?;func active(_ now: Int64 = millis()) -> Bool { paused && (until == nil || now < until!) } }
struct SupplyValue: Codable, Identifiable {
    var id: Int64 { medicationId };var medicationId: Int64;var unitLabel: String;var countedAt: Int64
    var lowThreshold: Double;var dosePerUnit: Double?;var doseUnit: String;var prescriptionDate: Int64?;var revision: Int64 = 1
}
struct MovementValue: Codable, Identifiable {
    var id: String;var medicationId: Int64;var kind: String;var timestamp: Int64;var units: Double;var entryId: Int64?
}
struct BalanceValue: Codable { var remaining: Double;var uncountedLogs: Int;var inconsistent: Bool }
struct MedicationSummaryValue: Codable { var medicationId: Int64;var name: String;var formulation: String;var strength: String;var unit: String;var count: Int;var total: Double }
struct ObservationSummaryValue: Codable { var category: String;var count: Int;var rated: Int;var none: Int;var unsure: Int;var recorded: Int }
struct SummaryValue: Codable {
    var days: Int;var doses: Int;var doseDays: Int;var observationDays: Int;var nonUseDays: Int;var daysWithoutRecords: Int
    var medications: [MedicationSummaryValue];var observations: [ObservationSummaryValue];var nonUseRecords: Int;var measurementDays: Int;var measurements: Int
    var lines: [String] {
        var result = [l("Recorded doses: %d",doses),l("Recorded measurements: %d",measurements),l("Days with measurements: %d / %d",measurementDays,days),l("Days with dose records: %d / %d",doseDays,days),l("Days with observations: %d / %d",observationDays,days),l("Days with explicit non-use records: %d / %d",nonUseDays,days),l("Days without records: %d / %d",daysWithoutRecords,days),l("Missing records are unknown, not confirmed non-use. Counts do not measure adherence or causation.")]
        result += medications.map { "\($0.name) · \($0.formulation) · \($0.strength): "+l("%d recorded doses; total %s %s",$0.count,number($0.total),l($0.unit)) }
        result += observations.map { l(categoryName($0.category))+": "+l("%d records: %d rated, %d none, %d unsure, %d recorded times",$0.count,$0.rated,$0.none,$0.unsure,$0.recorded) }
        return result
    }
}
extension Document {
    func balance(_ medicationId: Int64) throws -> BalanceValue { try decoded(NativeBridge.shared.stockBalance(document: encoded(self),medicationId: medicationId),as: BalanceValue.self) }
    func summary(from: Date,to: Date) throws -> SummaryValue {
        var bounds: [Int64] = [];var current = Calendar.current.startOfDay(for: from)
        let end = Calendar.current.date(byAdding: .day,value: 1,to: Calendar.current.startOfDay(for: to))!
        guard current < end && end.timeIntervalSince(current) < 368*86400 else { throw AppError.invalid }
        while current <= end { bounds.append(millis(current));current = Calendar.current.date(byAdding: .day,value: 1,to: current)! }
        return try decoded(NativeBridge.shared.summary(document: encoded(self),boundaries: encoded(bounds)),as: SummaryValue.self)
    }
}
