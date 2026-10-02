package com.adhs.logbook.shared

import kotlin.test.*

class CouldTest {
    private val med=Medication(1,Preset.CUSTOM,2.0,name="Separate identity",unit="ml")
    private fun doc()=BackupDocument(createdAt=100,medications=listOf(med),entries=emptyList(),reminders=emptyList())
    private fun measurement()=Measurement("m","pressure",120.0,"mmHg",80.0,100,101,"UTC","Z")
    @Test fun typedValuesRejectMissingZeroNonfiniteAndMismatchedUnits() {
        MeasurementRules.validate(measurement())
        listOf(measurement().copy(diastolic=null),measurement().copy(unit="kg"),measurement().copy(value=Double.NaN),measurement().copy(value=0.0),measurement().copy(kind="pulse",unit="bpm"),measurement().copy(kind="weight",unit="st",diastolic=null)).forEach { assertFails { MeasurementRules.validate(it) } }
    }
    @Test fun conversionsDoNotChangeOriginalUnitAndRoundTrip() {
        val original=measurement().copy(kind="weight",value=150.5,unit="lb",diastolic=null)
        assertFails { MeasurementRules.weight(Double.MAX_VALUE,"kg","lb") }
        val kg=MeasurementRules.weight(original.value,"lb","kg")
        assertEquals(150.5,MeasurementRules.weight(kg,"kg","lb"),0.0000001)
        assertEquals("lb",original.unit);assertEquals(150.5,original.value)
    }
    @Test fun backupsAreVersionedAndOlderDocumentsDefaultToNoMeasurements() {
        val current=doc().copy(measurements=listOf(measurement()))
        assertEquals(current,BackupFormat.decode(BackupFormat.encode(current)))
        assertFails { BackupFormat.validate(current.copy(version=2)) }
        assertTrue(BackupFormat.decode(BackupFormat.encode(doc().copy(version=2))).measurements.isEmpty())
    }
    @Test fun untrustedRoutesCannotSelectArbitraryMedicationOrAmounts() {
        val c=QuickConfiguration("opaque",med.id,med.revision)
        assertTrue(QuickAccessPolicy.review("opaque",false,c,listOf(med)).unavailable)
        assertTrue(QuickAccessPolicy.review("medication=1&dose=999",true,c,listOf(med)).unavailable)
        assertNull(QuickAccessPolicy.review("generic",true,c,listOf(med)).medicationId)
        assertEquals(med.id,QuickAccessPolicy.review("opaque",true,c,listOf(med)).medicationId)
        assertTrue(QuickAccessPolicy.review("opaque",true,c,listOf(med.copy(active=false))).unavailable)
        assertTrue(QuickAccessPolicy.review("opaque",true,c,listOf(med.copy(revision=2))).changed)
    }
    @Test fun weeklySummaryMatchesReportsWithoutInventingMissingValues() {
        val bounds=listOf(0L,1000,2000,3000,4000,5000,6000,7000)
        val d=doc().copy(measurements=listOf(measurement()),observations=listOf(Observation("o","focus","none",timestamp=2000,createdAt=2000,zoneId="UTC",offset="Z")),nonUse=listOf(NonUse("n",1,3000,3000,3000,"UTC","Z")))
        val week=WeeklyBuilder.build(d,bounds)
        assertEquals(SummaryBuilder.build(d,bounds),week.summary)
        assertEquals(4,week.summary.daysWithoutRecords);assertEquals(1,week.summary.measurementDays)
        assertEquals(1,week.distributions.single().count);assertNull(week.distributions.single().value)
        assertTrue(week.days.flatMap { it.doses }.isEmpty());assertTrue(week.legacyMoods.isEmpty())
    }
    @Test fun irregularDayBoundariesAndOrdinalDistributionStaySeparate() {
        val bounds=listOf(0L,24,48,73,97,121,145,169).map { it*3600000 }
        val first=DoseEntry(1,1,Preset.CUSTOM,2.0,bounds[2],"UTC","Z",mood=4,unit="ml",medicationName="Old",formulation="solution")
        val second=first.copy(id=2,timestamp=bounds[3],medicationName="New",formulation="other")
        val d=doc().copy(entries=listOf(first,second),observations=listOf(Observation("o","mood","rated",0,bounds[2],bounds[2],"UTC","Z")))
        val week=WeeklyBuilder.build(d,bounds)
        assertEquals(2,week.summary.medications.size);assertEquals(0,week.distributions.single().value)
        assertEquals(4,week.legacyMoods.single().value);assertEquals(2,week.legacyMoods.single().count)
        assertEquals(listOf(first),week.days[2].doses);assertEquals(listOf(second),week.days[3].doses)
    }
}
