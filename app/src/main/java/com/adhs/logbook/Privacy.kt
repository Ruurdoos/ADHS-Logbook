package com.adhs.logbook

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/** Only the lock preference is stored here. Biometric templates and device credentials remain with the OS. */
object AppPrivacy {
    var unlocked by mutableStateOf(false)
    var enabled by mutableStateOf(false)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("logbook.privacy",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("logbook.privacy",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun isEnabled(context: Context): Boolean {
        val raw=context.getSharedPreferences("privacy",Context.MODE_PRIVATE).getString("lock",null) ?: return false
        return runCatching {
            val data=Base64.decode(raw,Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12)))
            cipher.doFinal(data.copyOfRange(12,data.size)).contentEquals(byteArrayOf(1))
        }.getOrDefault(true)
    }
    fun setEnabled(context: Context,value: Boolean) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        check(context.getSharedPreferences("privacy",Context.MODE_PRIVATE).edit().putString("lock",Base64.encodeToString(cipher.iv+cipher.doFinal(byteArrayOf(if(value) 1 else 0)),Base64.NO_WRAP)).commit())
        enabled=value;unlocked=true
        (context as? FragmentActivity)?.window?.let { if(value) it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        LogWidget.refresh(context)
    }
    fun authenticate(activity: FragmentActivity,success: () -> Unit,error: (String)->Unit) {
        if(!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { error(tr("Set a device screen lock first."));return }
        val prompt=BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked=true;success() }
            override fun onAuthenticationError(code: Int,message: CharSequence) { error(message.toString()) }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(tr("Unlock logbook"))
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }
}
@Composable fun PrivacyGate(content: @Composable ()->Unit) {
    val activity=androidx.activity.compose.LocalActivity.current as FragmentActivity
    val holder=rememberSaveableStateHolder()
    var message by remember { mutableStateOf<String?>(null) }
    if(!com.adhs.logbook.shared.PrivacyPolicy.needsAuthentication(AppPrivacy.enabled,AppPrivacy.unlocked)) {
        holder.SaveableStateProvider("private-content",content)
    } else {
        androidx.activity.compose.BackHandler { activity.moveTaskToBack(true) }
        Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(tr("Logbook locked"),style=MaterialTheme.typography.headlineMedium)
            Button({ AppPrivacy.authenticate(activity,{ message=null },{ message=it }) },Modifier.semantics { contentDescription=tr("App lock") }) { Text(tr("Unlock logbook")) }
            message?.let { Text(it) }
        }
    }
}
@Composable fun PrivacyControl() {
    val activity=androidx.activity.compose.LocalActivity.current as FragmentActivity
    var message by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(tr("App lock"),Modifier.weight(1f))
        Switch(AppPrivacy.enabled,{ desired -> AppPrivacy.authenticate(activity,{ runCatching { AppPrivacy.setEnabled(activity,desired) }.onFailure { message=tr("Could not save or load data. Please try again.") } },{ message=it }) },Modifier.semantics { contentDescription=tr("App lock") })
    }
    Text(tr("Relocks when you leave the app. Exported files remain outside this lock; this is not whole-database encryption."),style=MaterialTheme.typography.bodySmall)
    message?.let { Text(it) }
}
