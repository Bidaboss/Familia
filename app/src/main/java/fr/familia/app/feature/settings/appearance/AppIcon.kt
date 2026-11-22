package fr.familia.app.feature.settings.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.familia.app.R

/**
 * The 3 app icons (« arbres de vie », design/icone): each is an activity-alias of MainActivity in
 * the manifest, and only one is enabled at a time. Olivier is the one enabled by the manifest.
 */
enum class AppIcon(
    val alias: String,
    @StringRes val labelRes: Int,
    @DrawableRes val foreground: Int,
    @ColorRes val background: Int,
) {
    Olivier("IconOlivier", R.string.app_icon_olivier, R.drawable.ic_launcher_foreground, R.color.ic_launcher_background),
    Terre("IconTerre", R.string.app_icon_terre, R.drawable.ic_launcher_foreground_terre, R.color.ic_launcher_background_terre),
    Or("IconOr", R.string.app_icon_or, R.drawable.ic_launcher_foreground_or, R.color.ic_launcher_background_or),
    ;

    companion object {
        val Default = Olivier

        /**
         * The component changes to make, in order: the chosen alias is enabled first, then the
         * others are disabled, so the app always has an entry in the launcher.
         */
        fun changesFor(chosen: AppIcon): List<Pair<AppIcon, Boolean>> =
            listOf(chosen to true) + entries.filter { it != chosen }.map { it to false }

        /** The icon shown when exactly one alias is on; the default when the states are mixed. */
        fun shown(enabled: (AppIcon) -> Boolean): AppIcon = entries.filter(enabled).singleOrNull() ?: Default
    }
}

/** Reads and changes the app icon of this phone through the aliases' enabled states. */
class AppIconSwitcher(context: Context) {
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    /** The aliases are declared in the namespace of the code, which the test build's package name extends. */
    private fun component(icon: AppIcon) = ComponentName(packageName, "$CODE_PACKAGE.${icon.alias}")

    private fun isEnabled(icon: AppIcon): Boolean =
        when (packageManager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.Default
            else -> false
        }

    val current: AppIcon get() = AppIcon.shown(::isEnabled)

    /** DONT_KILL_APP keeps Familia open; the launcher updates its icon a few seconds later. */
    fun choose(icon: AppIcon) {
        AppIcon.changesFor(icon).forEach { (alias, on) ->
            packageManager.setComponentEnabledSetting(
                component(alias),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private companion object {
        /** The namespace of the code, where the activity-alias live: written out, it survives R8. */
        const val CODE_PACKAGE: String = "fr.familia.app"
    }
}
