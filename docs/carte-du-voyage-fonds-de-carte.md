# La carte du voyage : fonds de carte gratuits (récit 87, 21/11/2026)

Note de préparation, sans code. Aujourd'hui la carte est dessinée sur le fond du paysage du voyage, avec les points (lat/lng) des étapes. Kévin veut à terme un fond avec les rues. Voici ce qui existe de gratuit, lu sur les pages officielles le 21/11/2026. « Non indiqué » veut dire que la page lue ne le dit pas : à vérifier avant de choisir.

## 1. Tuiles officielles d'OpenStreetMap (serveurs de la fondation)

Pages : politique des tuiles raster (operations.osmfoundation.org/policies/tiles/) et des tuiles vectorielles (…/policies/vector/).

- **Conditions** : pas de clé ni de compte. Pas de distinction commerciale ou non. Il faut une attribution visible (« © OpenStreetMap contributors »), un User-Agent propre à l'appli (les User-Agent par défaut des bibliothèques, comme okhttp, sont bloqués), et respecter le cache du serveur (au moins 7 jours à défaut). Pas de préchargement ni de téléchargement en masse. La fondation peut bloquer sans préavis un usage qui dégrade le service, et il n'y a « no SLA or guarantee ».
- **Réseau** : toujours. « Offline use is not permitted ».
- **Poids ajouté à l'APK** : celui de la bibliothèque de carte (voir la fin). Les tuiles ne sont pas dans l'APK.
- **Pour Familia** : possible pour 13 personnes, en ligne seulement, mais sans garantie. Un fond qui disparaît quand le service nous bloque ou tombe est un risque à accepter.

## 2. OpenFreeMap (tuiles vectorielles libres, openfreemap.org)

Page : conditions d'utilisation (openfreemap.org/tos/, mise à jour le 14/10/2026).

- **Conditions** : service gratuit « as-is, with no warranties », qui peut s'arrêter « at any time without notice ». Il faut avoir 18 ans pour l'intégrer dans une appli. Collecte automatisée interdite, sauf téléchargements de zones hors ligne : au plus 3 000 tuiles ou 50 Mo par téléchargement, 4 requêtes en parallèle, avec un User-Agent unique qui identifie l'appli. Au-delà, il faut écrire à l'équipe. Clé ou compte, attribution, usage commercial, limites de débit : non indiqués sur cette page.
- **Réseau** : oui pour l'usage normal ; hors ligne seulement dans les limites ci-dessus.
- **Poids** : la bibliothèque de carte ; les tuiles ne sont pas dans l'APK.
- **Pour Familia** : la plus simple, gratuite, mais sans engagement de durée.

## 3. Fichier de carte embarqué ou téléchargé (Protomaps, format PMTiles)

Pages : documentation des téléchargements (docs.protomaps.com/basemaps/downloads), FAQ (protomaps.com/faq), exemple MapLibre Android (maplibre.org/maplibre-native/android/examples/data/PMTiles/).

- **Conditions** : fichier unique sous licence ODbL, avec l'attribution OpenStreetMap. Pour le fichier à soi, « no dependence on a third party or API keys ». L'API hébergée de démonstration demande une clé (limite souple de 1 000 000 de tuiles par mois) et, pour un usage commercial, de sponsoriser le projet : ce n'est pas la voie ici. Les adresses de téléchargement peuvent changer, la page déconseille de s'y brancher directement : on copierait le fichier dans notre propre stockage.
- **Réseau** : aucun une fois le fichier sur le téléphone. MapLibre Android sait lire un fichier local à partir de la version 11.7.0.
- **Poids** : la planète entière fait environ 120 Go, chaque niveau de zoom en plus double à peu près la taille. La taille d'un extrait (un pays, l'Europe) n'est pas indiquée sur ces pages : à mesurer avec l'outil d'extraction avant de décider. Un extrait de la seule zone d'un voyage reste le seul moyen d'un poids raisonnable ; il faudrait donc le préparer et le télécharger par voyage, ce qui est un vrai chantier.
- **Pour Familia** : le plus robuste et le plus privé (aucune requête vers un tiers), mais le plus lourd à construire.

## À vérifier avant de choisir (non vérifié ici)

- Le poids ajouté à l'APK par la bibliothèque de carte (MapLibre Android) : à mesurer sur un build, aucune des pages lues ne le donne.
- La taille d'un extrait PMTiles pour la Corse, l'Europe, etc.
- Si le fond doit marcher hors ligne : seule la voie 3 le permet sans réserve.

## Ce qui est déjà prêt dans le code

La carte sera un composant dont le fond se remplace (aujourd'hui le paysage, plus tard un fond de rues), avec la projection des points isolée dans une fonction testée. Les étapes gardent leur latitude et leur longitude, ce qui sert à tous les fonds ci-dessus.
