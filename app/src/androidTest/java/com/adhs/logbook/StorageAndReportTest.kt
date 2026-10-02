package com.adhs.logbook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import java.io.File
import com.adhs.logbook.shared.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class StorageAndReportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun persistenceSnapshotsAndReports() {
        val name="test-${System.nanoTime()}.db"
        val zone=ZoneId.of("Europe/Berlin")
        val date=LocalDate.of(2026,3,29) // daylight-saving transition: a 23-hour day
        try {
            val medId: Long
            Store(context,name).use { store ->
                store.saveMedication(null,Preset.METHYLPHENIDATE_IR,10.0)
                medId=store.snapshot().medications.single().id
                val time=date.atTime(8,0).atZone(zone)
                val id=store.saveEntry(DoseEntry(medicationId=medId,preset=Preset.METHYLPHENIDATE_IR,doseMg=10.0,
                    timestamp=time.toInstant().toEpochMilli(),zoneId=zone.id,offset=time.offset.id,mood=3,notes="=formula,\"quoted\"\nA calmer day."))
                store.saveMedication(medId,Preset.METHYLPHENIDATE_IR,20.0)
                store.removeMedication(medId)
                assertEquals(10.0,store.snapshot().entries.single().doseMg,0.0)
                assertTrue(store.snapshot().onboarded)
                assertEquals(id,store.snapshot().entries.single().id)
            }
            Store(context,name).use { reopened ->
                val state=reopened.snapshot()
                assertFalse(state.medications.single().active)
                assertEquals(3,state.entries.single().mood)
                assertEquals(1,Reports.inRange(state.entries,date,date,zone).size)
                val tomorrow=state.entries.single().copy(timestamp=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                assertTrue(Reports.inRange(listOf(tomorrow),date,date,zone).isEmpty())
                val csv=Reports.create(context,state.entries,date,date,ReportFormat.CSV,zone)
                assertTrue(csv.readText().contains("\"'=formula,\"\"quoted\"\""))
                assertTrue(csv.readText().contains("+02:00"))
                val longEntry=state.entries.single().copy(notes=("Long note with commas, quotes \"and\" Unicode äöü.\n").repeat(100))
                val pdf=Reports.create(context,listOf(longEntry),date,date,ReportFormat.PDF,zone)
                val output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                output?.mkdirs()
                output?.let { pdf.copyTo(File(it,"report-review.pdf"),overwrite=true) }
                PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    assertTrue(renderer.pageCount>1)
                    renderer.openPage(0).use { page ->
                        assertEquals(595,page.width)
                        val bitmap=Bitmap.createBitmap(1190,1684,Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        output?.let { File(it,"report-review.png").outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG,100,stream) } }
                        bitmap.recycle()
                    }
                }
                csv.delete(); pdf.delete()
                reopened.deleteEntry(state.entries.single().id)
                assertTrue(reopened.snapshot().entries.isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}
