package fr.familia.app.core.whatsnew

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The screens « Essayer » can open, by the key written in the field `open` of a card
 * (docs/whats-new/README.md). Each one has the icon of the card's Argile illustration. FamiliaApp
 * does the navigation.
 */
enum class WhatsNewTarget(val key: String, val icon: ImageVector) {
    Home("home", Icons.Outlined.Home),
    Planning("planning", Icons.Outlined.CalendarMonth),
    Gifts("gifts", Icons.Outlined.CardGiftcard),
    HelpRequests("helpRequests", Icons.Outlined.VolunteerActivism),
    Members("members", Icons.Outlined.Groups),
    Recipes("recipes", Icons.Outlined.Restaurant),
    Trips("trips", Icons.Outlined.Luggage),
    Polls("polls", Icons.Outlined.HowToVote),
    Capsules("capsules", Icons.Outlined.HourglassEmpty),
    Genealogy("genealogy", Icons.Outlined.AccountTree),
    WeeklyQuestion("weeklyQuestion", Icons.Outlined.QuestionAnswer),
    GoodNews("goodNews", Icons.Outlined.Celebration),
    JoinMe("joinMe", Icons.Outlined.Place),
    Appearance("appearance", Icons.Outlined.Palette),
    WhoIsIt("whoIsIt", Icons.Outlined.PhotoAlbum),
    ;

    companion object {
        fun of(key: String?): WhatsNewTarget? = entries.firstOrNull { it.key == key }

        /** The illustration of a card without a screen to open. */
        val defaultIcon: ImageVector = Icons.Outlined.AutoAwesome
    }
}
