package com.adhs.logbook.shared

import kotlin.test.*

class ModelTest {
    private val ir = Preset.METHYLPHENIDATE_IR
    private fun dose(time: Long, preset: Preset = ir) = DoseEntry(medicationId = 1, preset = preset, doseMg = 10.0, timestamp = time, zoneId = "UTC", offset = "Z")
    @Test fun noContributionBeforeDose() { assertEquals(0.0, Estimate.at(listOf(dose(1000)), ir, 999)) }
    @Test fun peakAndHalfLifeMatchDefinedApproximation() {
        assertEquals(10.0, Estimate.contribution(ir, 10.0, 2.0), 0.0001)
        assertEquals(5.0, Estimate.contribution(ir, 10.0, 5.5), 0.0001)
    }
    @Test fun sumsSamePresetButNeverDifferentDrugs() {
        val one = Estimate.at(listOf(dose(0)), ir, 7_200_000)
        assertEquals(one * 2, Estimate.at(listOf(dose(0), dose(0), dose(0, Preset.LISDEXAMFETAMINE)), ir, 7_200_000))
    }
    @Test fun priorDayDosesContribute() { assertTrue(Estimate.at(listOf(dose(0, Preset.LISDEXAMFETAMINE)), Preset.LISDEXAMFETAMINE, 86_400_000) > 0) }
    @Test fun unsupportedProfileHasNoFabricatedCurve() { assertEquals(EstimatePhase.UNAVAILABLE, Estimate.phase(emptyList(), Preset.ATOMOXETINE, 0)) }
    @Test fun validatesDose() { listOf("", "NaN", "Infinity", "-1", "0").forEach { assertNull(parseDose(it)) }; assertEquals(2.5, parseDose("2,5")) }
    @Test fun csvPreservesQuotedMultilineNotesAndDefusesFormulas() {
        assertEquals("\"a,\"\"b\"\"\nnext\"", Csv.cell("a,\"b\"\nnext"))
        assertEquals("\"'=SUM(A1:A2)\"", Csv.cell("=SUM(A1:A2)"))
        assertEquals("\"'  @x\"", Csv.cell("  @x"))
    }
    @Test fun malformedMoodRejected() { assertFailsWith<IllegalArgumentException> { dose(0).copy(mood = 5) } }
    @Test fun unsupportedFormulationsAndChangedMedicationStaySeparated() {
        val custom = dose(0, Preset.CUSTOM)
        val extended = dose(0, Preset.CONCERTA)
        assertNull(defaultModel(Preset.CONCERTA))
        assertEquals(0.0, Estimate.at(listOf(custom, extended), Preset.CONCERTA, 7_200_000))
        val doc = BackupDocument(createdAt=0, reminders=emptyList(), medications=listOf(Medication(1, ir, 10.0),Medication(2,ir,10.0)),
            entries=listOf(dose(0).copy(id=1),dose(0).copy(id=2,medicationId=2,doseMg=100.0)))
        val curve = NativeBridge.curve(BackupFormat.encode(doc),1,86_400_000)
        val onlyOne = NativeBridge.curve(BackupFormat.encode(doc.copy(entries=doc.entries.take(1))),1,86_400_000)
        assertEquals(onlyOne,curve)
    }
    @Test fun occurrencePolicyRejectsStaleActions() {
        val med=Medication(1,ir,10.0)
        val reminder=Reminder(1,8,0,medicationId=1)
        val occurrence=Occurrence("test",1,1000,2000,reminder.revision,med.revision)
        assertTrue(ReminderPolicy.actionable(occurrence,reminder,med,1500))
        assertFalse(ReminderPolicy.actionable(occurrence,reminder,med,999))
        assertFalse(ReminderPolicy.actionable(occurrence,reminder,med,2000))
        assertFalse(ReminderPolicy.actionable(occurrence,reminder,med.copy(active=false),1500))
        assertFalse(ReminderPolicy.actionable(occurrence,reminder,med.copy(revision=2),1500))
        assertFalse(ReminderPolicy.actionable(occurrence,reminder.copy(revision=2),med,1500))
        assertFalse(ReminderPolicy.actionable(occurrence.copy(state="logged"),reminder,med,1500))
        val generic=reminder.copy(medicationId=null)
        assertTrue(ReminderPolicy.actionable(occurrence.copy(medicationRevision=null),generic,null,1500))
    }
}
