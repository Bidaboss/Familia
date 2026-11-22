# Carte du voyage avec les rues (Mapbox) : ce qui est prêt, ce qui attend (PRODUIT-147)

Aucun code Mapbox n'est écrit. Il attend la confirmation de Kévin (carte bancaire, plafond de dépense, licence) et son jeton public. Étude : `docs/carte-du-voyage-fonds-de-carte.md`, section 4.

## Prêt dans le code

- **Interrupteur de module** « Carte du voyage avec les rues » (`Feature.MapStreets`, Administration > Modules). Éteint par défaut. L'admin principal le voit toujours, pour l'essayer. Pas de règle ni de function : pas de « Publier Firebase ».
- **Choix du fond** : `mapGround(streetsOn, token, hasNetwork)` (feature/trips/map/MapGround.kt, testé). Les rues seulement si le module est allumé, le jeton présent dans l'APK et le réseau là. Sinon la carte dessinée sur le paysage du voyage reste, sans message. `TripMap` a déjà un paramètre `background` : le fond de rues viendra s'y brancher, les points et le trait gardent la même projection (`projectToUnit`).
- **Jeton** : `BuildConfig.MAPBOX_PUBLIC_TOKEN`, lu dans le secret GitHub `MAPBOX_PUBLIC_TOKEN` (ou `keystore.properties` en local). Seul le workflow « APK de publication » le reçoit ; les APK de test de la CI sont des artefacts publics et ne l'ont pas. Vide, tout reste comme avant.

## Ce que Kévin fait quand il a vérifié son compte

1. Créer le compte Mapbox, relever la carte bancaire demandée ou non, le plafond de dépense possible, et demander par écrit au support si une appli familiale gratuite relève de la licence « production ».
2. Créer un jeton **public** (`pk.`) avec les droits minimum. Pas de jeton secret.
3. GitHub, Settings, Secrets and variables, Actions : ajouter `MAPBOX_PUBLIC_TOKEN`.
4. Dire au fil [DEV] que c'est fait : le code Mapbox (dépendance, fond de rues, logo, attribution) vient alors dans une PR à part.

## Texte d'encadrement de la télémétrie (à valider par Kévin et [PRODUIT])

Le contrat de Mapbox oblige à offrir à chacun un moyen de refuser la télémétrie, fourni avec le bouton d'attribution de la carte. Familia l'éteint par défaut et le dit sur l'écran de la carte, sous la carte, à chaque fois que les rues sont affichées :

> « Les rues viennent de Mapbox. Pour les afficher, ton téléphone leur envoie la zone regardée et sa langue. Familia ne leur envoie pas ton nom, ni tes photos, ni tes messages. L'envoi des statistiques d'usage de la carte est coupé. »

Et dans « Confidentialité » de Familia, une ligne : « La carte avec les rues (essai) appelle un service extérieur, Mapbox, quand tu l'ouvres. Éteindre le module dans Administration remet la carte dessinée, sans aucun appel. »

À vérifier au moment du code, sur le SDK : que la télémétrie est bien coupée par réglage, et que le bouton d'attribution reste visible (obligatoire).

## Pas dans cette étape

- La dépendance `com.mapbox.maps` et tout composant de carte Mapbox.
- Le téléchargement hors ligne d'une région (non retenu pour l'essai : le secours est la carte dessinée).
- La liste des étapes lisible par TalkBack à côté de la carte (à faire avec le fond de rues, voir l'étude).
