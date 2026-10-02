package com.adhs.logbook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adhs.logbook.shared.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CouldFeaturesTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun database(test: (Store,String)->Unit) { val name="could-${UUID.randomUUID()}.db";try { Store(context,name).use { test(it,name) } } finally { context.deleteDatabase(name) } }
    private fun sample()=Measurement("measurement","weight",150.5,"lb",timestamp=1720000000000,createdAt=1720000060000,zoneId="UTC",offset="Z",notes="PRIVATE measurement")
    @Test fun measurementsPersistEditDeleteAndRollbackInvalidRestore() = database { store,name ->
        val original=sample();store.saveMeasurement(original)
        Store(context,name).use { assertEquals(original,it.snapshot().measurements.single()) }
        assertTrue(store.snapshot().entries.isEmpty());assertTrue(store.snapshot().observations.isEmpty())
        assertThrows(Exception::class.java) { store.saveMeasurement(original.copy(unit="bpm")) }
        assertEquals(original,store.snapshot().measurements.single())
        store.saveMeasurement(original.copy(value=151.25));assertEquals(151.25,store.snapshot().measurements.single().value,0.0)
        val backup=BackupCrypto.decrypt(BackupCrypto.encrypt(store.backup(),"could passphrase".toCharArray()),"could passphrase".toCharArray())
        assertEquals(3,backup.version)
        assertThrows(Exception::class.java) { store.restore(backup.copy(measurements=listOf(original.copy(value=Double.POSITIVE_INFINITY)))) }
        assertEquals(151.25,store.snapshot().measurements.single().value,0.0)
        store.deleteMeasurement(original.id);assertTrue(store.snapshot().measurements.isEmpty());store.restore(backup)
        assertEquals("lb",store.snapshot().measurements.single().unit)
        File(context.filesDir,"could-android.adhsbak").writeBytes(BackupCrypto.encrypt(store.backup(),"could passphrase".toCharArray()))
    }
    @Test fun schemaThreeMigrationRetainsPreviousRecords() = database { store,name ->
        store.saveMedication(null,Preset.CUSTOM,2.0)
        store.saveObservation(Observation("o","focus","none",timestamp=100,createdAt=101,zoneId="UTC",offset="Z"))
        store.writableDatabase.execSQL("DROP TABLE measurement");store.writableDatabase.version=3;store.close()
        Store(context,name).use { migrated ->
            assertEquals(1,migrated.snapshot().medications.size);assertEquals("none",migrated.snapshot().observations.single().response)
            assertTrue(migrated.snapshot().measurements.isEmpty());migrated.saveMeasurement(sample());assertEquals(4,migrated.readableDatabase.version)
        }
    }
    @Test fun measurementReportAndWeeklyCoveragePreserveOriginalFields() = database { store,_ ->
        val weight=sample();val pressure=weight.copy(id="pressure",kind="pressure",value=120.0,diastolic=80.0,unit="mmHg")
        store.saveMeasurement(weight);store.saveMeasurement(pressure)
        val doc=store.backup();val zone=ZoneId.of("UTC");val day=Instant.ofEpochMilli(weight.timestamp).atZone(zone).toLocalDate()
        val summary=SummaryBuilder.build(doc,listOf(day.atStartOfDay(zone).toInstant().toEpochMilli(),day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()))
        assertEquals(2,summary.measurements);assertEquals(0,summary.doses);assertEquals(0,summary.daysWithoutRecords)
        val csv=Reports.createDocument(context,doc,day,day,ReportFormat.CSV,true,false,"",zone).readText()
        assertTrue(csv.contains("created_at_iso8601"));assertTrue(csv.contains("150.5"));assertTrue(csv.contains("mmHg"));assertFalse(csv.contains("PRIVATE"))
        val pdf=Reports.createDocument(context,doc,day,day,ReportFormat.PDF,true,true,"",zone)
        File(context.filesDir,"could-summary.pdf").writeBytes(pdf.readBytes())
        assertTrue(pdf.length()>1000)
    }
    @Test fun shortcutsReconcileOptInAndNeverWriteRecords() = database { store,_ ->
        store.saveMedication(null,Preset.CUSTOM,2.0);val med=store.snapshot().medications.single()
        val prefs=context.getSharedPreferences("quick_access",0)
        try {
            QuickAccess.enable(context,true);QuickAccess.configure(context,med)
            val token=QuickAccess.configuration(context)!!.token
            assertEquals(med.id,QuickAccess.route(context,token,listOf(med)).medicationId)
            assertTrue(QuickAccess.route(context,token,listOf(med.copy(revision=med.revision+1))).changed)
            assertTrue(QuickAccess.route(context,token,listOf(med.copy(active=false))).unavailable)
            assertTrue(QuickAccess.route(context,"dose=999",listOf(med)).unavailable)
            val shortcuts=context.getSystemService(android.content.pm.ShortcutManager::class.java).dynamicShortcuts
            assertTrue(shortcuts.any { it.id=="review-generic" && it.intent?.getStringExtra("review_token")=="generic" })
            QuickAccess.enable(context,false);assertTrue(QuickAccess.route(context,token,listOf(med)).unavailable)
            QuickAccess.reset(context);assertNull(QuickAccess.configuration(context));assertTrue(store.snapshot().entries.isEmpty())
        } finally { prefs.edit().clear().commit();QuickAccess.reconcile(context) }
    }
    @Test fun readsIosSchemaThreeMeasurements() {
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("could-ios.adhsbak").use { it.readBytes() }
        val doc=BackupCrypto.decrypt(bytes,"could passphrase".toCharArray())
        assertEquals(3,doc.version);assertEquals(151.25,doc.measurements.single().value,0.0)
        assertEquals("lb",doc.measurements.single().unit);assertEquals(1720000060000L,doc.measurements.single().createdAt)
    }

}
