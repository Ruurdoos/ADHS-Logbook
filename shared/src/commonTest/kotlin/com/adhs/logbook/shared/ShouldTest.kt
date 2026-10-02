package com.adhs.logbook.shared

import kotlin.test.*

class ShouldTest {
    private val med=Medication(1,Preset.METHYLPHENIDATE_IR,10.0)
    private fun document()=BackupDocument(createdAt=100,medications=listOf(med),entries=emptyList(),reminders=emptyList())
    private fun dose(id: Long,time: Long,amount: Double=10.0,units: Double?=null)=DoseEntry(id,1,med.preset,amount,time,"UTC","Z",supplyUnits=units)
    private fun supplied()=document().copy(supplies=listOf(Supply(1,"tablet",100,2.0,10.0)),stock=listOf(StockMovement("count",1,"count",100,10.0)))
    @Test fun inventoryIsIdempotentAndReconcilesEditsUndoAndPartialUnits() {
        val one=SupplyLedger.reconcile(supplied().copy(entries=listOf(dose(1,101,5.0))))
        assertEquals(9.5,SupplyLedger.balance(one,one.supplies.single()).remaining)
        assertEquals(one,SupplyLedger.reconcile(one))
        val edited=SupplyLedger.reconcile(one.copy(entries=listOf(dose(1,101,20.0))))
        assertEquals(8.0,SupplyLedger.balance(edited,edited.supplies.single()).remaining)
        val undone=SupplyLedger.reconcile(edited.copy(entries=emptyList()))
        assertEquals(10.0,SupplyLedger.balance(undone,undone.supplies.single()).remaining)
        BackupFormat.validate(undone)
    }
    @Test fun aNewPhysicalCountDoesNotConsumeBackdatedDoses() {
        val doc=SupplyLedger.reconcile(supplied().copy(entries=listOf(dose(1,99,100.0),dose(2,101)),stock=supplied().stock+StockMovement("restock",1,"restock",102,3.5)))
        assertEquals(12.5,SupplyLedger.balance(doc,doc.supplies.single()).remaining)
    }
    @Test fun missingMappingStaysUnknownAndNegativeStockIsNotClamped() {
        val raw=supplied().copy(supplies=listOf(supplied().supplies.single().copy(dosePerUnit=null)),entries=listOf(dose(1,101)))
        val unknown=SupplyLedger.reconcile(raw)
        assertEquals(1,SupplyLedger.balance(unknown,unknown.supplies.single()).uncountedLogs)
        val negative=SupplyLedger.reconcile(raw.copy(entries=listOf(dose(1,101,units=12.0))))
        assertEquals(-2.0,SupplyLedger.balance(negative,negative.supplies.single()).remaining)
        assertTrue(SupplyLedger.balance(negative,negative.supplies.single()).inconsistent)
    }
    @Test fun observationValuesAreExplicitAndVersioned() {
        val observation=Observation("o","sleep","unsure",timestamp=100,createdAt=101,zoneId="UTC",offset="Z",sleepDate="2026-09-29")
        val doc=document().copy(observations=listOf(observation))
        assertEquals(doc,BackupFormat.decode(BackupFormat.encode(doc)))
        assertFails { BackupFormat.validate(doc.copy(observations=listOf(observation.copy(response="rated")))) }
        assertFails { BackupFormat.validate(doc.copy(observations=listOf(observation.copy(value=0)))) }
        assertFails { BackupFormat.validate(doc.copy(observations=listOf(observation.copy(scaleVersion=99)))) }
    }
    @Test fun nonUseDoesNotBecomeDoseOrOverlapRecordedDoses() {
        val n=NonUse("n",1,100,200,201,"UTC","Z")
        val doc=supplied().copy(nonUse=listOf(n))
        BackupFormat.validate(doc)
        assertEquals(10.0,SupplyLedger.balance(SupplyLedger.reconcile(doc),doc.supplies.single()).remaining)
        assertFails { BackupFormat.validate(doc.copy(entries=listOf(dose(1,150)))) }
        assertFails { BackupFormat.validate(doc.copy(nonUse=listOf(n,n.copy(id="n2")))) }
    }
    @Test fun pausesExpireWithoutRecordingNonUse() {
        assertTrue(ReminderPause(true).active(1000000))
        assertTrue(ReminderPause(true,200).active(199))
        assertFalse(ReminderPause(true,200).active(200))
        assertFalse(ReminderPause(false,200).active(199))
        assertTrue(document().copy(pause=ReminderPause(true)).nonUse.isEmpty())
    }
    @Test fun reportCoverageKeepsMissingAndExplicitNoneSeparate() {
        val doc=document().copy(entries=listOf(dose(1,100)),observations=listOf(Observation("o","focus","none",timestamp=1100,createdAt=1100,zoneId="UTC",offset="Z")),nonUse=listOf(NonUse("n",1,2100,2200,2300,"UTC","Z")))
        val summary=SummaryBuilder.build(doc,listOf(0,1000,2000,3000,4000))
        assertEquals(4,summary.days);assertEquals(1,summary.daysWithoutRecords)
        assertEquals(1,summary.doseDays);assertEquals(1,summary.nonUseDays)
        assertEquals(1,summary.observations.single().none);assertEquals(0,summary.observations.single().rated)
        assertEquals(4,SummaryBuilder.build(document(),listOf(0,1000,2000,3000,4000)).daysWithoutRecords)
    }
    @Test fun oldBackupsRemainReadableAndLockNeverPermitsExternalWrite() {
        val old=document().copy(version=1)
        assertEquals(old,BackupFormat.decode(BackupFormat.encode(old)))
        assertFails { BackupFormat.validate(old.copy(pause=ReminderPause(true))) }
        assertTrue(PrivacyPolicy.needsAuthentication(true,false));assertFalse(PrivacyPolicy.needsAuthentication(true,true))
        assertFalse(PrivacyPolicy.externalMayWrite(true));assertTrue(PrivacyPolicy.externalMayWrite(false))
    }
    @Test fun sleepQualityDoesNotMergeWithLegacySleep() {
        val old=Observation("old","sleep","rated",2,100,100,"UTC","Z",sleepDate="2026-10-01")
        val quality=old.copy(id="quality",scaleVersion=2)
        val doc=document().copy(observations=listOf(old,quality))
        val decoded=BackupFormat.decode(BackupFormat.encode(doc))
        assertEquals(listOf(1,2),decoded.observations.map { it.scaleVersion })
        val week=WeeklyBuilder.build(decoded,listOf(0,200,400,600,800,1000,1200,1400))
        assertEquals(2,week.distributions.size)
        assertFails { BackupFormat.validate(doc.copy(version=3)) }
    }
    @Test fun newNonUseIntervalsExcludeEndAndScheduledMissesAllowOtherDoses() {
        val period=NonUse("period",1,100,200,201,"UTC","Z",kind="period")
        assertTrue(period.contains(100));assertFalse(period.contains(200))
        assertFalse(period.intersects(200,300))
        BackupFormat.validate(document().copy(nonUse=listOf(period),entries=listOf(dose(1,200))))
        assertFails { BackupFormat.validate(document().copy(nonUse=listOf(period),entries=listOf(dose(1,199)))) }
        val missed=period.copy(id="missed",end=100,occurrenceId="reminder-token",kind="scheduled")
        BackupFormat.validate(document().copy(nonUse=listOf(missed),entries=listOf(dose(1,100))))
        assertFalse(missed.contains(100))
        assertTrue(period.copy(kind="legacy").contains(200))
    }
    @Test fun adjacentDaysDoNotOverlapButLegacyEndpointsStillDo() {
        val first=NonUse("first",1,100,200,300,"UTC","Z",kind="day")
        val second=first.copy(id="second",start=200,end=300)
        BackupFormat.validate(document().copy(nonUse=listOf(first,second)))
        assertFails { BackupFormat.validate(document().copy(nonUse=listOf(first.copy(kind="legacy"),second))) }
    }
}
