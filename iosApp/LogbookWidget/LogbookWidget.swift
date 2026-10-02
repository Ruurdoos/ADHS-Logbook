import SwiftUI
import WidgetKit

struct WidgetEntry: TimelineEntry { let date: Date;let value: WidgetSnapshot }
struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> WidgetEntry { WidgetEntry(date: .now,value: WidgetSnapshot()) }
    func getSnapshot(in context: Context,completion: @escaping (WidgetEntry)->Void) { completion(WidgetEntry(date: .now,value: WidgetFiles.snapshot())) }
    func getTimeline(in context: Context,completion: @escaping (Timeline<WidgetEntry>)->Void) { completion(Timeline(entries: [WidgetEntry(date: .now,value: WidgetFiles.snapshot())],policy: .after(Date().addingTimeInterval(1800)))) }
}
func wt(_ english: String,_ german: String) -> String { Locale.current.language.languageCode?.identifier == "de" ? german : english }
struct WidgetBody: View {
    @Environment(\.widgetFamily) var family
    let entry: WidgetEntry
    var body: some View {
        if family == .accessoryCircular || family == .accessoryRectangular {
            Link(destination: URL(string: "adhslogbook://review/generic")!) {
                if family == .accessoryCircular { Image(systemName: "plus.circle").accessibilityLabel(wt("Log dose","Dosis erfassen")) }
                else { VStack(alignment: .leading) { Text(wt("Open logbook","Protokoll öffnen"));if entry.value.lockEnabled == true, let last = entry.value.lockLast, !last.isEmpty { Text(last).font(.caption).privacySensitive() } } }
            }.containerBackground(.background,for: .widget)
        } else { VStack(alignment: .leading,spacing: 8) {
            Text(entry.value.generic ? wt("Medication log","Medikamentenprotokoll") : entry.value.name).font(.headline).lineLimit(2)
            if !entry.value.generic && !entry.value.last.isEmpty { Text(wt("Last logged: ","Zuletzt erfasst: ")+entry.value.last).font(.caption).lineLimit(2) }
            Button(intent: LogWidgetIntent(token: entry.value.token)) { Text(entry.value.generic ? wt("Open logbook","Protokoll öffnen") : wt("Log now · ","Jetzt erfassen · ")+entry.value.amount).fixedSize(horizontal: false,vertical: true) }
        }.containerBackground(.background,for: .widget).privacySensitive(!entry.value.generic)
            .widgetURL(URL(string: "adhslogbook://widget/\(entry.value.token)"))
        }
    }
}
@main struct LogbookWidget: Widget {
    var body: some WidgetConfiguration { StaticConfiguration(kind: "LogbookWidget",provider: Provider()) { WidgetBody(entry: $0) }.configurationDisplayName("ADHS Logbook").description(wt("Choose a medication in the app's widget settings.","Wähle ein Medikament in den Widget-Einstellungen der App.")).supportedFamilies([.systemSmall,.systemMedium,.accessoryCircular,.accessoryRectangular]) }
}
