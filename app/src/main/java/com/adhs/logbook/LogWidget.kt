package com.adhs.logbook

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.util.UUID
import com.adhs.logbook.shared.*
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogWidget: GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(androidx.compose.ui.unit.DpSize(150.dp,110.dp),androidx.compose.ui.unit.DpSize(240.dp,160.dp)))
    override suspend fun provideGlance(context: Context,id: GlanceId) {
        val prefs=context.getSharedPreferences("widget",Context.MODE_PRIVATE)
        val medId=prefs.getLong("med",0);val token=prefs.getString("token","") ?: ""
        val state=withContext(Dispatchers.IO) { Store(context).use { it.snapshot() } }
        val med=state.medications.find { it.id==medId && it.active }
        val private=AppPrivacy.isEnabled(context) || prefs.getBoolean("private",true)
        val last=state.entries.firstOrNull { it.medicationId==medId }
        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).padding(12.dp),verticalAlignment=Alignment.Vertical.CenterVertically) {
                    Text(if(private || med==null) tr("Medication log") else med.name,style=TextStyle(color=GlanceTheme.colors.onSurface,fontSize=14.sp),maxLines=2)
                    if(!private && last!=null) Text(tr("Last logged")+": "+Instant.ofEpochMilli(last.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, "+if(android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")),style=TextStyle(color=GlanceTheme.colors.onSurface,fontSize=12.sp),maxLines=2)
                    Spacer(GlanceModifier.height(8.dp))
                    Button(if(private || med==null) tr("Open logbook") else tr("Log now · %s %s",doseText(med.usualDose),tr(med.unit)),
                        onClick=actionStartActivity(Intent(context,MainActivity::class.java).putExtra("widget_token",token).setData(android.net.Uri.parse("logbook://widget/$token"))))
                }
            }
        }
    }
    companion object {
        fun log(context: Context,store: Store,token: String,now: ZonedDateTime=ZonedDateTime.now()): Long {
            var id=0L
            store.transaction {
                val prefs=context.getSharedPreferences("widget",Context.MODE_PRIVATE)
                require(token.isNotBlank() && prefs.getString("token",null)==token && !prefs.getBoolean("private",true))
                require(!AppPrivacy.isEnabled(context))
                val med=store.snapshot().medications.first { it.id==prefs.getLong("med",0) && it.active && it.revision==prefs.getLong("revision",0) }
                id=LogDose(store,LogClock { now.toInstant().toEpochMilli() }).now(med,"widget:$token",now.zone.id,now.offset.id)
            }
            return id
        }
        fun configure(context: Context,medId: Long,revision: Long,private: Boolean) {
            check(context.getSharedPreferences("widget",Context.MODE_PRIVATE).edit().putLong("med",medId).putLong("revision",revision).putBoolean("private",private).putString("token",UUID.randomUUID().toString()).commit())
            refresh(context)
        }
        fun refresh(context: Context) { CoroutineScope(Dispatchers.IO).launch { runCatching { LogWidget().updateAll(context.applicationContext) } } }
        fun invalidate(context: Context) {
            context.getSharedPreferences("widget",Context.MODE_PRIVATE).edit().putString("token",UUID.randomUUID().toString()).apply();refresh(context)
        }
    }
}
class LogWidgetReceiver: GlanceAppWidgetReceiver() { override val glanceAppWidget=LogWidget() }
