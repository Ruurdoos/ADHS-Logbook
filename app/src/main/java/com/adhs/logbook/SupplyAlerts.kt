package com.adhs.logbook

import android.app.*
import android.content.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adhs.logbook.shared.*

object SupplyAlerts {
    private fun alarm(context: Context,id: Long)=PendingIntent.getBroadcast(context,0,Intent(context,ReminderReceiver::class.java).setAction("SUPPLY").setData(Uri.parse("logbook://supply/$id")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancelAll(context: Context) {
        val prefs=context.getSharedPreferences("supply_alerts",Context.MODE_PRIVATE)
        prefs.getStringSet("ids",emptySet())!!.forEach { id -> context.getSystemService(AlarmManager::class.java).cancel(alarm(context,id.toLong()));context.getSystemService(NotificationManager::class.java).cancel("supply:$id",2) }
        prefs.edit().clear().apply()
    }
    fun reconcile(context: Context,store: Store) {
        val doc=store.backup();val now=System.currentTimeMillis()
        if(store.pref("supply_enabled")!="true") { cancelAll(context);return }
        val prefs=context.getSharedPreferences("supply_alerts",Context.MODE_PRIVATE)
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("supply",tr("Supply reminders"),NotificationManager.IMPORTANCE_DEFAULT))
        val current=doc.supplies.filter { supply -> doc.medications.any { it.id==supply.medicationId && it.active } }
        (prefs.getStringSet("ids",emptySet())!!-current.map { it.medicationId.toString() }.toSet()).forEach { id ->
            context.getSystemService(AlarmManager::class.java).cancel(alarm(context,id.toLong()));manager.cancel("supply:$id",2)
        }
        prefs.edit().putStringSet("ids",current.map { it.medicationId.toString() }.toSet()).apply()
        current.forEach { supply ->
            val id=supply.medicationId;val balance=SupplyLedger.balance(doc,supply)
            val low=balance.remaining<=supply.lowThreshold
            if(!low) { prefs.edit().remove("low:$id").apply();manager.cancel("supply:$id",2) }
            context.getSystemService(AlarmManager::class.java).cancel(alarm(context,id))
            val prescription=supply.prescriptionDate
            if(prescription!=null && prescription>now) {
                if(!doc.pause.active(now)) prefs.edit().remove("rx:$id").apply() // An early resume re-enables dates that are still in the future.
                context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,prescription,alarm(context,id))
            }
            if(doc.pause.active(now)) {
                manager.cancel("supply:$id",2)
                if(prescription!=null && (doc.pause.until==null || prescription<=doc.pause.until!!)) prefs.edit().putLong("rx:$id",prescription).apply()
                return@forEach
            }
            val due=prescription!=null && prescription<=now && prefs.getLong("rx:$id",-1)!=prescription
            if(NotificationManagerCompat.from(context).areNotificationsEnabled() && ((low && !prefs.getBoolean("low:$id",false)) || due)) {
                val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).setData(Uri.parse("logbook://supply/open/$id")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationCompat.Builder(context,"supply").setSmallIcon(R.drawable.ic_logbook)
                    .setContentTitle(tr("Supply reminder")).setContentText(tr(if(due) "Your selected prescription request date has arrived." else "Review your estimated supply in the app."))
                    .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                runCatching { manager.notify("supply:$id",2,notification) }.onSuccess {
                    prefs.edit().putBoolean("low:$id",low).apply();if(due) prefs.edit().putLong("rx:$id",prescription!!).apply()
                }
            }
        }
    }
}
