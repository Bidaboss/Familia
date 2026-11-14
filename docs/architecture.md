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
    ├── birthdays/           onglet Anniversaires
    └── members/             onglet Famille (membres, réglages, version)
```

## Règles suivies

- **MVVM** : à partir du lot 2, chaque écran a un `ViewModel` qui expose un `StateFlow<UiState>` (`Loading | Success | Error`). L'écran ne parle jamais à Firebase directement : il passe par un `Repository`.
- **Injection de dépendances manuelle** (pas de Hilt) : un objet `AppContainer` créé dans l'`Application` fournira les repositories. Choix tenu pour tout le projet, plus facile à lire pour un débutant.
- **Design** : les [principes de design Familia](https://claude.ai/artifact/YV4g9YiKNhYhmN2Dx7WzCs) priment sur la skill générique (pas de couleurs dynamiques, texte à 16 sp minimum, zones tactiles de 56 dp, aperçus clair, sombre et police 200 %).
- **Thème** : couleurs lues dans `MaterialTheme.colorScheme`, styles dans `MaterialTheme.typography`, textes dans `strings.xml`. Rien en dur dans les écrans.
- **Versions** : toutes dans `gradle/libs.versions.toml`, Compose via sa BOM.
- **Code et commentaires en anglais, interface en français.**

## Compilation

- La CI GitHub (`.github/workflows/android.yml`) compile l'APK de test, lance le lint Android et les tests unitaires à chaque pull request. L'APK est téléchargeable dans l'onglet « Actions » de la PR, rubrique « Artifacts ».
- Des captures d'écran clair et sombre sont produites sans téléphone par Roborazzi (`app/src/test/.../screenshots`) et publiées dans l'artefact « captures-ecrans ».
- La signature de l'APK de publication lit ses informations dans des variables d'environnement (secrets GitHub) ou dans un fichier local `keystore.properties` ignoré par git. Aucune clé n'entre dans le dépôt.
