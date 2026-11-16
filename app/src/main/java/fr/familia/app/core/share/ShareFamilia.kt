package fr.familia.app.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile

/**
 * « Partager Familia » (PRD, récit 1 bis bis): any adult may send the whole family the install
 * link. The link carries no token, so it adds nobody: the admin principal still enters each address.
 */
fun canShareApp(me: Member?): Boolean = me?.profile == MemberProfile.Adult

/**
 * The message, ready and still editable in the app chosen. [ownerFirstName] is read from the
 * family, never written in the code; without it the message names « l'admin de la famille ».
 */
fun shareAppMessage(context: Context, ownerFirstName: String?, link: String = BuildConfig.DOWNLOAD_URL): String {
    val owner = ownerOr(ownerFirstName, context.getString(R.string.share_app_owner_unknown))
    return context.getString(R.string.share_app_message, link.trim(), owner)
}

internal fun ownerOr(ownerFirstName: String?, fallback: String): String = ownerFirstName?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

/** Opens the Android share sheet, WhatsApp first when it is installed. */
fun shareApp(context: Context, ownerFirstName: String?) {
    val text = shareAppMessage(context, ownerFirstName)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_app_chooser))
    whatsApp(context)?.let { pkg ->
        val first = Intent(send).setPackage(pkg)
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(first))
    }
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can send text: nothing to open.
    }
}

/** WhatsApp, then WhatsApp Business; declared in the manifest's <queries> to be seen. */
private fun whatsApp(context: Context): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
    try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
