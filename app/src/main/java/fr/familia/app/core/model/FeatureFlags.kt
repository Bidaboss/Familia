package fr.familia.app.core.model

import androidx.annotation.StringRes
import fr.familia.app.R

/**
 * Modules that can stay hidden from the family until they are finished and reviewed (PRD rév. 120:
 * the version shown on 21 November only shows what works). Each [key] is a field of
 * config/app.features. Every [dev] thread adds its own entry here, nothing else.
 */
enum class Feature(
    val key: String,
    @StringRes val labelRes: Int,
    /**
     * On until switched off, for everyone, the admin principal included: a kill switch for a change
     * already shown by default (R1 bis), not a module waiting for its test.
     */
    val onByDefault: Boolean = false,
) {
    Home("home", R.string.feature_home),
    Gifts("gifts", R.string.feature_gifts),
    GiftNotebook("giftNotebook", R.string.feature_gift_notebook),
    ShareToWishes("shareToWishes", R.string.feature_share_to_wishes),

    /**
     * « Ajouter à mes envies » from the share menu of another app (PRD, récit 50): no reading of the
     * shop page, and an adult may make it « Une idée pour… » someone else.
     */
    WishFromShare("wishFromShare", R.string.feature_wish_from_share),

    /** Les envies offertes passent dans « On m'a offert » le lendemain de l'occasion (PRD, récit 10 bis). */
    WishesReveal("wishesReveal", R.string.feature_wishes_reveal),
    HelpRequests("helpRequests", R.string.feature_help_requests),
    Trips("trips", R.string.feature_trips),
    GoodNews("goodNews", R.string.feature_good_news),
    Themes("themes", R.string.feature_themes),

    /**
     * Counting the time spent in the app (PRD, récit 1 nonies). Unlike the others, the switch
     * also governs the admin principal: nothing is counted until it is on for the family, once
     * the main admin has announced it.
     */
    Usage("usage", R.string.feature_usage),
    WeeklyQuestion("weeklyQuestion", R.string.feature_weekly_question),
    EventAlbums("eventAlbums", R.string.feature_event_albums),

    /** « Ajouter à mon agenda » on an event and a birthday (PRD, récit 30). */
    Calendar("calendar", R.string.feature_calendar),

    Souvenirs("souvenirs", R.string.feature_souvenirs),

    /** L'arbre de la famille et le défi du mois, sur l'Accueil (PRD, récit 35). */
    Tree("tree", R.string.feature_tree),
    /**
     * The résumé of the day (AVIS-14): the notices of the souvenirs, of the challenge and of the
     * question of the week wait for one notification at 18 h. Server only: the app just shows it.
     */
    Digest("digest", R.string.feature_digest),
    MonthSlideshow("monthSlideshow", R.string.feature_month_slideshow),
    /** Sondage de date et sondage libre (PRD, récits 2 bis et 2 ter). */
    Polls("polls", R.string.feature_polls),
    Recipes("recipes", R.string.feature_recipes),
    /** La capsule temporelle (PRD, récit 39); the server opens nothing while it is off. */
    Capsules("capsules", R.string.feature_capsules),
    /** L'arbre généalogique (PRD, récit 40), not the tree of leaves of [Tree]. */
    Genealogy("genealogy", R.string.feature_genealogy),
    /** « Qui apporte quoi » on an event (PRD, récit 4 bis). */
    Bring("bring", R.string.feature_bring),
    /** The place component and « Rejoignez-moi » (PRD, récit 33). */
    Places("places", R.string.feature_places),
    /** « Partager » vers une messagerie, et les liens qui ouvrent l'appli (PRD, récit 31). */
    Share("share", R.string.feature_share),
    /** Les commentaires sous un événement (PRD, récit 28). */
    Comments("comments", R.string.feature_comments),
    /** Les comptes du voyage (PRD rév. 193, récit 16 bis), une brique commune reprise plus tard par les événements. */
    Expenses("expenses", R.string.feature_expenses),
    /** « La semaine de la famille », le récap du dimanche à 18 h (PRD, récit 36). */
    Recap("recap", R.string.feature_recap),
    /** « Il y a un an », la carte souvenir de l'Accueil (PRD, récit 36). */
    YearAgo("yearAgo", R.string.feature_year_ago),
    /** Les vœux d'anniversaire : cartes de vœux le jour J (PRD, récit 36). */
    BirthdayWishes("birthdayWishes", R.string.feature_birthday_wishes),
    /** La bienvenue : la famille est prévenue de l'arrivée d'un membre (PRD, récit 43). */
    Welcome("welcome", R.string.feature_welcome),
    /** Pointure, goûts et passions sur la fiche d'un membre (PRD, récit 7 bis). */
    MemberCard("memberCard", R.string.feature_member_card),
    /** Le coin des petits : l'étiquette « Les petits » et son filtre sur les Bonnes nouvelles (PRD, récit 43). */
    LittleOnes("littleOnes", R.string.feature_little_ones),
    /** « À découvrir » : un module par semaine sur l'Accueil, dès le lundi 30/11 (AVIS-19, PRD récit 29). */
    Discover("discover", R.string.feature_discover),
    /** Le petit geste du jour : une ligne de « À faire pour moi » sur l'Accueil, sans notification (PRD, récit 43). */
    DailyNudge("dailyNudge", R.string.feature_daily_nudge),
    /** « Pensez à moi » : un moment important noté, la famille encourage le jour J (PRD, récit 43). */
    ThinkOfMe("thinkOfMe", R.string.feature_think_of_me),
    /** « Qui est-ce ? » : une vieille photo, la famille devine qui et en quelle année (PRD, récit 43). */
    WhoIsIt("whoIsIt", R.string.feature_who_is_it),
    /**
     * Une seule liste d'envies par personne et « Idées de la famille » à la place des listes d'occasion
     * et des listes surprises (PRD rév. 209, récit 9 ter). Allumé, l'ancien écran des listes disparaît.
     */
    SingleList("singleList", R.string.feature_single_list),
    /** Une photo de couverture sur un événement et une photo sur une envie (PRD, récit 44). */
    MorePhotos("morePhotos", R.string.feature_more_photos),
    /**
     * Le livre de recettes de la famille (PRD, récit 47) : la petite histoire d'une recette, la case
     * « Un enfant est sur la photo », puis « Faire le livre » en PDF A5 dans Recettes.
     */
    RecipeBook("recipeBook", R.string.feature_recipe_book),
    /**
     * La présentation « R1 bis » (spec DESIGN du 17/11) : frise de l'Accueil, Tout Familia, pastilles
     * à deux lettres, motif du thème… Allumée par défaut ; l'éteindre rend l'ancienne présentation.
     */
    PresentationR1bis("presentationR1bis", R.string.feature_presentation_r1bis, onByDefault = true),
    /** Bons plans et bonnes adresses dans Entraide (PRD, récit 45, PRODUIT-60). */
    GoodDeals("goodDeals", R.string.feature_good_deals),
    /** « Voir dans mon agenda » : le planning dans Google Agenda, par un lien secret (PRD, récit 49). */
    CalendarFeed("calendarFeed", R.string.feature_calendar_feed),
    /** On se prête : les objets que la famille se prête, dans Entraide (PRD, récit 51). */
    Lending("lending", R.string.feature_lending),
    /** « On y va ensemble » : le covoiturage d'un événement (PRD, récit 52). */
    Carpool("carpool", R.string.feature_carpool),
    /** « Se répète » : un événement qui revient chaque semaine, chaque mois… (PRD, récit 54). */
    RecurringEvents("recurringEvents", R.string.feature_recurring_events),
    /** L'affiche de l'arbre : un PDF paysage A4 ou A3 de l'arbre généalogique (PRD rév. 243, récit 53). */
    TreePoster("treePoster", R.string.feature_tree_poster),
    /** Le livre photo d'un album : un PDF A4 paysage ou carré d'un voyage ou d'un événement (PRD rév. 253, récit 59). */
    PhotoBook("photoBook", R.string.feature_photo_book),
    /** « Notre année en famille » : le récit de l'année, du 26/12 au 10/01 (PRD rév. 245, récit 55). */
    YearInReview("yearInReview", R.string.feature_year_in_review),
}

/**
 * Which modules the current member sees. A module is on for the family once switched on in
 * Administration; the admin principal always sees every module, to try it first.
 */
data class FeatureFlags(val switchedOn: Map<String, Boolean> = emptyMap(), val isOwner: Boolean = false) {
    fun isOn(feature: Feature): Boolean =
        if (feature.onByDefault) isOnForFamily(feature) else isOwner || switchedOn[feature.key] == true

    /** Switched on for the whole family (what Administration shows). */
    fun isOnForFamily(feature: Feature): Boolean = switchedOn[feature.key] ?: feature.onByDefault
}
