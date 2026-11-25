# Carte du voyage avec les rues (Mapbox) : l'essai (PRODUIT-147)

L'admin a confirmé son compte (sans carte bancaire, licence pour son usage) et rangé le jeton public. Étude : `docs/carte-du-voyage-fonds-de-carte.md`, section 4.

## Prêt dans le code

- **Interrupteur de module** « Carte du voyage avec les rues » (`Feature.MapStreets`, Administration > Modules). Éteint par défaut. L'admin principal le voit toujours, pour l'essayer. Pas de règle ni de function : pas de « Publier Firebase ».
- **Choix du fond** : `mapGround(streetsOn, token, hasNetwork)` (feature/trips/map/MapGround.kt, testé). Les rues seulement si le module est allumé, le jeton présent dans l'APK et le réseau là. Sinon la carte dessinée sur le paysage du voyage reste, sans message. `TripMap` a déjà un paramètre `background` : le fond de rues viendra s'y brancher, les points et le trait gardent la même projection (`projectToUnit`).
- **Jeton** : `BuildConfig.MAPBOX_PUBLIC_TOKEN`, lu dans le secret GitHub `MAPBOX_PUBLIC_TOKEN` (ou `keystore.properties` en local). Seul le workflow « APK de publication » le reçoit ; les APK de test de la CI sont des artefacts publics et ne l'ont pas. Vide, tout reste comme avant.

## Ce que l'admin fait quand il a vérifié son compte

1. Créer le compte Mapbox, relever la carte bancaire demandée ou non, le plafond de dépense possible, et demander par écrit au support si une appli familiale gratuite relève de la licence « production ».
2. Créer un jeton **public** (`pk.`) avec les droits minimum. Pas de jeton secret.
3. GitHub, Settings, Secrets and variables, Actions : ajouter `MAPBOX_PUBLIC_TOKEN`.
4. Dire au fil [DEV] que c'est fait : le code Mapbox (dépendance, fond de rues, logo, attribution) vient alors dans une PR à part.

## Ce que l'essai fait (PRODUIT-147)

- **Pas de SDK** : le fond est une image de l'API « Static Images » de Mapbox (`mapbox/streets-v12`, 576 × 640 en @2x), demandée pour la zone du voyage. Rien n'est ajouté à l'APK, pas de bibliothèque native, pas de télémétrie du SDK. Les points sont placés par le même calcul (Web Mercator, tuiles de 512) que l'image, donc ils tombent sur leurs rues. Attribution : la page officielle (docs.mapbox.com/help/dive-deeper/attribution/) demande le logo et le texte « © Mapbox © OpenStreetMap Improve this map », avec les trois liens ; pour une image statique, « en texte près de l'image ». La carte les affiche donc sous l'image (trois boutons de 56 dp qui ouvrent les liens). La page ne dit pas ce que fait le paramètre `attribution` de l'API (par défaut, il est actif et dessine une marque dans l'image) : on le laisse actif, et le texte est ajouté en plus.
- **Gratuit** : 50 000 images par mois sans frais (page des prix) ; un voyage ouvert = une image, gardée en cache par l'appli.
- **Repli sans message** : sans jeton, sans réseau, ou si Mapbox répond par un refus (jeton, limite de débit, plafond du compte « demo access » qui coupe toutes les API sans rien facturer), l'image ne vient pas et la carte dessinée sur le paysage du voyage reste sous les points, sans message d'erreur. Le risque d'un plafond atteint est donc une carte qui redevient dessinée, pas un coût.
- **Pas de zoom ni de déplacement** : c'est une image, pas une carte vivante. Le SDK (carte interactive) n'est pas retenu pour l'essai.

## Texte d'encadrement de la télémétrie (à valider par l'admin et [PRODUIT])

Avec l'image statique il n'y a pas de SDK, donc pas de télémétrie à couper ni de réglage à offrir. Ce qui part : la zone du voyage et le jeton public, vers Mapbox, quand le module est allumé. À dire sur l'écran de la carte, sous la carte, quand les rues sont affichées (pas encore dans l'appli, à valider) :

> « Les rues viennent de Mapbox. Pour les afficher, ton téléphone leur envoie la zone du voyage. Familia ne leur envoie pas ton nom, ni tes photos, ni tes messages. »

Et dans « Confidentialité » de Familia, une ligne : « La carte avec les rues (essai) appelle un service extérieur, Mapbox, quand tu l'ouvres. Éteindre le module dans Administration remet la carte dessinée, sans aucun appel. »

À vérifier à l'essai sur un téléphone : que le logo et l'attribution sont lisibles dans l'image.

## Pas dans cette étape

- Le SDK `com.mapbox.maps` (carte interactive) : écarté pour l'essai, voir plus haut.
- Le téléchargement hors ligne d'une région (non retenu pour l'essai : le secours est la carte dessinée).
- La liste des étapes lisible par TalkBack à côté de la carte (à faire avec le fond de rues, voir l'étude).
