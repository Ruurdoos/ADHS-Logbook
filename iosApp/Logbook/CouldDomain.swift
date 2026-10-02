import Foundation
import LogbookShared

struct MeasurementValue: Codable,Identifiable {
    var id = UUID().uuidString;var kind: String;var value: Double;var unit: String;var diastolic: Double?
    var timestamp: Int64;var createdAt: Int64;var zoneId: String;var offset: String;var notes = ""
}
func measurementName(_ kind: String) -> String { ["pressure":"Blood pressure","pulse":"Pulse","weight":"Weight"][kind] ?? kind }
func measurementText(_ m: MeasurementValue) -> String { l(measurementName(m.kind))+": "+number(m.value)+(m.diastolic.map { " / "+number($0) } ?? "")+" "+m.unit }
func blankMeasurement() -> MeasurementValue { let now = millis();return MeasurementValue(kind: "pressure",value: 0,unit: "mmHg",timestamp: now,createdAt: now,zoneId: TimeZone.current.identifier,offset: zoneOffset(date(now))) }
struct QuickConfigurationValue: Codable { var token: String;var medicationId: Int64;var revision: Int64 }
struct ReviewRouteValue: Codable { var medicationId: Int64?;var unavailable: Bool;var changed: Bool }
struct DistributionValue: Codable { var category: String;var response: String;var value: Int?;var count: Int;var scaleVersion: Int? }
struct MoodCountValue: Codable { var value: Int;var count: Int }
struct WeekDayValue: Codable { var start: Int64;var doses: [Entry];var observations: [ObservationValue];var nonUse: [NonUseValue];var measurements: [MeasurementValue] }
struct WeeklyValue: Codable { var summary: SummaryValue;var days: [WeekDayValue];var distributions: [DistributionValue];var legacyMoods: [MoodCountValue] }
func weekStart(_ day: Date) -> Date { Calendar.current.dateInterval(of: .weekOfYear,for: day)!.start }
extension Document {
    func weekly(_ day: Date) throws -> WeeklyValue {
        let start = weekStart(day);let bounds = (0...7).map { millis(Calendar.current.date(byAdding: .day,value: $0,to: start)!) }
        return try decoded(NativeBridge.shared.weekly(document: encoded(self),boundaries: encoded(bounds)),as: WeeklyValue.self)
    }
}
