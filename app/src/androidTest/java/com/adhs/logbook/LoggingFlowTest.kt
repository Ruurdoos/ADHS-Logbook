package com.adhs.logbook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoggingFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun resetTestLog() {
        // Text assertions are English; keep them independent of the device language.
        if(android.os.Build.VERSION.SDK_INT>=33) ui.activity.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags("en")
        Store(ui.activity).use { store ->
            store.restore(com.adhs.logbook.shared.BackupDocument(createdAt=0,medications=emptyList(),entries=emptyList(),reminders=emptyList()))
        }
        ui.activityRule.scenario.recreate()
    }
    @After fun resetLanguage() {
        if(android.os.Build.VERSION.SDK_INT>=33) ui.activity.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.getEmptyLocaleList()
    }
    private fun openHome() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
        if(ui.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("Get started").performClick()
            ui.onNodeWithText("Your usual dose (mg)").performTextInput("10")
            ui.onNodeWithText("Start",useUnmergedTree=true).performScrollTo().performClick()
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("＋ Record").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openRecordSheet() {
        ui.onNodeWithText("＋ Record").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("What do you want to record?").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun quickLogAndUndo() {
        openHome()
        val before=Store(ui.activity).use { it.snapshot().entries.size }
        ui.onNodeWithText("Taken · 10 mg").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Dose logged · 10 mg").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Undo").performClick()
        ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().entries.size==before } }
    }
    @Test fun quickLogShowsTodayAndChangeOpensEditor() {
        openHome()
        ui.onNodeWithText("Nothing recorded today").performScrollTo().assertExists()
        ui.onNodeWithText("Taken · 10 mg").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Methylphenidate IR · 10 mg").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Change").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Edit entry").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Delete entry").performScrollTo()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Delete entry").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("Delete entry").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Delete this entry?").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Delete",useUnmergedTree=true).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Nothing recorded today").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun notTakenFromHomeRecordsTodaySoFar() {
        openHome()
        ui.onNodeWithText("Not taken").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Record not taken").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Save").performScrollTo().performClick()
        ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().nonUse.isNotEmpty() } }
        Store(ui.activity).use { store ->
            val record=store.snapshot().nonUse.single()
            org.junit.Assert.assertEquals("period",record.kind)
            org.junit.Assert.assertEquals(java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),record.start)
        }
        ui.onNodeWithText("No doses taken during this period · Methylphenidate IR").performScrollTo().assertExists()
    }
    @Test fun germanLargeTextDarkModeLogging() {
        if(android.os.Build.VERSION.SDK_INT<33) return
        openHome()
        val localeManager=ui.activity.getSystemService(android.app.LocaleManager::class.java)
        val night=ui.activity.getSystemService(android.app.UiModeManager::class.java)
        val oldLocales=localeManager.applicationLocales
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        val oldScale=android.provider.Settings.System.getString(ui.activity.contentResolver,"font_scale") ?: "1.0"
        val originalIds=Store(ui.activity).use { it.snapshot().entries.map { e->e.id }.toSet() }
        try {
            shell("settings put system font_scale 1.5")
            night.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES)
            localeManager.applicationLocales=android.os.LocaleList.forLanguageTags("de")
            ui.waitUntil(20_000) { ui.onAllNodesWithText("＋ Erfassen").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("＋ Erfassen").assertIsDisplayed().performClick()
            ui.waitUntil(5_000) { ui.onAllNodesWithText("Was möchtest du erfassen?").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Wie geht es mir?").assertExists()
            ui.onNodeWithText("Medikament").performClick()
            ui.onNodeWithText("Dosis speichern").performScrollTo().assertIsDisplayed().performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithText("Zuletzt erfasst").fetchSemanticsNodes().isNotEmpty() }
            val output=ui.activity.filesDir
            File(output,"must-de-dark-large.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
            ui.onNodeWithText("Export").performClick()
            ui.onNodeWithText("Bericht erstellen").performScrollTo().assertIsDisplayed()
        } finally {
            Store(ui.activity).use { store -> store.snapshot().entries.filter { it.id !in originalIds }.forEach { store.deleteEntry(it.id) } }
            localeManager.applicationLocales=oldLocales
            night.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO)
            shell("settings put system font_scale $oldScale")
        }
    }
    @Test fun setupLogEditAndDelete() {
        openHome()
        openRecordSheet()
        ui.onNodeWithText("Medication").performClick()
        ui.onNodeWithText("How are you feeling?").assertDoesNotExist()
        ui.onNodeWithText("Notes (optional)").performTextInput("Instrumentation test note")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Instrumentation test note").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Save dose").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Dose logged",substring=true).fetchSemanticsNodes().isNotEmpty() }
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: ui.activity.getExternalFilesDir(null)!!
        output.mkdirs()
        File(output,"home-review.png").outputStream().use {
            ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        ui.onNodeWithText("History").performClick()
        ui.onNodeWithText("Methylphenidate IR").performClick()
        ui.onNodeWithText("Instrumentation test note").assertExists()
        ui.onNodeWithText("Notes (optional)").performTextReplacement("Edited instrumentation note")
        ui.onNodeWithText("Save changes").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Edit entry").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Methylphenidate IR").performClick()
        ui.onNodeWithText("Edited instrumentation note").assertExists()
        ui.onNodeWithText("Delete entry").performScrollTo()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Delete entry").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.onNodeWithText("Delete entry").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Delete this entry?").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Delete",useUnmergedTree=true).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("No doses logged yet.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun observationWithoutDoseAndSummaryPreview() {
        openHome()
        val before=Store(ui.activity).use { it.snapshot().entries.size }
        openRecordSheet()
        ui.onNodeWithText("Sleep").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Night starting on").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText("Response").onLast().performClick()
        ui.onNodeWithText("None").performClick()
        ui.onNodeWithText("Notes (optional)").performScrollTo().performTextInput("Should observation fixture")
        ui.onNodeWithText("Save").performScrollTo().performClick()
        ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().observations.any { o->o.notes=="Should observation fixture" } } }
        Store(ui.activity).use { store ->
            org.junit.Assert.assertEquals(before,store.snapshot().entries.size)
            val record=store.snapshot().observations.first { it.notes=="Should observation fixture" }
            org.junit.Assert.assertEquals("none",record.response);org.junit.Assert.assertNull(record.value)
            org.junit.Assert.assertEquals("sleep",record.category)
            store.deleteObservation(record.id)
        }
        ui.onNodeWithText("Export").performClick()
        ui.onNodeWithText("Summary with details").performScrollTo().assertExists()
        ui.onNodeWithText("Include free-text notes").performScrollTo().assertExists()
    }

    @Test fun optionalMeasurementAndWeeklyOverview() {
        openHome()
        val before=Store(ui.activity).use { store -> store.setPref("measurements_enabled","true");store.setPref("weekly_enabled","true");store.snapshot().entries.size }
        ui.activityRule.scenario.recreate();openHome()
        try {
            openRecordSheet()
            ui.onNodeWithText("Measurement").performClick()
            ui.onNodeWithText("Systolic (mmHg)").performTextInput("120")
            ui.onNodeWithText("Diastolic (mmHg)").performTextInput("80")
            ui.onNodeWithText("Notes (optional)").performScrollTo().performTextInput("Could measurement fixture")
            ui.onNodeWithText("Save").performScrollTo().performClick()
            ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().measurements.any { m->m.notes=="Could measurement fixture" } } }
            Store(ui.activity).use { store ->
                org.junit.Assert.assertEquals(before,store.snapshot().entries.size)
                org.junit.Assert.assertEquals(80.0,store.snapshot().measurements.first { it.notes=="Could measurement fixture" }.diastolic!!,0.0)
            }
            ui.onNode(hasText("History") and hasClickAction()).performClick()
            ui.onNodeWithText("Weekly overview").performClick()
            ui.onNodeWithText("Choose week").assertExists()
            ui.onNodeWithText("Could measurement fixture").performScrollTo().assertExists()
            File(ui.activity.filesDir,"could-weekly.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
            if(android.os.Build.VERSION.SDK_INT>=33) {
                val locales=ui.activity.getSystemService(android.app.LocaleManager::class.java)
                val previous=locales.applicationLocales
                try {
                    locales.applicationLocales=android.os.LocaleList.forLanguageTags("de")
                    ui.waitUntil(10_000) { ui.onAllNodesWithText("Wochenübersicht").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Woche auswählen").performScrollTo().assertExists()
                    File(ui.activity.filesDir,"could-weekly-de.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
                } finally { locales.applicationLocales=previous }
            }
        } finally {
            Store(ui.activity).use { store -> store.snapshot().measurements.filter { it.notes=="Could measurement fixture" }.forEach { store.deleteMeasurement(it.id) };store.setPref("measurements_enabled","false");store.setPref("weekly_enabled","false") }
        }
    }

    @Test fun medicationFreeOnboardingAndNestedObservationDraft() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Get started").performClick()
        ui.onNodeWithText("No medication",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("Start",useUnmergedTree=true).performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("＋ Record").fetchSemanticsNodes().isNotEmpty() }
        openRecordSheet()
        ui.onNodeWithText("Not taken").assertDoesNotExist()
        ui.onNodeWithText("Focus").performClick()
        ui.onAllNodesWithText("Response").onLast().performClick();ui.onNodeWithText("None").performClick()
        ui.onNodeWithText("Notes (optional)").performScrollTo().performTextInput("Preserved draft")
        ui.onNodeWithText("Add measurement").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Back").performClick();ui.onNodeWithText("Discard").performClick()
        ui.onNodeWithText("Preserved draft").performScrollTo().assertExists()
        ui.onNodeWithText("Save").performScrollTo().performClick()
        ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().observations.any { o->o.notes=="Preserved draft" } } }
        Store(ui.activity).use { org.junit.Assert.assertTrue(it.snapshot().medications.isEmpty());org.junit.Assert.assertTrue(it.snapshot().entries.isEmpty()) }
    }
}
