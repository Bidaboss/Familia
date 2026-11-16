package fr.familia.app.feature.gifts.share

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A link shared to Familia from another app, on its way to « Mes envies » (PRD, récit 8 bis). */
data class SharedWish(val url: String, val title: String)

private val LINK = Regex("""https?://\S+""")

/**
 * The first web link of what another app shared, with a title: the subject the app gave, else the
 * text around the link. Shops often share « Regarde ce produit : https://… ». Null without a link.
 */
fun parseSharedWish(text: String?, subject: String?): SharedWish? {
    val body = text?.trim().orEmpty()
    val raw = LINK.find(body)?.value ?: return null
    val url = raw.trimEnd('.', ',', ')', ']', '»', '"', '\'')
    val around = body.replace(raw, " ").replace(Regex("""\s+"""), " ").trim().trim(':', '-', '–', ' ')
    val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: around
    return SharedWish(url, title.take(TITLE_MAX))
}

/** What a « Partager » of another app hands to Familia, if it is a link. */
fun Intent.sharedWish(): SharedWish? {
    if (action != Intent.ACTION_SEND || type?.startsWith("text/") != true) return null
    return parseSharedWish(getStringExtra(Intent.EXTRA_TEXT), getStringExtra(Intent.EXTRA_SUBJECT))
}

/**
 * Shows Familia in the share menu of the phone only while the module is on: the entry is an alias
 * of the main screen, off in the manifest.
 */
class ShareTargetSwitch(context: Context) {
    private val packageManager = context.packageManager
    private val component = ComponentName(context.packageName, "$CODE_PACKAGE.$ALIAS")

    fun set(on: Boolean) {
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (packageManager.getComponentEnabledSetting(component) != wanted) {
            packageManager.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
        }
    }

    private companion object {
        /** Declared in the namespace of the code, which the test build's package name extends. */
        val CODE_PACKAGE: String = SharedWish::class.java.name.substringBefore(".feature.")
        const val ALIAS = "ShareToWishes"
    }
}

/** The title limit of an item, checked by the security rules. */
private const val TITLE_MAX = 80
