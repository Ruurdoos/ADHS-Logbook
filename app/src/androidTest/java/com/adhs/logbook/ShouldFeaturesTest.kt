package com.adhs.logbook

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
class ShouldFeaturesTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun database(test: (Store)->Unit) { val name="should-${UUID.randomUUID()}.db";try { Store(context,name).use(test) } finally { context.deleteDatabase(name) } }
    @Test fun stockEditsUndoRecountAndRestoreAreTransactional() = database { store ->
        store.saveMedication(null,Preset.METHYLPHENIDATE_IR,10.0)
        val med=store.snapshot().medications.single()
        store.saveSupply(Supply(med.id,"tablet",100,2.0,10.0),10.0,"count")
        val id=LogDose(store,LogClock { 200 }).now(med,"dose","UTC","Z")
        fun balance()=SupplyLedger.balance(store.backup(),store.snapshot().supplies.single()).remaining
        assertEquals(9.0,balance(),0.0)
        store.saveEntry(store.snapshot().entries.single().copy(supplyUnits=0.5));assertEquals(9.5,balance(),0.0)
        store.restock(med.id,3.0,"restock",300);store.restock(med.id,3.0,"restock",300);assertEquals(12.5,balance(),0.0)
        store.deleteEntry(id);assertEquals(13.0,balance(),0.0)
        store.saveSupply(Supply(med.id,"tablet",400,2.0,10.0),7.0,"recount")
        LogDose(store,LogClock { 200 }).now(med,"backdated","UTC","Z");assertEquals(7.0,balance(),0.0)
        val doc=store.backup();store.setPref("supply_enabled","true");store.restore(doc)
        assertEquals(7.0,balance(),0.0);assertEquals("false",store.pref("supply_enabled"))
    }
    @Test fun independentRecordsBackupPauseAndConflicts() = database { store ->
        store.saveMedication(null,Preset.CUSTOM,2.0)
        val med=store.snapshot().medications.single()
        val observation=Observation("o","sleep","unsure",timestamp=100,createdAt=101,zoneId="Europe/Berlin",offset="+02:00",sleepDate="2026-09-29",notes="private observation")
        store.saveObservation(observation)
        store.saveNonUse(NonUse("n",med.id,200,300,301,"UTC","Z"))
        store.pause(ReminderPause(true,1000));assertTrue(store.snapshot().entries.isEmpty())
        assertThrows(Exception::class.java) { LogDose(store,LogClock { 250 }).now(med,"conflict","UTC","Z") }
        assertTrue(store.snapshot().entries.isEmpty())
        val document=BackupCrypto.decrypt(BackupCrypto.encrypt(store.backup(),"should passphrase".toCharArray()),"should passphrase".toCharArray())
        store.restore(document);assertEquals(observation,store.snapshot().observations.single());assertEquals(1,store.snapshot().nonUse.size)
        store.deleteObservation("o");store.deleteNonUse("n");assertTrue(store.snapshot().nonUse.isEmpty())
        // Cross-platform fixture contains every new record type and count ledger.
        store.saveObservation(observation);store.saveNonUse(NonUse("n",med.id,200,300,301,"UTC","Z"))
        store.saveSupply(Supply(med.id,"unit",400,2.0),10.0,"count")
        File(context.filesDir,"should-android.adhsbak").writeBytes(BackupCrypto.encrypt(store.backup(),"should passphrase".toCharArray()))
    }
    @Test fun staleWidgetDuplicateAndCurrentLockPolicyCannotBypass() = database { store ->
        store.saveMedication(null,Preset.METHYLPHENIDATE_IR,10.0);val med=store.snapshot().medications.single()
        try {
            LogWidget.configure(context,med.id,med.revision,false)
            val token=context.getSharedPreferences("widget",0).getString("token","")!!
            val a=LogWidget.log(context,store,token);assertEquals(a,LogWidget.log(context,store,token));assertEquals(1,store.snapshot().entries.size)
            AppPrivacy.setEnabled(context,true);AppPrivacy.unlocked=false
            assertThrows(Exception::class.java) { LogWidget.log(context,store,token) }
            store.saveReminder(Reminder(0,8,0,med.id));store.setPref("reminders","true")
            val r=store.snapshot().reminders.single();val now=ZonedDateTime.now();val stamp=now.toInstant().toEpochMilli()
            val o=Occurrence("locked-occurrence",r.id,stamp,stamp+60000,r.revision,med.revision);store.putOccurrence(o)
            ReminderScheduler.handle(context,store,o.id,"LOG",now);assertEquals(1,store.snapshot().entries.size)
            AppPrivacy.setEnabled(context,false)
            store.saveMedication(med.copy(usualDose=20.0))
            assertThrows(Exception::class.java) { LogWidget.log(context,store,token) }
            assertEquals(1,store.snapshot().entries.size)
        } finally {
            AppPrivacy.setEnabled(context,false);context.getSharedPreferences("widget",0).edit().clear().commit();ReminderScheduler.cancelAll(context,store)
        }
    }
    @Test fun summaryCsvRedactionAndDstPdfLayout() = database { store ->
        val zone=ZoneId.of("Europe/Berlin");val day=LocalDate.of(2026,10,25)
        val start=day.atStartOfDay(zone).toInstant().toEpochMilli();val end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(25*3600000L,end-start)
        store.saveMedication(Medication(0,Preset.CUSTOM,1.0,name="Sehr langer individueller Medikamentenname ÄÖÜ für den Arztbericht",formulation="liquid",unit="ml"))
        val med=store.snapshot().medications.single()
        store.saveObservation(Observation("o","focus","none",timestamp=start+1000,createdAt=start+1000,zoneId=zone.id,offset="+02:00",notes="SECRET"))
        store.saveNonUse(NonUse("n",med.id,start+2000,start+3000,start+3000,zone.id,"+02:00",notes="SECRET"))
        val doc=store.backup();val summary=SummaryBuilder.build(doc,listOf(start,end))
        assertEquals(0,summary.daysWithoutRecords);assertEquals(0,summary.doses)
        val csv=Reports.createDocument(context,doc,day,day,ReportFormat.CSV,true,false,"",zone)
        assertFalse(csv.readText().contains("SECRET"));assertTrue(csv.readText().contains("non_use"));assertTrue(csv.readText().contains("observation"))
        val old=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMAN)
            val pdf=Reports.createDocument(context,doc,day,day,ReportFormat.PDF,true,true,"Lange Frage für meinen nächsten Termin. ".repeat(80),zone)
            File(context.filesDir,"should-summary.pdf").writeBytes(pdf.readBytes())
            PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount>1)
                renderer.openPage(0).use { page -> val bitmap=Bitmap.createBitmap(1190,1684,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);File(context.filesDir,"should-summary.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
            }
        } finally { java.util.Locale.setDefault(old) }
    }
    @Test fun readsIosSchemaTwoBackup() {
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("should-ios.adhsbak").use { it.readBytes() }
        val doc=BackupCrypto.decrypt(bytes,"should passphrase".toCharArray())
        assertEquals(2,doc.version);assertEquals("none",doc.observations.single().response)
        assertEquals(1,doc.nonUse.size);assertTrue(doc.pause.paused)
        assertEquals(12.5,SupplyLedger.balance(doc,doc.supplies.single()).remaining,0.0)
    }
    @Test fun pauseCancelsOccurrenceAndResumeSchedulesOnlyFuture() = database { store ->
        store.saveMedication(null,Preset.METHYLPHENIDATE_IR,10.0);val med=store.snapshot().medications.single()
        store.saveReminder(Reminder(0,8,0,med.id));store.setPref("reminders","true")
        val r=store.snapshot().reminders.single();val now=ZonedDateTime.now();val stamp=now.toInstant().toEpochMilli()
        store.putOccurrence(Occurrence("before-pause",r.id,stamp-60000,stamp+3600000,r.revision,med.revision))
        try {
            store.pause(ReminderPause(true));ReminderScheduler.reconcile(context,store,now)
            assertTrue(store.occurrences().none { it.state=="pending" });assertTrue(store.snapshot().nonUse.isEmpty())
            ReminderScheduler.handle(context,store,"before-pause","LOG",now);assertTrue(store.snapshot().entries.isEmpty())
            store.pause(ReminderPause(false));ReminderScheduler.reconcile(context,store,now)
            assertTrue(store.occurrences().filter { it.state=="pending" }.all { it.scheduled>stamp })
        } finally { ReminderScheduler.cancelAll(context,store) }
    }

    @Test fun resumingBeforePrescriptionDateRearmsThatFutureDate() = database { store ->
        store.saveMedication(null,Preset.METHYLPHENIDATE_IR,10.0);val med=store.snapshot().medications.single()
        val prescription=System.currentTimeMillis()+86400000
        store.saveSupply(Supply(med.id,"tablet",100,1.0,10.0,prescriptionDate=prescription),10.0,"count")
        store.setPref("supply_enabled","true")
        try {
            store.pause(ReminderPause(true));SupplyAlerts.reconcile(context,store)
            val prefs=context.getSharedPreferences("supply_alerts",0)
            assertEquals(prescription,prefs.getLong("rx:${med.id}",-1))
            store.pause(ReminderPause(false));SupplyAlerts.reconcile(context,store)
            assertFalse(prefs.contains("rx:${med.id}"))
        } finally { SupplyAlerts.cancelAll(context) }
    }

}
