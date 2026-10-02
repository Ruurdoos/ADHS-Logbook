import SwiftUI
import UIKit
import LogbookShared

struct ExportView: View {
    @EnvironmentObject var store: LogbookStore
    @State var from = Calendar.current.date(byAdding: .day,value: -6,to: Date())!
    @State var to = Date()
    @State var summary = false
    @State var includeNotes = true
    @State var questions = ""
    @State var pdf = true
    @State var file: URL?
    @State var working = false
    var entries: [Entry] { let start = Calendar.current.startOfDay(for: from),end = Calendar.current.date(byAdding: .day,value: 1,to: Calendar.current.startOfDay(for: to))!;return store.state.document.entries.filter { date($0.timestamp) >= start && date($0.timestamp) < end } }
    var body: some View {
        Form {
            DatePicker(l("From"),selection: $from,displayedComponents: .date)
            DatePicker(l("To"),selection: $to,displayedComponents: .date)
            Button(l("Last 7 days")) { to = Date();from = Calendar.current.date(byAdding: .day,value: -6,to: to)!;file = nil }
            Button(l("Last 30 days")) { to = Date();from = Calendar.current.date(byAdding: .day,value: -29,to: to)!;file = nil }
            Picker(l("Export"),selection: $pdf) { Text(l("PDF report")).tag(true);Text("CSV").tag(false) }.pickerStyle(.segmented)
            Text(l("Only your selected dates are included. You choose where to share the file."))
            if to < Calendar.current.startOfDay(for: from) || to.timeIntervalSince(from) > 366*86400 {
                Text(l("Choose a valid range of up to 366 days."))
            } else if entries.isEmpty { Text(l("No doses logged. This does not confirm that no medication was taken.")) }
            Toggle(l("Summary with details"),isOn: $summary)
            Toggle(l("Include free-text notes"),isOn: $includeNotes)
            if summary {
                TextField(l("Questions for my appointment"),text: $questions,axis: .vertical).lineLimit(2...6)
                if let preview = try? store.state.document.summary(from: from,to: to) { ForEach(Array(preview.lines.enumerated()),id: \.offset) { Text($0.element) } }
            }
            Button(l("Export report")) {
                var document = store.state.document
                if !includeNotes { document.measurements = document.measurements.map { var m = $0;m.notes = "";return m };document.entries = document.entries.map { var e = $0;e.notes = "";return e };document.observations = document.observations.map { var o = $0;o.notes = "";return o };document.nonUse = document.nonUse.map { var n = $0;n.notes = "";return n } }
                let selectedEntries = document.entries.filter { e in entries.contains { $0.id == e.id } }, start = from, end = to, makePDF = pdf, makeSummary = summary, appointment = questions
                let captured = document
                working = true
                Task {
                    do { file = try await Task.detached { try NativeReports.create(document: captured,entries: selectedEntries,from: start,to: end,pdf: makePDF,summary: makeSummary,questions: appointment) }.value }
                    catch { store.error = l("Could not export this report. Please try again.") }
                    working = false
                }
            }.disabled(working || questions.count > 5000 || to < Calendar.current.startOfDay(for: from) || to.timeIntervalSince(from) > 366*86400)
            if let file { ShareLink(item: file) { Label(l("Export report"),systemImage: "square.and.arrow.up") } }
        }.navigationTitle(l("Export"))
            .onChange(of: from) { _ in file = nil }.onChange(of: to) { _ in file = nil }.onChange(of: pdf) { _ in file = nil }.onChange(of: summary) { _ in file = nil }.onChange(of: includeNotes) { _ in file = nil }.onChange(of: questions) { _ in file = nil }
    }
}
enum NativeReports {
    static func create(document: Document, entries: [Entry], from: Date, to: Date, pdf: Bool, summary: Bool = false, questions: String = "") throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("reports",isDirectory: true)
        try FileManager.default.createDirectory(at: folder,withIntermediateDirectories: true)
        for url in (try? FileManager.default.contentsOfDirectory(at: folder,includingPropertiesForKeys: [.creationDateKey])) ?? [] {
            if let created = try? url.resourceValues(forKeys: [.creationDateKey]).creationDate, Date().timeIntervalSince(created) > 7*86400 { try? FileManager.default.removeItem(at: url) }
        }
        let file = folder.appendingPathComponent("ADHS-logbook-\(UUID().uuidString).\(pdf ? "pdf" : "csv")")
        if !pdf {
            let headers = ["schema_version","record_type","record_id","medication_id","medication","formulation","amount","unit","timestamp_iso8601","end_timestamp_iso8601","timezone","category","response","scale_version","value","sleep_date","linked_dose_id","dose_mood","notes","strength","model_id","stock_units","measurement_kind","measurement_value","systolic","diastolic","created_at_iso8601"]
            var rows = [headers]
            let iso = ISO8601DateFormatter()
            func timestamp(_ stamp: Int64,_ offset: String) -> String { iso.timeZone = fixedOffset(offset);return iso.string(from: date(stamp)) }
            func row(_ values: [String:String]) { rows.append(headers.map { $0 == "schema_version" ? "3" : values[$0] ?? "" }) }
            for e in entries {
                row(["record_type":"dose","record_id":String(e.id),"medication_id":String(e.medicationId),"medication":e.medicationName,"formulation":e.formulation,"amount":String(e.doseMg),"unit":e.unit,"timestamp_iso8601":timestamp(e.timestamp,e.offset),"timezone":e.zoneId,"dose_mood":e.mood.map(String.init) ?? "","notes":e.notes,"strength":e.strength,"model_id":e.modelId ?? "","stock_units":e.supplyUnits.map { String($0) } ?? ""])
            }
            let a = millis(Calendar.current.startOfDay(for: from)),b = millis(Calendar.current.date(byAdding: .day,value: 1,to: Calendar.current.startOfDay(for: to))!)
            for o in document.observations where o.timestamp >= a && o.timestamp < b {
                row(["record_type":"observation","record_id":o.id,"timestamp_iso8601":timestamp(o.timestamp,o.offset),"timezone":o.zoneId,"category":o.category,"response":o.response,"scale_version":String(o.scaleVersion),"value":o.value.map(String.init) ?? "","sleep_date":o.sleepDate ?? "","linked_dose_id":o.doseId.map(String.init) ?? "","notes":o.notes])
            }
            for m in document.measurements where m.timestamp >= a && m.timestamp < b {
                row(["record_type":"measurement","record_id":m.id,"measurement_kind":m.kind,"measurement_value":m.kind == "pressure" ? "" : String(m.value),"systolic":m.kind == "pressure" ? String(m.value) : "","diastolic":m.diastolic.map { String($0) } ?? "","unit":m.unit,"timestamp_iso8601":timestamp(m.timestamp,m.offset),"created_at_iso8601":timestamp(m.createdAt,"Z"),"timezone":m.zoneId,"notes":m.notes])
            }
            for n in document.nonUse where n.start < b && n.end >= a {
                row(["record_type":"non_use","record_id":n.id,"medication_id":String(n.medicationId),"medication":document.medications.first { $0.id == n.medicationId }?.name ?? "","timestamp_iso8601":timestamp(n.start,n.offset),"end_timestamp_iso8601":timestamp(n.end,n.offset),"timezone":n.zoneId,"notes":n.notes])
            }
            let content = rows.map { $0.map { Csv.shared.cell(value: $0) }.joined(separator: ",") }.joined(separator: "\r\n")
            try content.write(to: file,atomically: true,encoding: .utf8)
        } else {
            let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0,y: 0,width: 595,height: 842))
            try renderer.writePDF(to: file) { context in
                var y: CGFloat = 40
                func page() {
                    context.beginPage();y = 40
                    (l("ADHS Logbook — Medication log") as NSString).draw(at: CGPoint(x: 40,y: y),withAttributes: [.font:UIFont.boldSystemFont(ofSize: 16)]);y += 26
                }
                func paragraph(_ text: String) {
                    // Wrap long notes into bounded paragraphs so every line can paginate.
                    for raw in text.components(separatedBy: .newlines) {
                        var remaining = raw[...]
                        repeat {
                            var chunk = String(remaining.prefix(120))
                            while chunk.count > 1 && (chunk as NSString).size(withAttributes: [.font:UIFont.systemFont(ofSize: 10)]).width > 515 { chunk.removeLast() }
                            if chunk.count < remaining.count, let space = chunk.lastIndex(of: " "), space != chunk.startIndex { chunk = String(chunk[..<space]) }
                            remaining = remaining.dropFirst(chunk.count)
                            while remaining.first == " " { remaining = remaining.dropFirst() }
                            let box = (chunk as NSString).boundingRect(with: CGSize(width: 515,height: 1000),options: [.usesLineFragmentOrigin],attributes: [.font:UIFont.systemFont(ofSize: 10)],context: nil)
                            if y + box.height + 8 > 790 { page() }
                            (chunk as NSString).draw(in: CGRect(x: 40,y: y,width: 515,height: box.height+2),withAttributes: [.font:UIFont.systemFont(ofSize: 10)]);y += box.height + 8
                        } while !remaining.isEmpty
                    }
                }
                func chart(_ values: [Double]) {
                    guard values.count > 1 else { return }
                    if y + 145 > 790 { page() }
                    let peak = max(values.max() ?? 0, 0.001)
                    let path = UIBezierPath()
                    for (index,value) in values.enumerated() {
                        let point = CGPoint(x: 40 + Double(index) * 515 / Double(values.count-1), y: Double(y) + 100 - value/peak * 95)
                        if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                    }
                    UIColor(red: 0.25,green: 0.42,blue: 0.35,alpha: 1).setStroke();path.lineWidth = 2;path.stroke()
                    y += 115;paragraph(l("Start of day")+" — "+l("End of day"))
                }
                page();paragraph(l("%s to %s | %s",from.formatted(date: .abbreviated,time: .omitted),to.formatted(date: .abbreviated,time: .omitted),TimeZone.current.identifier))
                paragraph(l("Generated %s | Model: relative-heuristic-v1",Date().formatted(date: .abbreviated,time: .omitted)))
                paragraph(l("Rough relative estimates, not measured blood levels or dosing guidance."));paragraph(l("Curves are scaled separately. They cannot compare medications or days."))
                if summary, let model = try? document.summary(from: from,to: to) {
                    paragraph(l("Summary with details"));model.lines.forEach(paragraph)
                    if !questions.isEmpty { paragraph(l("Questions for my appointment"));paragraph(questions) }
                }
                var current = Calendar.current.startOfDay(for: from)
                while current <= to {
                    let day = entries.filter { Calendar.current.isDate(date($0.timestamp),inSameDayAs: current) }
                    paragraph(current.formatted(date: .complete,time: .omitted))
                    if day.isEmpty { paragraph(l("No doses logged. This does not confirm that no medication was taken.")) }
                    var shown = Set<String>()
                    for sample in day {
                        let key = "\(sample.medicationId):\(sample.modelId ?? "none")"
                        guard shown.insert(key).inserted else { continue }
                        paragraph(sample.medicationName)
                        if sample.modelId == nil { paragraph(l("Estimate unavailable. Logged doses are shown below.")) }
                        else {
                            let until = Calendar.current.date(byAdding: .day,value: 1,to: current)!
                            let values = (try? decoded(NativeBridge.shared.reportCurve(document: encoded(document), entryId: sample.id, start: millis(current), end: millis(until)), as: [Double].self)) ?? []
                            chart(values)
                        }
                    }
                    var details: [(Int64, [String])] = []
                    for entry in day {
                        var lines = ["\(date(entry.timestamp).formatted(date: .omitted,time: .shortened)) · \(entry.medicationName) · \(number(entry.doseMg)) \(l(entry.unit)) · \(entry.strength)"]
                        if let mood = entry.mood { lines.append(l(["Very low","Low","Okay","Good","Very good"][mood])) }
                        if !entry.notes.isEmpty { lines.append(l("Note: %s",entry.notes)) };details.append((entry.timestamp,lines))
                    }
                    let a = millis(current),b = millis(Calendar.current.date(byAdding: .day,value: 1,to: current)!)
                    for o in document.observations.filter({ $0.timestamp >= a && $0.timestamp < b }).sorted(by: { $0.timestamp < $1.timestamp }) {
                        let line = date(o.timestamp).formatted(date: .omitted,time: .shortened)+" · "+l(categoryName(o.category))+" · "+l(responseName(o.response))+(o.value.map { " \($0) / 4" } ?? "")+(o.sleepDate.map { " · \($0)" } ?? "")
                        details.append((o.timestamp,[line]+(o.notes.isEmpty ? [] : [o.notes])))
                    }
                    for n in document.nonUse where n.start < b && n.end >= a {
                        let line = l("Not taken")+" · "+(document.medications.first { $0.id == n.medicationId }?.name ?? "")+" · "+date(n.start).formatted()+" – "+date(n.end).formatted()
                        details.append((n.start,[line]+(n.notes.isEmpty ? [] : [n.notes])))
                    }
                    for m in document.measurements where m.timestamp >= a && m.timestamp < b { details.append((m.timestamp,[date(m.timestamp).formatted(date: .omitted,time: .shortened)+" · "+measurementText(m)]+(m.notes.isEmpty ? [] : [m.notes]))) }
                    details.sorted { $0.0 < $1.0 }.forEach { $0.1.forEach(paragraph) }
                    current = Calendar.current.date(byAdding: .day,value: 1,to: current)!
                }
            }
        }
        return file
    }
}
