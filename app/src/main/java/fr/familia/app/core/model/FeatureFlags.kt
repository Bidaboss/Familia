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

    /** « Mon budget » (PRD, récit 57): a budget for a whole occasion in the notebook, adults only. */
    GiftBudget("giftBudget", R.string.feature_gift_budget),
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
    /** « Qui est-ce ? » : une vieille photo, la famille devine qui et en quelle année (PRD, récit 43). */
    WhoIsIt("whoIsIt", R.string.feature_who_is_it),
    /**
     * Une seule liste d'envies par personne et « Idées de la famille » à la place des listes d'occasion
     * et des listes surprises (PRD rév. 209, récit 9 ter). Allumé, l'ancien écran des listes disparaît, et le
     * serveur efface les anciennes listes copiées en entier (PRODUIT-58, singleList.ts).
     */
    SingleList("singleList", R.string.feature_single_list),
    /**
     * « Préparer une surprise » depuis un seul bouton de Cadeaux, et « Surprises en préparation » en
     * haut de l'onglet (PRD rév. 287, récit 63). Lu avec isOnForFamily : éteint, même l'admin principal
     * garde l'écran d'avant.
     */
    SurpriseButton("surpriseButton", R.string.feature_surprise_button),
    /** Une photo de couverture sur un événement et une photo sur une envie (PRD, récit 44). */
    MorePhotos("morePhotos", R.string.feature_more_photos),
    /**
     * Le livre de recettes de la famille (PRD, récit 47) : la petite histoire d'une recette, la case
     * « Un enfant est sur la photo », puis « Faire le livre » en PDF A5 dans Recettes.
     */
    RecipeBook("recipeBook", R.string.feature_recipe_book),
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

    /** L'arbre de vie (spec DESIGN du 18/11): the tree drawn as a tree, the rows kept in « Liste ». */
    ArbreDeVie("arbreDeVie", R.string.feature_arbre_de_vie),
    /** Le livre photo d'un album : un PDF A4 paysage ou carré d'un voyage ou d'un événement (PRD rév. 253, récit 59). */
    PhotoBook("photoBook", R.string.feature_photo_book),
    /** « Notre année en famille » : le récit de l'année, du 26/12 au 10/01 (PRD rév. 245, récit 55). */
    YearInReview("yearInReview", R.string.feature_year_in_review),
    /** « Me le rappeler » : mes rappels privés sur un événement, et des rappels par défaut (PRD rév. 248, récit 56). */
    MyReminders("myReminders", R.string.feature_my_reminders),
    /** « Mes premiers pas » : trois gestes sur l'Accueil pendant les 14 premiers jours d'un membre (PRD rév. 260, récit 61). */
    FirstSteps("firstSteps", R.string.feature_first_steps),
    /** « À emporter » dans un voyage : « Pour tout le monde » et « Ma valise » (PRD rév. 263, récit 62). */
    PackingList("packingList", R.string.feature_packing_list),
    /**
     * L'animation de bienvenue (spec DESIGN du 17/11) : à la première ouverture sur un téléphone, les
     * fruits de l'arbre deviennent la famille. « Revoir l'animation de bienvenue » dans Apparence.
     */
    OpeningAnimation("openingAnimation", R.string.feature_opening_animation),
    /**
     * L'onglet Famille « par foyer » (PRD récit 64, Kévin le 18/11) : ma carte, mon foyer, puis les
     * autres foyers repliés. Lu avec isOnForFamily : éteint, même l'admin principal garde l'ancien onglet.
     */
    FamilyTabV2("familyTabV2", R.string.feature_family_tab_v2),
    /**
     * « Comment ça marche » sous le défi du mois, et le mot facultatif du foyer qui le lance (PRD
     * récit 65, rév. 289). Lu avec isOnForFamily : éteint, même l'admin principal garde l'écran d'avant.
     */
    ChallengeDetails("challengeDetails", R.string.feature_challenge_details),
    /**
     * « Déjà acheté, participation bienvenue » sur une envie (PRD rév. 309, récit 68) : plus de « Je le
     * prends », seulement « Je participe » et une jauge. Lu avec isOnForFamily : éteint, même l'admin
     * principal ne voit pas la case.
     */
    AlreadyBought("alreadyBought", R.string.feature_already_bought),
    /**
     * « Envoyer une notification » dans Administration (PRD récit 69, PRODUIT-111) : l'admin principal
     * écrit à la famille, sans réponse. Lu avec isOnForFamily : éteint, même l'admin principal ne voit
     * pas l'entrée, et le serveur refuse l'envoi.
     */
    AdminAnnouncements("adminAnnouncements", R.string.feature_admin_announcements),
    /**
     * Le fil d'actualité de l'Accueil (PRD récit 67) : « Aujourd'hui » | « Le fil » sous « Bonjour ».
     * Lu avec isOnForFamily : éteint, l'Accueil ne change pas, même pour l'admin principal.
     */
    NewsFeed("newsFeed", R.string.feature_news_feed),
    /**
     * Le résumé du soir des notifications (PRD récit 70, DESIGN-31) : réactions, commentaires,
     * question de la semaine et souvenirs partent groupés à 19 h ; chacun peut garder un type
     * immédiat. Lu avec isOnForFamily côté serveur et dans les réglages : éteint, rien ne change.
     */
    EveningDigest("eveningDigest", R.string.feature_evening_digest),

    /**
     * Le fond de rues de la carte du voyage (PRODUIT-147, essai de Mapbox). Éteint, la carte reste
     * celle dessinée sur le paysage du voyage, comme sans réseau ou sans jeton. Aucune règle ni function.
     */
    MapStreets("mapStreets", R.string.feature_map_streets),

    /**
     * Les liens d'idées sur la carte du voyage (PRODUIT-149, récit 96) : « Restaurants près de… »,
     * « Hôtels à… », « Voir sur Tripadvisor ». Éteint, aucun lien. Aucune règle ni function.
     */
    PlaceLinks("placeLinks", R.string.feature_place_links),

    /**
     * Le globe animé de la carte du voyage (PRODUIT-148, récit 94) : « Lancer le voyage », la caméra
     * vole d'étape en étape. Éteint, rien ne change : la carte reste à plat. Aucune règle ni function.
     */
    MapGlobe("mapGlobe", R.string.feature_map_globe),

    /**
     * La création d'un voyage « destination d'abord » (PRODUIT-150, récit 95) : la question « Où
     * allez-vous ? », le nom pré-rempli, puis la première étape. Éteint, le formulaire d'avant.
     * Aucune règle ni function.
     */
    TripDestinationFirst("tripDestinationFirst", R.string.feature_trip_destination_first),

    /**
     * Le compte à rebours du voyage en nuits (PRODUIT-150, récit 95) : « 4 nuits avant le départ »,
     * « Demain », « Jour 2 sur 6 ». Éteint, rien ne s'affiche. Aucune règle ni function.
     */
    TripCountdown("tripCountdown", R.string.feature_trip_countdown),
}

/**
 * Which modules the current member sees. A module is on for the family once switched on in
 * Administration; the admin principal always sees every module, to try it first.
 */
data class FeatureFlags(val switchedOn: Map<String, Boolean> = emptyMap(), val isOwner: Boolean = false) {
    fun isOn(feature: Feature): Boolean = when {
        feature.tier == FeatureTier.Base -> true
        feature.onByDefault -> isOnForFamily(feature)
        else -> isOwner || switchedOn[feature.key] == true
    }

    /**
     * Switched on for the whole family (what Administration shows). « La base » is always on and has
     * no switch any more (Kévin le 18/11, 20 h 39).
     */
    fun isOnForFamily(feature: Feature): Boolean =
        feature.tier == FeatureTier.Base || (switchedOn[feature.key] ?: feature.onByDefault)
}
