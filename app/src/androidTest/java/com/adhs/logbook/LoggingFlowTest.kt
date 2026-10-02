package com.adhs.logbook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoggingFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun removeOnlyPreviousTestFixtures() {
        Store(ui.activity).use { store ->
            store.snapshot().entries.filter { it.notes in listOf("Instrumentation test note","Edited instrumentation note") }.forEach { store.deleteEntry(it.id) }
        }
        ui.activityRule.scenario.recreate()
    }
    private fun openHome() {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() || ui.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
        if(ui.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()) {
            ui.onNodeWithText("Get started").performClick()
            ui.onNodeWithText("Your usual dose (mg)").performTextInput("10")
            ui.onNodeWithText("Start",useUnmergedTree=true).performScrollTo().performClick()
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithText("＋ Log dose").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun quickLogAndUndo() {
        openHome()
        val before=Store(ui.activity).use { it.snapshot().entries.size }
        ui.onNodeWithText("Log now · 10 mg").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Dose logged").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Undo").performClick()
        ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().entries.size==before } }
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
            ui.waitUntil(20_000) { ui.onAllNodesWithText("＋ Dosis erfassen").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("＋ Dosis erfassen").assertIsDisplayed().performClick()
            ui.onNodeWithText("Dosis speichern").performScrollTo().assertIsDisplayed().performClick()
            ui.waitUntil(10_000) { ui.onAllNodesWithText("Zuletzt erfasst").fetchSemanticsNodes().isNotEmpty() }
            val output=ui.activity.filesDir
            File(output,"must-de-dark-large.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
            ui.onNodeWithText("Export").performClick()
            ui.onNodeWithText("Bericht exportieren").performScrollTo().assertIsDisplayed()
        } finally {
            Store(ui.activity).use { store -> store.snapshot().entries.filter { it.id !in originalIds }.forEach { store.deleteEntry(it.id) } }
            localeManager.applicationLocales=oldLocales
            night.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO)
            shell("settings put system font_scale $oldScale")
        }
    }
    @Test fun setupLogEditAndDelete() {
        openHome()
        ui.onNodeWithText("＋ Log dose").performClick()
        ui.onNodeWithText("Notes (optional)").performTextInput("Instrumentation test note")
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Instrumentation test note").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Save dose").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Dose logged").fetchSemanticsNodes().isNotEmpty() }
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
        ui.onNodeWithText("History").performClick()
        ui.onNodeWithText("Observations & non-use").performClick()
        ui.onNodeWithText("Add observation").performClick()
        ui.onAllNodesWithText("Response").onLast().performClick()
        ui.onNodeWithText("None").performClick()
        ui.onNodeWithText("Notes (optional)").performScrollTo().performTextInput("Should observation fixture")
        ui.onNodeWithText("Save").performScrollTo().performClick()
        ui.waitUntil(10_000) { Store(ui.activity).use { it.snapshot().observations.any { o->o.notes=="Should observation fixture" } } }
        Store(ui.activity).use { store ->
            org.junit.Assert.assertEquals(before,store.snapshot().entries.size)
            val record=store.snapshot().observations.first { it.notes=="Should observation fixture" }
            org.junit.Assert.assertEquals("none",record.response);org.junit.Assert.assertNull(record.value)
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
            ui.onNode(hasText("History") and hasClickAction()).performClick()
            ui.onNodeWithText("Observations & non-use").performClick()
            ui.onNodeWithText("Add measurement").performClick()
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

}
