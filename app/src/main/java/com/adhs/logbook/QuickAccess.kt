package com.adhs.logbook

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.*
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.adhs.logbook.shared.*
import java.util.UUID

object QuickAccess {
    private fun prefs(c: Context)=c.getSharedPreferences("quick_access",Context.MODE_PRIVATE)
    fun enabled(c: Context)=prefs(c).getBoolean("enabled",false)
    fun configuration(c: Context): QuickConfiguration? = prefs(c).getString("token",null)?.let { QuickConfiguration(it,prefs(c).getLong("med",0),prefs(c).getLong("revision",0)) }
    fun intent(c: Context,token: String)=Intent(c,MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("review_token",token).setData(android.net.Uri.parse("logbook://review/$token")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    fun route(c: Context,token: String,meds: List<Medication>)=QuickAccessPolicy.review(token,enabled(c),configuration(c),meds)
    fun enable(c: Context,value: Boolean) { check(prefs(c).edit().putBoolean("enabled",value).commit());reconcile(c) }
    fun configure(c: Context,med: Medication) {
        check(prefs(c).edit().putString("token",UUID.randomUUID().toString()).putLong("med",med.id).putLong("revision",med.revision).commit());reconcile(c)
    }
    private fun shortcut(c: Context,id: String,token: String,label: String)=ShortcutInfo.Builder(c,id).setShortLabel(tr(label)).setIcon(Icon.createWithResource(c,R.drawable.ic_logbook)).setIntent(intent(c,token)).build()
    fun reconcile(c: Context) {
        val manager=c.getSystemService(ShortcutManager::class.java)
        val old=manager.dynamicShortcuts+manager.pinnedShortcuts
        val current=mutableListOf<ShortcutInfo>()
        if(enabled(c)) {
            current+=shortcut(c,"review-generic","generic","Log dose")
            configuration(c)?.let { config -> Store(c).use { store ->
                val med=store.snapshot().medications.find { it.id==config.medicationId && it.active }
                if(med!=null) current+=shortcut(c,"review-"+config.token,config.token,"Review medication")
            } }
        }
        val removed=old.map { it.id }.distinct()-current.map { it.id }.toSet()
        if(removed.isNotEmpty()) manager.disableShortcuts(removed,tr("Quick access is unavailable. Open Settings to configure it."))
        if(current.isNotEmpty()) manager.enableShortcuts(current.map { it.id })
        manager.dynamicShortcuts=current
        TileService.requestListeningState(c,ComponentName(c,LogTile::class.java))
    }
    fun pin(c: Context) {
        val config=configuration(c) ?: return
        val manager=c.getSystemService(ShortcutManager::class.java)
        if(manager.isRequestPinShortcutSupported) manager.requestPinShortcut(shortcut(c,"review-"+config.token,config.token,"Review medication"),null)
    }
    fun reset(c: Context) { prefs(c).edit().clear().commit();reconcile(c) }
    fun addTile(c: Context) {
        if(Build.VERSION.SDK_INT>=33) c.getSystemService(StatusBarManager::class.java).requestAddTileService(ComponentName(c,LogTile::class.java),tr("Log dose"),Icon.createWithResource(c,R.drawable.ic_logbook),c.mainExecutor) { }
    }
}
class LogTile: TileService() {
    override fun onStartListening() { super.onStartListening();qsTile?.apply { label=tr("Log dose");state=if(QuickAccess.enabled(this@LogTile)) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE;updateTile() } }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required before API 34.
    override fun onClick() {
        super.onClick();if(!QuickAccess.enabled(this)) return
        val open=Runnable {
            val intent=QuickAccess.intent(this,"generic")
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        }
        if(isLocked) unlockAndRun(open) else open.run()
    }
}
