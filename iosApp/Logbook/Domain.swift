import Foundation
import LogbookShared

func l(_ key: String, _ args: CVarArg...) -> String {
    let text = TextCatalog.shared.text(key: key, language: Locale.current.language.languageCode?.identifier ?? "en")
    return args.isEmpty ? text : String(format: text.replacingOccurrences(of: "%s", with: "%@"), locale: Locale.current, arguments: args)
}
func encoded<T: Encodable>(_ value: T) throws -> String { String(data: try JSONEncoder().encode(value), encoding: .utf8)! }
func decoded<T: Decodable>(_ value: String, as type: T.Type) throws -> T { try JSONDecoder().decode(type, from: Data(value.utf8)) }
func millis(_ date: Date = Date()) -> Int64 { Int64(date.timeIntervalSince1970 * 1000) }
func date(_ value: Int64) -> Date { Date(timeIntervalSince1970: Double(value) / 1000) }
func zoneOffset(_ date: Date) -> String {
    let seconds = TimeZone.current.secondsFromGMT(for: date)
    return seconds == 0 ? "Z" : String(format: "%@%02d:%02d", seconds < 0 ? "-" : "+", abs(seconds) / 3600, (abs(seconds) % 3600) / 60)
}
func number(_ value: Double) -> String { value.formatted(.number.precision(.fractionLength(0...10)).grouping(.never)) }
func parseNumber(_ value: String) -> Double? {
    let result = Double(value.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: "."))
    return result.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
}
enum AppError: Error { case invalid; case validation(String) }
let presetTitles = ["METHYLPHENIDATE_IR": "Methylphenidate IR", "CONCERTA": "Methylphenidate ER (Concerta-type)", "LISDEXAMFETAMINE": "Elvanse / Vyvanse", "ATOMOXETINE": "Atomoxetine / Strattera", "CUSTOM": "Custom medication"]
let presetOrder = ["METHYLPHENIDATE_IR", "CONCERTA", "LISDEXAMFETAMINE", "ATOMOXETINE", "CUSTOM"]
func model(_ preset: String) -> String? {
    switch preset { case "METHYLPHENIDATE_IR": return "ritalin-ir-v1"; case "LISDEXAMFETAMINE": return "vyvanse-capsule-v1"; default: return nil }
}
struct Med: Codable, Identifiable {
    var id: Int64; var preset: String; var usualDose: Double; var active = true
    var name: String; var formulation: String; var strength = ""; var unit = "mg"; var modelId: String?; var revision: Int64 = 1
}
struct Entry: Codable, Identifiable {
    var id: Int64; var medicationId: Int64; var preset: String; var doseMg: Double; var timestamp: Int64
    var zoneId: String; var offset: String; var mood: Int?; var notes: String
    var medicationName: String; var formulation: String; var strength: String; var unit: String; var modelId: String?;var supplyUnits: Double?
}
struct ReminderValue: Codable, Identifiable {
    var id: Int; var hour: Int; var minute: Int; var medicationId: Int64?; var followUp = false; var cutoffMinutes = 120; var revision: Int64 = 1
}
struct OccurrenceValue: Codable, Identifiable {
    var id: String; var reminderId: Int; var scheduled: Int64; var expires: Int64; var revision: Int64; var medicationRevision: Int64?
    var state = "pending"; var nextAlert: Int64; var delivered = false; var followedUp = false; var entryId: Int64?
}
struct Document: Codable {
    var version = 4; var createdAt = millis(); var medications: [Med] = []; var entries: [Entry] = []; var reminders: [ReminderValue] = []
    var preferences: [String: String] = ["onboarded": "false", "haptics": "false"]
    var observations: [ObservationValue] = [];var nonUse: [NonUseValue] = [];var pause = PauseValue();var supplies: [SupplyValue] = [];var stock: [MovementValue] = [];var measurements: [MeasurementValue] = []
    enum CodingKeys: String,CodingKey { case version,createdAt,medications,entries,reminders,preferences,observations,nonUse,pause,supplies,stock,measurements }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decode(Int.self,forKey: .version);createdAt = try c.decode(Int64.self,forKey: .createdAt)
        medications = try c.decode([Med].self,forKey: .medications);entries = try c.decode([Entry].self,forKey: .entries)
        reminders = try c.decode([ReminderValue].self,forKey: .reminders);preferences = try c.decode([String:String].self,forKey: .preferences)
        observations = try c.decodeIfPresent([ObservationValue].self,forKey: .observations) ?? []
        nonUse = try c.decodeIfPresent([NonUseValue].self,forKey: .nonUse) ?? []
        pause = try c.decodeIfPresent(PauseValue.self,forKey: .pause) ?? PauseValue()
        supplies = try c.decodeIfPresent([SupplyValue].self,forKey: .supplies) ?? []
        measurements = try c.decodeIfPresent([MeasurementValue].self,forKey: .measurements) ?? []
        stock = try c.decodeIfPresent([MovementValue].self,forKey: .stock) ?? []
    }
    func validated() throws -> Document {
        let source = try encoded(self)
        if let issue = NativeBridge.shared.validationIssue(document: source) { throw AppError.validation(issue) }
        let result = try decoded(NativeBridge.shared.canonicalBackup(document: source), as: Document.self)
        guard result.measurements.allSatisfy({ TimeZone(identifier: $0.zoneId) != nil && fixedOffset($0.offset) != nil }),
              result.entries.allSatisfy({ TimeZone(identifier: $0.zoneId) != nil && fixedOffset($0.offset) != nil }),
              result.observations.allSatisfy({ TimeZone(identifier: $0.zoneId) != nil && fixedOffset($0.offset) != nil && ($0.sleepDate == nil || validDay($0.sleepDate!)) }),
              result.nonUse.allSatisfy({ TimeZone(identifier: $0.zoneId) != nil && fixedOffset($0.offset) != nil }),
              Set(result.reminders.map { $0.hour * 60 + $0.minute }).count == result.reminders.count else { throw AppError.invalid }
        return result
    }
}
struct DiskState: Codable {
    var schema = 1; var document = Document(); var actions: [String: Int64] = [:]; var occurrences: [OccurrenceValue] = []
    var supplyAlerts: [String:String]?
    var supplyNotificationsEnabled: Bool?
    var remindersEnabled = false; var recovery: Document?; var lastBackup: Date?
}

func fixedOffset(_ value: String) -> TimeZone? {
    if value == "Z" { return TimeZone(secondsFromGMT: 0) }
    let parts = value.dropFirst().split(separator: ":")
    guard (value.first == "+" || value.first == "-"), parts.count == 2,
          parts[0].count == 2, parts[1].count == 2,
          parts.allSatisfy({ $0.allSatisfy { $0.isASCII && $0.isNumber } }),
          let hours = Int(parts[0]), let minutes = Int(parts[1]), hours <= 18, minutes < 60,
          hours < 18 || minutes == 0 else { return nil }
    return TimeZone(secondsFromGMT: (hours * 3600 + minutes * 60) * (value.first == "-" ? -1 : 1))
}

func validDay(_ value: String) -> Bool {
    let f = DateFormatter();f.locale = Locale(identifier: "en_US_POSIX");f.dateFormat = "yyyy-MM-dd";f.isLenient = false
    return f.date(from: value).map { f.string(from: $0) == value } ?? false
}
