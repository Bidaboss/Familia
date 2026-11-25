# Textes de « Quoi de neuf ? »

La feuille « Quoi de neuf ? » s'ouvre à la première ouverture après une mise à jour (PRD, récit 6 septies). Ses textes vivent ici, un fichier par version, écrits par [PRODUIT].

## Comment ça marche

1. Ajoute un fichier `.json` dans ce dossier, dans la même PR que la nouveauté ou juste après. Nomme-le par la date à partir de laquelle il peut partir, par exemple `2026-11-28.json`. Un fichier daté d'un jour à venir attend : la publication qui le prend est la première faite ce jour-là ou après (heure de Paris). Chaque version a ainsi ses propres cartes et son propre numéro.
2. L'admin lance « APK de publication » comme d'habitude. Le workflow donne au nouveau fichier le numéro de la version publiée, puis il écrit tout l'historique dans `config/app.whatsNew`. L'admin n'a aucun geste de plus à faire.
3. Un fichier déjà publié garde son numéro de version. Tu peux corriger une faute dedans : la correction part à la publication suivante.

On ne connaît pas le numéro de version à l'avance, parce que c'est le nombre de commits de main. Le nom du fichier sert donc seulement à le reconnaître d'une publication à l'autre.

Une version sans fichier n'affiche rien.

## Format

```json
{
  "cards": [
    {
      "id": "recettes-photo",
      "title": "Ta photo sur la recette",
      "body": "Ajoute la photo de ton plat, la famille verra à quoi il ressemble.",
      "childTitle": "Montre ton plat",
      "childBody": "Prends ton plat en photo et ajoute-la à la recette.",
      "feature": "recipes",
      "open": "recipes",
      "adultsOnly": false
    }
  ]
}
```

Chaque fichier contient de 1 à 3 cartes. Les champs :

- `id` (obligatoire) : unique dans tout le dossier, en minuscules, chiffres et tirets. Le téléphone s'en sert pour retenir les cartes déjà vues.
- `title` (obligatoire) : 40 caractères au plus.
- `body` (obligatoire) : une seule phrase, en tutoiement, 140 caractères au plus.
- `childTitle` et `childBody` (facultatifs) : la variante pour un enfant. Sans elle, l'enfant lit le texte des adultes.
- `feature` (facultatif) : la clé de l'interrupteur du module, par exemple `recipes` (voir `Feature.kt`). La carte attend tant que le module est éteint pour la famille. Elle apparaît à l'ouverture suivante quand l'admin l'allume, et « À découvrir » saute ensuite ce module.
- `open` (facultatif) : l'écran qu'ouvre « Essayer ». Sans lui, la carte n'a pas de bouton. Les valeurs possibles sont `home`, `planning`, `gifts`, `helpRequests`, `members`, `recipes`, `trips`, `polls`, `capsules`, `genealogy`, `weeklyQuestion`, `goodNews`, `joinMe`, `appearance` et `whoIsIt`. Elles donnent aussi l'illustration de la carte.
- `adultsOnly` (facultatif) : `true` pour ne pas montrer la carte aux enfants.

## Ce que voit le membre

- Il voit 3 cartes au plus pour une version, et 5 au plus s'il a sauté des versions, les plus récentes d'abord.
- Rien ne s'affiche à la première installation, où le tuto prend la place, ni en « Voir comme ». Aucune notification n'est envoyée.
- « C'est parti » et « Essayer » ferment la feuille pour de bon. « Plus tard », ou la feuille glissée vers le bas, la fait revenir une seule fois, à l'ouverture suivante. Tout l'historique se revoit depuis l'aide et le menu de l'avatar.
- Tous les modules sont allumés le 21/11 (DESIGN-10) : « feature » sert surtout pour un module ajouté plus tard.

Un test vérifie chaque fichier à chaque PR (`firebase/tests/whatsNew.test.mjs`). Le workflow refuse aussi de publier si un fichier a un problème, avant de toucher au Drive.

## Corrections (automatique)

À chaque « APK de publication », le workflow ajoute aussi une carte « Corrections » pour cette version, sans fichier à écrire : la liste des PR fusionnées depuis la version précédente (leur titre, sans le numéro ni les références comme « (PRODUIT-12) »), six lignes au plus. L'appli la dessine à part des nouveautés et la montre après elles. Le dernier commit publié est gardé dans `config/app.latestCommit` ; la première fois, ce sont les commits des dernières 24 heures. Un titre de PR est donc lu par la famille : l'écrire en français simple, sans prénom.
