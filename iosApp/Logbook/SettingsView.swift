import SwiftUI
import UniformTypeIdentifiers

struct ReminderForm: View {
    @EnvironmentObject var store: LogbookStore
    @Environment(\.dismiss) var dismiss
    @State var value: ReminderValue
    @State var time = Date()
    var body: some View {
        NavigationStack {
            Form {
                DatePicker(l("Time"),selection: $time,displayedComponents: .hourAndMinute)
                Picker(l("Medication"),selection: Binding(get: { value.medicationId ?? 0 },set: { value.medicationId = $0 == 0 ? nil : $0 })) {
                    Text(l("Generic reminder")).tag(Int64(0));ForEach(store.state.document.medications.filter(\.active)) { Text($0.name).tag($0.id) }
                }
                Text(l("Choose a medication to enable log-now. Otherwise this reminder opens your log.")).font(.footnote)
                Toggle(l("One follow-up after 30 minutes"),isOn: $value.followUp)
                Picker(l("Stop this reminder after"),selection: $value.cutoffMinutes) { ForEach([60,120,240],id: \.self) { Text(l("%d minutes",$0)).tag($0) } }
                Text(l("Snooze adds 10 minutes, up to the cutoff. Reminder text hides medication names.")).font(.footnote)
            }.navigationTitle(l("Reminder options"))
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(l("Cancel")) { dismiss() } };ToolbarItem(placement: .confirmationAction) { Button(l("Save")) {
                    value.hour = Calendar.current.component(.hour,from: time);value.minute = Calendar.current.component(.minute,from: time)
                    store.attempt { try store.saveReminder(value);dismiss() }
                } } }
        }.onAppear { time = Calendar.current.date(bySettingHour: value.hour,minute: value.minute,second: 0,of: Date())! }
    }
}
struct BinaryDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { guard let data = configuration.file.regularFileContents else { throw AppError.invalid };self.data = data }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
struct BackupView: View {
    @EnvironmentObject var store: LogbookStore
    @State var password = ""
    @State var repeatPassword = ""
    @State var working = false
    @State var importing = false
    @State var exporting = false
    @State var exportData = BinaryDocument(data: Data())
    @State var preview: Document?
    @State var showConfirm = false
    @State var message: String?
    var body: some View {
        Form {
            Text(l("Encrypted backup. Use at least 10 characters. Keep the passphrase separately; it cannot be recovered."))
            Text(l("You choose the file destination. Cloud-backed file providers are outside this app's local storage.")).font(.footnote)
            SecureField(l("Passphrase"),text: $password).textContentType(.password)
            SecureField(l("Repeat passphrase"),text: $repeatPassword).textContentType(.password)
            if let last = store.state.lastBackup { Text(l("Last backup: %s",last.formatted(date: .abbreviated,time: .shortened))) }
            Button(l("Create backup")) {
                let secret = password, doc = store.state.document;password = "";repeatPassword = "";working = true
                Task { do { let data = try await Task.detached { try BackupCipher.encrypt(doc,password: secret) }.value;exportData = BinaryDocument(data: data);exporting = true }
                    catch { message = l("Backup failed. The selected file may be incomplete; create a new backup.") };working = false }
            }.disabled(working || password.utf16.count < 10 || password != repeatPassword)
            Button(l("Restore backup")) { importing = true }.disabled(working || password.utf16.count < 10)
            Button(l("Recover pre-restore log")) { if let doc = store.state.recovery { preview = doc;showConfirm = true } else { message = l("No pre-restore snapshot available.") } }.disabled(working)
            if working { ProgressView(l("Working…")) }
        }.navigationTitle(l("Data & privacy"))
            .fileExporter(isPresented: $exporting,document: exportData,contentType: .data,defaultFilename: "ADHS-logbook.adhsbak") { result in
                switch result { case .success: store.attempt { try store.change { $0.lastBackup = Date() } };message = l("Backup created.")
                case .failure: message = l("Backup failed. The selected file may be incomplete; create a new backup.") };exportData = BinaryDocument(data: Data())
            }
            .fileImporter(isPresented: $importing,allowedContentTypes: [.data],allowsMultipleSelection: false) { result in
                let secret = password;password = "";repeatPassword = "";working = true
                Task { do {
                    guard let url = try result.get().first else { throw AppError.invalid }
                    let doc = try await Task.detached {
                        let access = url.startAccessingSecurityScopedResource();defer { if access { url.stopAccessingSecurityScopedResource() } }
                        guard let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size <= BackupCipher.limit else { throw AppError.invalid }
                        return try BackupCipher.decrypt(Data(contentsOf: url),password: secret)
                    }.value
                    preview = doc;showConfirm = true
                } catch { message = l("Cannot open backup. Check the passphrase and file. Your log was not changed.") };working = false }
            }
            .alert(l("Replace current log?"),isPresented: $showConfirm) {
                Button(l("Replace log"),role: .destructive) { if let doc = preview { store.attempt { try store.restore(doc);message = l("Backup restored. Reminders are off.") } };preview = nil }
                Button(l("Cancel"),role: .cancel) { preview = nil }
            } message: {
                if let doc = preview {
                    Text(l("Medications: %d · Entries: %d",doc.medications.count,doc.entries.count)+"\n"+backupRange(doc)+"\n"+l("This replaces the current log. A private pre-restore snapshot is kept on this device. Reminders stay off until you enable them."))
                }
            }
            .alert(message ?? "",isPresented: Binding(get: { message != nil },set: { if !$0 { message = nil } })) { Button(l("Close")) { message = nil } }
            .onDisappear { password = "";repeatPassword = "" }
    }
    func backupRange(_ doc: Document) -> String { guard let min = doc.entries.map(\.timestamp).min(), let max = doc.entries.map(\.timestamp).max() else { return l("No entries") };return date(min).formatted(date: .abbreviated,time: .omitted)+" – "+date(max).formatted(date: .abbreviated,time: .omitted) }
}
