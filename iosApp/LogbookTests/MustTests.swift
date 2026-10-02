import XCTest
import PDFKit
@testable import Logbook

final class MustTests: XCTestCase {
    let password = "test passphrase ä 😀"
    func fixture() throws -> Document {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "android-backup", withExtension: "adhsbak"))
        return try BackupCipher.decrypt(Data(contentsOf: url), password: password)
    }
    func testAndroidBackupAndAuthenticatedRoundTrip() throws {
        let document = try fixture()
        XCTAssertEqual(document.medications.first?.name, "Ä Unicode")
        XCTAssertEqual(document.entries.first?.unit, "ml")
        let encrypted = try BackupCipher.encrypt(document, password: password)
        XCTAssertEqual(try BackupCipher.decrypt(encrypted, password: password).entries.first?.doseMg, 2.5)
        XCTAssertThrowsError(try BackupCipher.decrypt(encrypted, password: "wrong password"))
        var corrupt = encrypted;corrupt[corrupt.count-1] ^= 1
        XCTAssertThrowsError(try BackupCipher.decrypt(corrupt, password: password))
        XCTAssertThrowsError(try BackupCipher.decrypt(encrypted.prefix(40), password: password))
        // Synthetic reverse-direction fixture for Android's decrypt test.
        try encrypted.write(to: FileManager.default.temporaryDirectory.appendingPathComponent("ios-backup.adhsbak"))
    }
    @MainActor func testPersistenceDedupeSnapshotAndAtomicRestore() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory, notifications: false);await store.ready()
        var med = try XCTUnwrap(fixture().medications.first);med.id = 0
        try await store.save(med);med = try XCTUnwrap(store.state.document.medications.first)
        try await store.quick(med, action: "same");try await store.quick(med, action: "same")
        XCTAssertEqual(store.state.document.entries.count, 1)
        let first = try XCTUnwrap(store.state.document.entries.first?.id)
        try await store.quick(med, action: "deliberate");XCTAssertEqual(store.state.document.entries.count, 2)
        try await store.delete(first);try await store.quick(med, action: "same");XCTAssertEqual(store.state.document.entries.count, 1)
        med.name = "Changed";med.unit = "mg";try await store.save(med)
        XCTAssertEqual(store.state.document.entries.first?.unit, "ml")
        XCTAssertEqual(store.state.document.entries.first?.medicationName, "Ä Unicode")
        let reopened = LogbookStore(directory: directory, notifications: false);await reopened.ready()
        XCTAssertNil(reopened.error);XCTAssertEqual(reopened.state.document.entries.count, 1)
        var invalid = reopened.state.document;invalid.entries[0].medicationId = 999
        do { try await reopened.restore(invalid);XCTFail("Expected validation failure") } catch { };XCTAssertEqual(reopened.state.document.entries.count, 1)
        try await reopened.restore(fixture());XCTAssertFalse(reopened.state.remindersEnabled)
        XCTAssertEqual(reopened.state.recovery?.entries.count, 1)
    }
    func testValidationFormattingAndLongReport() throws {
        var doc = try fixture();doc.version = 900
        XCTAssertThrowsError(try doc.validated())
        XCTAssertEqual(l("100% custom"), "100% custom")
        XCTAssertTrue(l("Log now · %s %s", "2,5", "ml").contains("2,5 ml"))
        doc = try fixture();doc.entries[0].notes = String(repeating: "Long note ä test. ", count: 280)
        let day = date(doc.entries[0].timestamp)
        let url = try NativeReports.create(document: doc, entries: doc.entries, from: day, to: day, pdf: true)
        defer { try? FileManager.default.removeItem(at: url) }
        XCTAssertGreaterThan(try XCTUnwrap(PDFDocument(url: url)).pageCount, 1)
    }
    @MainActor func testShouldLedgerRecordsAndRestore() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        var med = try XCTUnwrap(fixture().medications.first);med.id = 0
        try await store.save(med);med = try XCTUnwrap(store.state.document.medications.first)
        let supply = SupplyValue(medicationId: med.id,unitLabel: "unit",countedAt: 100,lowThreshold: 2,dosePerUnit: 2.5,doseUnit: "ml")
        try await store.saveSupply(supply,count: 10,action: "count")
        try await store.quick(med,action: "one");try await store.quick(med,action: "one")
        XCTAssertEqual(try store.state.document.balance(med.id).remaining,9)
        var entry = try XCTUnwrap(store.state.document.entries.first);entry.supplyUnits = 0.5
        try await store.save(entry,action: "edit");XCTAssertEqual(try store.state.document.balance(med.id).remaining,9.5)
        let observation = ObservationValue(category: "focus",response: "none",timestamp: 100,createdAt: 101,zoneId: "UTC",offset: "Z",doseId: entry.id)
        try await store.saveObservation(observation)
        try await store.delete(entry.id);XCTAssertNil(store.state.document.observations.first?.doseId)
        XCTAssertEqual(try store.state.document.balance(med.id).remaining,10)
        try await store.restock(med.id,units: 2.5,action: "restock");try await store.restock(med.id,units: 2.5,action: "restock")
        XCTAssertEqual(try store.state.document.balance(med.id).remaining,12.5)
        try await store.saveNonUse(NonUseValue(medicationId: med.id,start: 200,end: 300,createdAt: 301,zoneId: "UTC",offset: "Z"))
        try await store.pause(PauseValue(paused: true,until: 1000))
        let encrypted = try BackupCipher.encrypt(store.state.document,password: "should passphrase")
        let restored = try BackupCipher.decrypt(encrypted,password: "should passphrase")
        try await store.restore(restored);XCTAssertFalse(store.state.supplyNotificationsEnabled ?? true)
        XCTAssertEqual(store.state.document.observations.first?.response,"none")
        XCTAssertEqual(store.state.document.nonUse.count,1);XCTAssertTrue(store.state.document.entries.isEmpty)
        let reopened = LogbookStore(directory: directory,notifications: false);await reopened.ready()
        XCTAssertNil(reopened.error);XCTAssertEqual(try reopened.state.document.balance(med.id).remaining,12.5)
        try encrypted.write(to: FileManager.default.temporaryDirectory.appendingPathComponent("should-ios.adhsbak"))
    }
    @MainActor func testShouldConflictsFailWithoutPartialWrite() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        var med = try XCTUnwrap(fixture().medications.first);med.id = 0;try await store.save(med)
        med = try XCTUnwrap(store.state.document.medications.first)
        try await store.quick(med,action: "dose");let entry = try XCTUnwrap(store.state.document.entries.first)
        do { try await store.saveNonUse(NonUseValue(medicationId: med.id,start: entry.timestamp,end: entry.timestamp,createdAt: millis(),zoneId: "UTC",offset: "Z"));XCTFail("Expected validation failure") } catch { }
        XCTAssertTrue(store.state.document.nonUse.isEmpty);XCTAssertEqual(store.state.document.entries.count,1)
        do { try await store.saveObservation(ObservationValue(category: "focus",response: "rated",timestamp: 100,createdAt: 100,zoneId: "UTC",offset: "Z"));XCTFail("Expected validation failure") } catch { }
        XCTAssertTrue(store.state.document.observations.isEmpty)
    }
    func testShouldSummaryTypedCsvAndEmptyPeriod() throws {
        var doc = try fixture();doc.version = 2
        let timestamp = doc.entries[0].timestamp
        doc.observations = [ObservationValue(category: "focus",response: "none",timestamp: timestamp,createdAt: timestamp,zoneId: "UTC",offset: "Z")]
        let day = date(timestamp),summary = try doc.summary(from: day,to: day)
        XCTAssertEqual(summary.doses,1);XCTAssertEqual(summary.observations.first?.none,1);XCTAssertEqual(summary.daysWithoutRecords,0)
        let csv = try NativeReports.create(document: doc,entries: doc.entries,from: day,to: day,pdf: false)
        let content = try String(contentsOf: csv,encoding: .utf8)
        XCTAssertTrue(content.contains("schema_version"));XCTAssertTrue(content.contains("observation"))
        let pdf = try NativeReports.create(document: doc,entries: doc.entries,from: day,to: day,pdf: true,summary: true,questions: String(repeating: "Frage für meinen nächsten Termin. ",count: 100))
        let document = try XCTUnwrap(PDFDocument(url: pdf));XCTAssertGreaterThan(document.pageCount,1)
        XCTAssertTrue(document.string?.contains("Summary with details") == true)
        try Data(contentsOf: pdf).write(to: FileManager.default.temporaryDirectory.appendingPathComponent("should-summary.pdf"))
        let empty = Document();XCTAssertEqual(try empty.summary(from: day,to: day).daysWithoutRecords,1)
    }

    @MainActor func testWidgetAppGroupStaleTokenAndLockedAction() async throws {
        XCTAssertNotNil(WidgetFiles.directory,"Run simulator tests with ad-hoc signing to include App Group entitlements")
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        var med = try XCTUnwrap(fixture().medications.first);med.id = 0;try await store.save(med)
        med = try XCTUnwrap(store.state.document.medications.first)
        let privacy = PrivacyController.shared, previous = privacy.enabled, session = privacy.unlocked
        defer { privacy.enabled = previous;privacy.unlocked = session;for key in ["widget.med","widget.revision","widget.generic","widget.token"] { UserDefaults.standard.removeObject(forKey: key) };try? WidgetFiles.publish(WidgetSnapshot()) }
        privacy.enabled = false
        try store.configureWidget(med,generic: false)
        let token = WidgetFiles.snapshot().token
        try WidgetFiles.write(Data(token.utf8),name: "pending.txt");store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.state.document.entries.count,1)
        try WidgetFiles.write(Data(token.utf8),name: "pending.txt");store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.state.document.entries.count,1)
        try store.configureWidget(med,generic: false);let current = WidgetFiles.snapshot().token
        privacy.enabled = true;privacy.unlocked = false
        try WidgetFiles.write(Data(current.utf8),name: "pending.txt");store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.state.document.entries.count,1)
        privacy.unlocked = true;store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.state.document.entries.count,1);XCTAssertEqual(store.widgetReview,med.id)
        privacy.enabled = false
        try store.configureWidget(med,generic: false);let stale = WidgetFiles.snapshot().token
        med.usualDose = 9;try await store.save(med)
        try WidgetFiles.write(Data(stale.utf8),name: "pending.txt");store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.state.document.entries.count,1);XCTAssertEqual(store.widgetReview,med.id)
    }

    func testReadsAndroidSchemaTwoBackup() throws {
        let file = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "should-android",withExtension: "adhsbak"))
        let doc = try BackupCipher.decrypt(Data(contentsOf: file),password: "should passphrase")
        XCTAssertEqual(doc.version,2);XCTAssertEqual(doc.observations.first?.response,"unsure")
        XCTAssertEqual(doc.observations.first?.sleepDate,"2026-09-29");XCTAssertEqual(doc.nonUse.count,1)
        XCTAssertTrue(doc.pause.paused);XCTAssertEqual(try doc.balance(doc.supplies[0].medicationId).remaining,10)
    }

    @MainActor func testCouldMeasurementsPersistBackupAndExport() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        let original = MeasurementValue(kind: "weight",value: 150.5,unit: "lb",timestamp: 1720000000000,createdAt: 1720000060000,zoneId: "UTC",offset: "Z",notes: "PRIVATE measurement")
        try await store.saveMeasurement(original)
        XCTAssertTrue(store.state.document.entries.isEmpty);XCTAssertTrue(store.state.document.observations.isEmpty)
        var invalid = original;invalid.unit = "bpm"
        do { try await store.saveMeasurement(invalid);XCTFail("Expected validation failure") } catch { }
        XCTAssertEqual(store.state.document.measurements.first?.unit,"lb")
        var edited = original;edited.value = 151.25;try await store.saveMeasurement(edited)
        let reopened = LogbookStore(directory: directory,notifications: false);await reopened.ready()
        XCTAssertNil(reopened.error);XCTAssertEqual(reopened.state.document.measurements.first?.value,151.25)
        let encrypted = try BackupCipher.encrypt(store.state.document,password: "could passphrase")
        let restored = try BackupCipher.decrypt(encrypted,password: "could passphrase")
        XCTAssertEqual(restored.version,4);XCTAssertEqual(restored.measurements.first?.createdAt,original.createdAt)
        try await store.deleteMeasurement(original.id);XCTAssertTrue(store.state.document.measurements.isEmpty)
        try await store.restore(restored);XCTAssertEqual(store.state.document.measurements.first?.unit,"lb")
        let day = date(original.timestamp)
        let summary = try restored.summary(from: day,to: day)
        XCTAssertEqual(summary.measurements,1);XCTAssertEqual(summary.daysWithoutRecords,0);XCTAssertEqual(summary.doses,0)
        let week = try restored.weekly(day)
        XCTAssertEqual(week.summary.measurements,1);XCTAssertEqual(week.summary.daysWithoutRecords,6)
        var redacted = restored;redacted.measurements = restored.measurements.map { var value = $0;value.notes = "";return value }
        let csv = try NativeReports.create(document: redacted,entries: [],from: day,to: day,pdf: false)
        let content = try String(contentsOf: csv,encoding: .utf8)
        XCTAssertTrue(content.contains("151.25"));XCTAssertTrue(content.contains("created_at_iso8601"));XCTAssertFalse(content.contains("PRIVATE"))
        let pdf = try NativeReports.create(document: restored,entries: [],from: day,to: day,pdf: true,summary: true)
        XCTAssertTrue(try XCTUnwrap(PDFDocument(url: pdf)).string?.contains(number(151.25)+" lb") == true)
        try Data(contentsOf: pdf).write(to: FileManager.default.temporaryDirectory.appendingPathComponent("could-summary.pdf"))
        try encrypted.write(to: FileManager.default.temporaryDirectory.appendingPathComponent("could-ios.adhsbak"))
    }
    @MainActor func testCouldReviewRoutingLockRevisionAndUrlNeverSave() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        var med = try XCTUnwrap(fixture().medications.first);med.id = 0;try await store.save(med)
        med = try XCTUnwrap(store.state.document.medications.first)
        let settings = UserDefaults.standard,privacy = PrivacyController.shared
        let previous = privacy.enabled,session = privacy.unlocked
        defer { privacy.enabled = previous;privacy.unlocked = session;for key in ["quick.enabled","quick.configuration","quick.last","widget.med","widget.revision","widget.generic","widget.token"] { settings.removeObject(forKey: key) };try? WidgetFiles.publish(WidgetSnapshot()) }
        privacy.enabled = false
        settings.set(true,forKey: "quick.enabled")
        settings.set(try JSONEncoder().encode(QuickConfigurationValue(token: "configured",medicationId: med.id,revision: med.revision)),forKey: "quick.configuration")
        store.reviewShortcut("configured");XCTAssertEqual(store.widgetReview,med.id);store.widgetReview = nil
        store.reviewShortcut("medication=1&dose=999");XCTAssertNil(store.widgetReview)
        privacy.enabled = true;privacy.unlocked = false
        try WidgetFiles.write(Data("review:configured".utf8),name: "pending.txt");store.processWidget();await store.waitForPendingActions();XCTAssertNil(store.widgetReview)
        privacy.unlocked = true;store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.widgetReview,med.id);store.widgetReview = nil
        privacy.enabled = false
        med.usualDose = 9;try await store.save(med);store.error = nil
        store.reviewShortcut("configured");XCTAssertNotNil(store.error);XCTAssertEqual(store.widgetReview,med.id)
        try store.configureWidget(med,generic: false);store.widgetReview = nil
        try WidgetFiles.write(Data(("widget-review:"+WidgetFiles.snapshot().token).utf8),name: "pending.txt")
        store.processWidget();await store.waitForPendingActions();XCTAssertEqual(store.widgetReview,med.id);XCTAssertTrue(store.state.document.entries.isEmpty)
        settings.set(true,forKey: "quick.last");privacy.enabled = true;store.publishWidget();XCTAssertEqual(WidgetFiles.snapshot().lockLast,"")
        privacy.enabled = false;try await store.restore(store.state.document);store.widgetReview = nil
        store.reviewShortcut("configured");XCTAssertNil(store.widgetReview);XCTAssertFalse(settings.bool(forKey: "quick.enabled"))
    }

    func testReadsAndroidSchemaThreeMeasurements() throws {
        let file = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "could-android",withExtension: "adhsbak"))
        let doc = try BackupCipher.decrypt(Data(contentsOf: file),password: "could passphrase")
        XCTAssertEqual(doc.version,3);XCTAssertEqual(doc.measurements.first?.value,151.25)
        XCTAssertEqual(doc.measurements.first?.unit,"lb");XCTAssertEqual(doc.measurements.first?.createdAt,1720000060000)
    }

    @MainActor func testCouldAppIntentQueuesReviewAndValidatesEntityTokens() async throws {
        let settings = UserDefaults.standard,privacy = PrivacyController.shared
        let previous = privacy.enabled,session = privacy.unlocked
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { privacy.enabled = previous;privacy.unlocked = session;for key in ["quick.enabled","quick.configuration"] { settings.removeObject(forKey: key) };_ = WidgetFiles.takePending();try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        var med = try XCTUnwrap(fixture().medications.first);med.id = 0;try await store.save(med)
        med = try XCTUnwrap(store.state.document.medications.first)
        settings.set(false,forKey: "quick.enabled")
        let absent = try await ReviewQuery().suggestedEntities();XCTAssertTrue(absent.isEmpty)
        settings.set(true,forKey: "quick.enabled")
        settings.set(try JSONEncoder().encode(QuickConfigurationValue(token: "intent-token",medicationId: med.id,revision: med.revision)),forKey: "quick.configuration")
        let entities = try await ReviewQuery().entities(for: ["intent-token","dose=999"])
        XCTAssertEqual(entities.map(\.id),["intent-token"])
        privacy.enabled = true;privacy.unlocked = false
        let intent = OpenDoseEditorIntent();_ = try await intent.perform()
        store.processWidget();await store.waitForPendingActions();XCTAssertNil(store.widgetReview);XCTAssertTrue(store.state.document.entries.isEmpty)
        privacy.unlocked = true;store.processWidget()
        XCTAssertEqual(store.widgetReview,med.id);XCTAssertTrue(store.state.document.entries.isEmpty)
    }

    @MainActor func testEditingDoesNotOfferCreationUndoAndKeepsOtherEntries() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        try await store.save(Med(id: 0,preset: "CUSTOM",usualDose: 2,name: "Example",formulation: "",modelId: nil))
        let med = try XCTUnwrap(store.state.document.medications.first)
        try await store.quick(med,action: "first")
        XCTAssertNotNil(store.undoID)
        var edited = try XCTUnwrap(store.state.document.entries.first);edited.notes = "Corrected"
        try await store.save(edited,action: "edit")
        XCTAssertNil(store.undoID)
        XCTAssertEqual(store.state.document.entries.count,1)
        XCTAssertEqual(store.state.document.entries.first?.notes,"Corrected")
    }
    @MainActor func testQueuedPreferencesAreAllPersisted() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        store.attempt { try await store.change { $0.document.preferences["observations_enabled"] = "true" } }
        store.attempt { try await store.change { $0.document.preferences["measurements_enabled"] = "true" } }
        await store.waitForPendingActions()
        let reopened = LogbookStore(directory: directory,notifications: false);await reopened.ready()
        XCTAssertEqual(reopened.state.document.preferences["observations_enabled"],"true")
        XCTAssertEqual(reopened.state.document.preferences["measurements_enabled"],"true")
    }
    @MainActor func testRecoveryFromUnreadableFilePreservesOriginalBytes() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory,withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let raw = Data("damaged json".utf8)
        try raw.write(to: directory.appendingPathComponent("logbook.json"))
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        XCTAssertFalse(store.readable)
        try await store.restore(Document())
        XCTAssertTrue(store.readable)
        let preserved = try XCTUnwrap(FileManager.default.contentsOfDirectory(at: directory,includingPropertiesForKeys: nil).first { $0.lastPathComponent.hasPrefix("unreadable-") })
        XCTAssertEqual(try Data(contentsOf: preserved),raw)
    }
    @MainActor func testConcurrentWritesHaveUniqueIdentities() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        try await store.save(Med(id: 0,preset: "CUSTOM",usualDose: 1,name: "Example",formulation: "",modelId: nil))
        let med = try XCTUnwrap(store.state.document.medications.first)
        async let first: Void = store.quick(med,action: "one")
        async let second: Void = store.quick(med,action: "two")
        _ = try await (first,second)
        XCTAssertEqual(Set(store.state.document.entries.map(\.id)).count,2)
    }
    @MainActor func testLargeLogPersistenceAndSummaryBenchmark() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = LogbookStore(directory: directory,notifications: false);await store.ready()
        var document = try fixture();document.version = 4
        let sample = try XCTUnwrap(document.entries.first)
        for size in [1000,10000] {
            document.entries = (1...size).map { var e = sample;e.id = Int64($0);e.timestamp = sample.timestamp+Int64($0)*60000;return e }
            let started = Date()
            try await store.restore(document)
            let duration = Date().timeIntervalSince(started)
            XCTAssertEqual(store.state.document.entries.count,size)
            let captured = store.state.document,from = date(sample.timestamp),to = date(sample.timestamp+Int64(size)*60000)
            let summaryStarted = Date()
            let result = try await Task.detached { try captured.summary(from: from,to: to) }.value
            XCTAssertEqual(result.doses,size)
            print("QA_BENCH iOS records=\(size) restore_ms=\(Int(duration*1000)) summary_ms=\(Int(Date().timeIntervalSince(summaryStarted)*1000))")
        }
    }
}
