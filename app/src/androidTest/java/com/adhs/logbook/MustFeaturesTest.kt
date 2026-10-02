package com.adhs.logbook

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adhs.logbook.shared.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MustFeaturesTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun database(test: (Store)->Unit) { val name="must-${UUID.randomUUID()}.db";try { Store(context,name).use(test) } finally { context.deleteDatabase(name) } }
    @Test fun versionOneUpgradePreservesHistoricalPresetAndIds() {
        val name="migration-${UUID.randomUUID()}.db"
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { db ->
                db.execSQL("CREATE TABLE medication(id INTEGER PRIMARY KEY AUTOINCREMENT,preset TEXT NOT NULL,dose REAL NOT NULL,active INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE entry(id INTEGER PRIMARY KEY AUTOINCREMENT,medication_id INTEGER NOT NULL,preset TEXT NOT NULL,dose REAL NOT NULL,timestamp INTEGER NOT NULL,zone TEXT NOT NULL,offset TEXT NOT NULL,mood INTEGER,notes TEXT NOT NULL)")
                db.execSQL("CREATE TABLE reminder(id INTEGER PRIMARY KEY AUTOINCREMENT,hour INTEGER NOT NULL,minute INTEGER NOT NULL,UNIQUE(hour,minute))")
                db.execSQL("CREATE TABLE preference(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                db.execSQL("INSERT INTO medication VALUES(7,'ATOMOXETINE',40,1)")
                db.execSQL("INSERT INTO entry VALUES(19,7,'METHYLPHENIDATE_IR',10,1000,'UTC','Z',NULL,'old record')")
                db.execSQL("INSERT INTO reminder VALUES(2,8,0)");db.version=1
            }
            Store(context,name).use { store ->
                val state=store.snapshot();assertEquals(19L,state.entries.single().id)
                assertEquals(Preset.METHYLPHENIDATE_IR,state.entries.single().preset)
                assertNull(state.reminders.single().medicationId)
                store.saveMedication(state.medications.single().copy(name="Renamed",usualDose=20.0))
                assertEquals("Methylphenidate IR",store.snapshot().entries.single().medicationName)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun actionTombstonesAndIndependentActions() = database { store ->
        store.saveMedication(Medication(0,Preset.CUSTOM,2.0,name="Custom Ä",unit="ml",formulation="solution",strength="1 mg/ml"))
        val med=store.snapshot().medications.single();val command=LogDose(store,LogClock { 1000L })
        val first=command.now(med,"repeat","UTC","Z")
        assertEquals(first,command.now(med,"repeat","UTC","Z"));assertEquals(1,store.snapshot().entries.size)
        val second=command.now(med,"deliberate","UTC","Z");assertNotEquals(first,second)
        store.deleteEntry(first);command.now(med,"repeat","UTC","Z")
        assertEquals(listOf(second),store.snapshot().entries.map { it.id })
        store.saveMedication(med.copy(name="New name",unit="mg"));assertEquals("ml",store.snapshot().entries.single().unit)
    }
    @Test fun encryptedRoundTripTamperingAndAtomicRestore() = database { store ->
        store.saveMedication(Medication(0,Preset.CUSTOM,2.5,name="Ä Unicode",formulation="liquid",unit="ml"))
        val med=store.snapshot().medications.single()
        LogDose(store,LogClock { 1000 }).now(med,"one","UTC","Z")
        val doc=store.backup();val encrypted=BackupCrypto.encrypt(doc,"test passphrase ä 😀".toCharArray())
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("Unicode"))
        assertEquals(doc,BackupCrypto.decrypt(encrypted,"test passphrase ä 😀".toCharArray()))
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(encrypted,"wrong password".toCharArray()) }
        val corrupted=encrypted.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(corrupted,"test passphrase ä 😀".toCharArray()) }
        val malformed=doc.copy(entries=doc.entries.map { it.copy(medicationId=999) })
        assertThrows(Exception::class.java) { store.restore(malformed) };assertEquals(doc.entries,store.snapshot().entries)
        store.restore(doc);assertFalse(store.snapshot().remindersEnabled)
        assertEquals(doc.entries,store.snapshot().entries);assertNotNull(store.pref("recovery"))
        // Fixture for the native client's interoperability test, using synthetic data only.
        val out=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it) }
        out?.mkdirs();out?.resolve("portable-backup.adhsbak")?.writeBytes(encrypted)
    }
    @Test fun decryptsNativeIosBackup() {
        val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("ios-backup.adhsbak").use { it.readBytes() }
        val doc=BackupCrypto.decrypt(fixture,"test passphrase ä 😀".toCharArray())
        assertEquals("Ä Unicode",doc.medications.single().name)
        assertEquals(2.5,doc.entries.single().doseMg,0.0)
        assertEquals("ml",doc.entries.single().unit)
    }
    @Test fun occurrenceActionsExpireSnoozeAndDoNotRepeatFollowups() = database { store ->
        val now=ZonedDateTime.now();val stamp=now.toInstant().toEpochMilli()
        store.saveMedication(null,Preset.METHYLPHENIDATE_IR,10.0)
        val med=store.snapshot().medications.single()
        store.saveReminder(Reminder(0,8,0,med.id,true,120));store.setPref("reminders","true")
        val r=store.snapshot().reminders.single()
        val o=Occurrence(UUID.randomUUID().toString(),r.id,stamp,stamp+120*60000,r.revision,med.revision)
        store.putOccurrence(o)
        try {
            ReminderScheduler.handle(context,store,o.id,"DELIVER",now)
            val first=store.occurrences().first { it.id==o.id };assertEquals(stamp+30*60000,first.nextAlert)
            ReminderScheduler.handle(context,store,o.id,"DELIVER",now.plusMinutes(30))
            assertEquals(o.expires,store.occurrences().first { it.id==o.id }.nextAlert)
            ReminderScheduler.handle(context,store,o.id,"LOG",now.plusMinutes(31))
            ReminderScheduler.handle(context,store,o.id,"LOG",now.plusMinutes(31))
            assertEquals(1,store.snapshot().entries.size)
            ReminderScheduler.handle(context,store,o.id,"UNDO",now.plusMinutes(32))
            ReminderScheduler.handle(context,store,o.id,"LOG",now.plusMinutes(33))
            assertTrue(store.snapshot().entries.isEmpty())
            val expired=o.copy(id=UUID.randomUUID().toString(),expires=stamp)
            store.putOccurrence(expired);ReminderScheduler.handle(context,store,expired.id,"LOG",now)
            assertTrue(store.snapshot().entries.isEmpty())
        } finally { ReminderScheduler.cancelAll(context,store) }
    }
}
