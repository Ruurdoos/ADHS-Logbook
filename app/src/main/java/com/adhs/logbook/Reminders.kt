package com.adhs.logbook

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.adhs.logbook.shared.*
import java.time.*
import java.util.UUID
import java.util.concurrent.Executors

object ReminderScheduler {
    private fun pending(context: Context, id: String, action: String="DELIVER") = PendingIntent.getBroadcast(context,0,
        Intent(context,ReminderReceiver::class.java).setAction(action).setData(Uri.parse("logbook://reminder/$id/$action")).putExtra("occurrence",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancelOccurrence(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context,id))
        context.getSystemService(NotificationManager::class.java).cancel(id,1)
    }
    fun cancelAll(context: Context, store: Store) { store.occurrences().forEach { cancelOccurrence(context,it.id) } }
    fun cancel(context: Context, id: Int) {
        // Cancel the v1 scheduler token, too, when upgrading.
        context.getSystemService(AlarmManager::class.java).cancel(PendingIntent.getBroadcast(context,id,
            Intent(context,ReminderReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }
    fun reschedule(context: Context, unused: LogbookState? = null) = Store(context).use { reconcile(context,it) }
    fun reconcile(context: Context, store: Store, now: ZonedDateTime = ZonedDateTime.now()) {
        store.transaction {
            val state=store.snapshot(); val instant=now.toInstant().toEpochMilli()
            state.reminders.forEach { cancel(context,it.id) }
            store.occurrences().forEach { o ->
                val r=state.reminders.find { it.id==o.reminderId }
                val m=state.medications.find { it.id==r?.medicationId }
                val invalid=state.pause.active(instant) || !state.remindersEnabled || r==null || r.revision!=o.revision || instant>=o.expires ||
                    (r.medicationId!=null && (m==null || !m.active || m.revision!=o.medicationRevision))
                if(invalid || o.state!="pending") {
                    context.getSystemService(AlarmManager::class.java).cancel(pending(context,o.id))
                    if(o.state!="logged") cancelOccurrence(context,o.id)
                    if(o.state=="pending") store.putOccurrence(o.copy(state="expired"))
                }
            }
            if(state.remindersEnabled && !state.pause.active(instant)) state.reminders.forEach { r ->
                val m=state.medications.find { it.id==r.medicationId }
                if(r.medicationId==null || m?.active==true) {
                    var next=now.toLocalDate().atTime(r.hour,r.minute).atZone(now.zone)
                    if(!next.isAfter(now)) next=next.toLocalDate().plusDays(1).atTime(r.hour,r.minute).atZone(now.zone)
                    val scheduled=next.toInstant().toEpochMilli()
                    // Cancel future occurrences from a previous time zone, then prepare one upcoming day.
                    store.occurrences().filter { it.reminderId==r.id && it.state=="pending" && it.scheduled>instant && it.scheduled!=scheduled }.forEach {
                        store.putOccurrence(it.copy(state="expired"));cancelOccurrence(context,it.id)
                    }
                    if(store.occurrences().none { it.reminderId==r.id && it.scheduled==scheduled && it.state=="pending" })
                        store.putOccurrence(Occurrence(UUID.randomUUID().toString(),r.id,scheduled,scheduled+r.cutoffMinutes*60_000L,r.revision,m?.revision))
                }
            }
            val wake=PendingIntent.getBroadcast(context,0,Intent(context,ReminderReceiver::class.java).setAction("PAUSE_WAKE"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarms=context.getSystemService(AlarmManager::class.java);alarms.cancel(wake)
            if(state.pause.active(instant)) state.pause.until?.let { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,it,wake) }
            store.occurrences().filter { it.state=="pending" && it.nextAlert<it.expires }.forEach {
                context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,it.nextAlert.coerceAtLeast(instant+1000),pending(context,it.id))
            }
        }
    }
    fun resolve(store: Store, occurrenceId: String?, entryId: Long, now: Long=System.currentTimeMillis()): Boolean {
        val occurrence=store.occurrences().find { it.id==occurrenceId } ?: return false
        val snapshot=store.snapshot()
        val entry=snapshot.entries.find { it.id==entryId } ?: return false
        val reminder=snapshot.reminders.find { it.id==occurrence.reminderId } ?: return false
        val medication=snapshot.medications.find { it.id==reminder.medicationId }
        if(!snapshot.remindersEnabled || (reminder.medicationId!=null && reminder.medicationId!=entry.medicationId) ||
            !ReminderPolicy.actionable(occurrence,reminder,medication,now)) return false
        store.putOccurrence(occurrence.copy(state="logged",entryId=entryId))
        return true
    }
    fun notification(context: Context, o: Occurrence, r: Reminder?, recorded: Boolean=false, expired: Boolean=false) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("log_reminders",context.getString(R.string.reminders),NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java)
            .setData(Uri.parse("logbook://open/${o.id}")).putExtra("occurrence",if(expired || recorded) null else o.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(context,"log_reminders").setSmallIcon(R.drawable.ic_logbook)
            .setContentTitle(context.getString(if(recorded) R.string.dose_logged else R.string.reminder_title))
            .setContentText(context.getString(if(expired) R.string.reminder_expired else if(recorded) R.string.review_history else R.string.reminder_body))
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context,"log_reminders").setSmallIcon(R.drawable.ic_logbook).setContentTitle(context.getString(R.string.reminders)).build())
        if(!recorded && !expired && !AppPrivacy.isEnabled(context)) {
            if(r?.medicationId!=null) builder.addAction(0,context.getString(R.string.log_now),pending(context,o.id,"LOG"))
            builder.addAction(0,context.getString(R.string.snooze),pending(context,o.id,"SNOOZE"))
            builder.addAction(0,context.getString(R.string.open),open)
        }
        if(AppPrivacy.isEnabled(context)) builder.addAction(0,context.getString(R.string.open),open)
        if(recorded && !AppPrivacy.isEnabled(context)) builder.addAction(0,context.getString(R.string.undo),pending(context,o.id,"UNDO")).setSilent(true)
        manager.notify(o.id,1,builder.build())
    }
    fun handle(context: Context, store: Store, id: String, action: String, now: ZonedDateTime=ZonedDateTime.now()) {
        var confirmLogged: (() -> Unit)? = null
        store.transaction {
            val o=store.occurrences().find { it.id==id } ?: return@transaction
            val s=store.snapshot();val r=s.reminders.find { it.id==o.reminderId };val m=s.medications.find { it.id==r?.medicationId }
            if(AppPrivacy.isEnabled(context) && action in listOf("LOG","UNDO","SNOOZE")) {
                notification(context,o,r,expired=true);return@transaction
            }
            if(action=="UNDO" && o.state=="logged") {
                o.entryId?.let(store::deleteEntry);store.putOccurrence(o.copy(state="undone"));cancelOccurrence(context,id)
                return@transaction
            }
            if(s.pause.active(now.toInstant().toEpochMilli()) || !s.remindersEnabled || !ReminderPolicy.actionable(o,r,m,now.toInstant().toEpochMilli())) {
                if(o.state=="pending") { store.putOccurrence(o.copy(state="expired"));notification(context,o,r,expired=true) }
                return@transaction
            }
            when(action) {
                "LOG" -> if(m!=null) {
                    val logId=LogDose(store,LogClock { now.toInstant().toEpochMilli() }).now(m,"reminder:$id",now.zone.id,now.offset.id)
                    resolve(store,id,logId,now.toInstant().toEpochMilli())
                    confirmLogged = { cancelOccurrence(context,id);notification(context,o,r,recorded=true) }
                }
                "SNOOZE" -> {
                    val next=(now.toInstant().toEpochMilli()+10*60_000).coerceAtMost(o.expires)
                    store.putOccurrence(o.copy(nextAlert=next));cancelOccurrence(context,id)
                }
                "DELIVER" -> {
                    // Duplicate broadcasts must not produce a fresh follow-up chain.
                    if(o.nextAlert>now.toInstant().toEpochMilli()+1500) return@transaction
                    notification(context,o,r)
                    val next=if(r!!.followUp && !o.delivered && !o.followedUp) (now.toInstant().toEpochMilli()+30*60_000).coerceAtMost(o.expires) else o.expires
                    store.putOccurrence(o.copy(delivered=true,followedUp=o.delivered || o.followedUp,nextAlert=next))
                }
            }
        }
        LogWidget.refresh(context)
        SupplyAlerts.reconcile(context,store)
        confirmLogged?.invoke() // Confirm only after the outer database transaction commits.
        reconcile(context,store,now)
    }
}
private val receiverWorker=Executors.newSingleThreadExecutor()
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val result=goAsync()
        receiverWorker.execute { try { Store(context).use { store ->
            val id=intent.getStringExtra("occurrence")
            if(intent.action=="SUPPLY") SupplyAlerts.reconcile(context,store)
            else if(id!=null) ReminderScheduler.handle(context,store,id,intent.action ?: "DELIVER") else { ReminderScheduler.reconcile(context,store);SupplyAlerts.reconcile(context,store) }
        } } catch(_: Exception) { /* A failed write never confirms a dose. The log remains editable in-app. */ }
        finally { result.finish() } }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val result=goAsync();receiverWorker.execute { try { ReminderScheduler.reschedule(context);Store(context).use { SupplyAlerts.reconcile(context,it) };LogWidget.refresh(context) } finally { result.finish() } }
    }
}
