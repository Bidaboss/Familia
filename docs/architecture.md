# Architecture de Familia

Ce document décrit comment le code est rangé. Il grandit avec chaque lot du plan de développement.

## Rangement du code

Un seul module Gradle, `app`, rangé par fonctionnalité :

```
app/src/main/java/fr/familia/app/
├── MainActivity.kt          point d'entrée, applique le thème et lance FamiliaApp
├── core/
│   ├── navigation/          barre du bas et NavHost unique (routes typées @Serializable)
│   ├── theme/               thème Material 3 fixe terracotta #B5541C, clair et sombre, texte relevé
│   └── ui/                  composants partagés (EmptyState, annotation d'aperçus FamiliaPreviews)
└── feature/
    ├── planning/            onglet Planning
    ├── trips/               voyages (V3), sans onglet : ouverts depuis le planning
    ├── birthdays/           onglet Anniversaires
    └── members/             onglet Famille (membres, réglages, version)
```

## Règles suivies

- **MVVM** : à partir du lot 2, chaque écran a un `ViewModel` qui expose un `StateFlow<UiState>` (`Loading | Success | Error`). L'écran ne parle jamais à Firebase directement : il passe par un `Repository`.
- **Injection de dépendances manuelle** (pas de Hilt) : un objet `AppContainer` créé dans `FamiliaApplication` fournit les repositories. Choix tenu pour tout le projet, plus facile à lire pour un débutant.
- **Design** : les [principes de design Familia](https://claude.ai/artifact/YV4g9YiKNhYhmN2Dx7WzCs) priment sur la skill générique (pas de couleurs dynamiques, texte à 16 sp minimum, zones tactiles de 56 dp, aperçus clair, sombre et police 200 %).
- **Thème** : couleurs lues dans `MaterialTheme.colorScheme`, styles dans `MaterialTheme.typography`, textes dans `strings.xml`. Rien en dur dans les écrans.
- **Versions** : toutes dans `gradle/libs.versions.toml`, Compose via sa BOM.
- **Code et commentaires en anglais, interface en français.**

## Partager (`core/share`, récit 31)

Chaque fil branche « Partager » sur ses écrans avec les mêmes briques :

- **Contenu** : une fonction `xxxShare(context, item): ShareContent` dans `ShareItems.kt`, ou dans le module du fil si elle a besoin de son modèle. Elle donne un titre, une ou deux lignes pour l'image, un texte qui se lit seul et le lien. Rien de caché ne sort : ni « qui vient », ni note, ni réponse de sondage, ni réservation ou cadeau.
- **Lien** : `shareLink(ShareKind.Xxx, id)`. Un nouveau `ShareKind` demande trois choses : son `path`, qui ne change plus une fois partagé ; sa porte dans `MainActivity.openSharedLink` ; et la page `firebase/hosting/open.html`, qui ne montre aucune donnée de la famille et **jamais** le lien d'installation.
- **Photo** : seulement si `photoAllowed(sharerId, …)` l'autorise (carte EXPERT-2, photo d'un enfant). Une vidéo ou un diaporama passent par `ShareConsentReminder`, puis `shareVideo` (carte EXPERT-6).
- **Bouton** : `share(context, content)` ouvre la feuille d'Android, ou dit qu'il n'y a pas d'appli. `ShareButton` fait 56 dp. Dans une liste, le libellé nomme l'élément (par exemple « Partager l'anniversaire de … »).
- **Interrupteur** : le bouton ne se montre que si `Feature.Share` est allumé (`LocalFeatureFlags`), et jamais en « Voir comme » s'il écrit quelque chose.

Déjà branchés : événement, sondage, anniversaire (Planning et liste des anniversaires), voyage. Les listes, Bonnes nouvelles et Coups de main sont faits par leurs fils.

## Services externes

Les services lus par le téléphone, en dehors de Firebase. Aucun ne reçoit le nom de la famille ni d'un membre : seulement un lieu ou un point, et un User-Agent générique (`Familia/1.0 (application familiale Android)`, `WIKIMEDIA_USER_AGENT`).

| Service | Adresse | Pour quoi | Fichier |
|---|---|---|---|
| Mapbox | api.mapbox.com | Cartes et globe du voyage (jeton public) | `feature/trips/map` |
| Géocodeur d'Android | (service du téléphone) | Trouver un lieu tapé | `core/place/PlaceLocator.kt` |
| Wikipédia et Wikimedia Commons | fr.wikipedia.org, commons.wikimedia.org, upload.wikimedia.org | Photo d'un lieu, sa licence, l'anecdote d'une destination | `core/place/PlacePhotos.kt` |
| Wikivoyage | fr.wikivoyage.org | « À savoir sur… » | `core/place/PlaceArticles.kt` |
| Overpass (OpenStreetMap) | overpass-api.de, overpass.private.coffee, overpass.kumi.systems (essayés dans cet ordre) | Lieux autour d'une étape, recherche de lieux | `core/place/PlaceSearch.kt`, `core/place/NearbyPlaces.kt` |
| Open-Meteo | api.open-meteo.com | Météo d'une étape | `core/place/PlaceWeather.kt` |
| ExchangeRate-API | open.er-api.com | Taux de change des dépenses | `core/money/ExchangeRates.kt` |

Les images de Wikimedia sont chargées par Coil avec ce même User-Agent (intercepteur de `FamiliaApplication`), et seulement aux largeurs que Commons sert (`WIKIMEDIA_WIDTHS`, 960 px pour une photo de lieu).

## Compilation

- La CI GitHub (`.github/workflows/android.yml`) compile l'APK de test, lance le lint Android et les tests unitaires à chaque pull request. L'APK est téléchargeable dans l'onglet « Actions » de la PR, rubrique « Artifacts ».
- Des captures d'écran clair et sombre sont produites sans téléphone par Roborazzi (`app/src/test/.../screenshots`) et publiées dans l'artefact « captures-ecrans ».
- Deux clés de signature stables (test et publication), lues dans des variables d'environnement (secrets GitHub) ou un fichier local `keystore.properties` ignoré par git. Aucune clé n'entre dans le dépôt. Voir `docs/firebase-setup.md`.
- **Connexion** (lot 2) : `feature/auth` contient `SessionViewModel` (qui expose `SessionUiState`), `AuthRepository` (Firebase Auth) et `GoogleCredentialProvider` (sélecteur de compte Google d'Android). `core/data/AppContainer` construit les repositories ; ils valent `null` tant que `google-services.json` manque, et l'écran le dit.
