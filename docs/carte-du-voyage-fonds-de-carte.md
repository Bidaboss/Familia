# La carte du voyage : fonds de carte gratuits (récit 87, 21/11/2026)

Note de préparation, sans code. Aujourd'hui la carte est dessinée sur le fond du paysage du voyage, avec les points (lat/lng) des étapes. L'admin veut à terme un fond avec les rues. Voici ce qui existe de gratuit, lu sur les pages officielles le 21/11/2026. « Non indiqué » veut dire que la page lue ne le dit pas : à vérifier avant de choisir.

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

## 4. Mapbox (étude du 22/11/2026, l'admin aime Mapbox pour les voyages)

Étude seulement, aucun code. Sources officielles lues le 22/11/2026 : docs.mapbox.com/android/maps/guides/ (présentation, installation, tarifs, hors ligne, interactions), mapbox.com/pricing et mapbox.com/legal/tos (« Last updated March 31, 2024 »). « Non indiqué » veut dire que la page lue ne le dit pas : à vérifier dans le compte Mapbox avant de s'engager.

### Offre gratuite et facturation

- **Ce qui est compté** : des utilisateurs actifs par mois (« MAU », catégorie « Maps SDKs for Mobile »), pas des requêtes de tuiles. Est actif celui qui voit une carte ou en crée une image. Les tuiles utilisées par le SDK sont comprises dans ce prix.
- **Seuils** (page des prix) : jusqu'à 25 000 MAU par mois, gratuit. De 25 001 à 125 000 : 4 $ pour 1 000. Au-delà, 3,20 $, puis 2,40 $, puis « contact sales ». Les seuils de tuiles (200 000 requêtes de tuiles vectorielles, 750 000 de tuiles raster) sont ceux des API web, pas ceux du SDK.
- **Réinstaller compte en plus** : « Deleting and re-installing an app … will result in an additional MAU ». Le mois de facturation n'est pas le mois civil.
- **Carte bancaire exigée ?** Non indiqué sur les pages lues. **Plafond de dépense ?** Aucun trouvé ; au-delà du gratuit, c'est de la facturation, pas un blocage. Sans plafond ni carte connue, à vérifier à l'inscription et à régler dans le tableau de bord (alerte).
- **Pour 13 personnes** : 13 MAU, loin des 25 000. Même 10 réinstallations chacune par mois ne s'en approchent pas. Coût attendu : 0 €.

### Clé d'accès et dépôt public

- Le SDK lit un jeton **public** (commence par `pk.`) dans une ressource `mapbox_access_token` ou le reçoit au démarrage (`MapboxOptions.accessToken`). Il est fait pour être dans l'APK : il s'y retrouve toujours, quiconque ouvre l'APK peut le lire.
- Un jeton **secret** (`sk.`, droit `downloads:read`) pour télécharger le SDK : la page d'installation lue n'en parle plus, le dépôt Maven est donné sans identifiants. À confirmer sur le premier essai de build.
- **Ranger sans le mettre dans le dépôt public** : comme nos autres secrets, un secret GitHub (ex. `MAPBOX_PUBLIC_TOKEN`) écrit dans un fichier ressource généré au moment de la construction par la CI de l'APK (dans `build/`, ignoré par Git). Les tests de la CI utilisent un faux jeton. Le seul risque réel d'un jeton qui fuit : quelqu'un d'autre fait grimper nos MAU. On limite les droits du jeton au minimum et on peut le changer sans toucher au code.
- Le plafond de dépense n'étant pas connu, c'est le point à trancher avant d'ouvrir la carte à toute la famille.

### Conditions d'utilisation

- Avoir **18 ans** pour ouvrir le compte : l'admin, pas les enfants. Chaque requête porte la clé du compte, qui répond de tout usage.
- La licence « production » est conditionnée au paiement quand elle est exigée ; le texte lu ne dit pas clairement si une appli familiale gratuite en relève. Le seuil gratuit est prévu « for most products » : à confirmer par écrit avec le support avant de compter dessus.
- **Logo et attribution obligatoires** : le mot-symbole Mapbox sur chaque carte, et l'attribution lisible. Elle contient aussi l'opposition à la télémétrie : le contrat oblige à offrir à chaque utilisateur un moyen de la refuser. Pour une appli familiale avec des enfants, la télémétrie (envoi d'usage à Mapbox) est à désactiver par défaut ou à expliquer dans l'écran de confidentialité de Familia.
- Les nouvelles conditions s'appliquent dès leur publication, et ne pas utiliser la dernière version du SDK « may result in … higher fees ».

### Hors ligne

- Possible mais à construire : des « régions de tuiles » à télécharger explicitement (aucune interface fournie), au plus 750 paquets de tuiles uniques en tout, un cache automatique sans garantie de durée. Taille du stock : sans limite par défaut, à plafonner (`diskQuota`).
- **Interdit d'embarquer** des données Mapbox dans l'APK ou de les redistribuer : elles doivent venir des serveurs de Mapbox. Pas de fichier de carte préparé à l'avance comme avec PMTiles.
- Donc : au mieux un téléchargement du voyage avant le départ (par chaque téléphone), jamais un fond livré avec l'appli.

### Poids et versions

- Version annoncée : 11.35.1 sur la présentation, 11.35.2 sur l'installation (les pages ne concordent pas : lire le dépôt Maven avant de fixer). Artefacts `-ndk27` pour les pages mémoire de 16 Ko, et extension Compose `maps-compose` officielle.
- **minSdk** : le SDK demande Android 21 minimum ; nous sommes à **26**, compatible. OpenGL ES 3 requis, Kotlin 1.6 ou plus.
- **Poids dans l'APK** : non indiqué sur les pages lues, à mesurer sur un build (bibliothèques natives par architecture). Le SDK tire aussi Google Play Services pour HTTP/3 (retirable).

### Accessibilité (TalkBack)

Rien trouvé dans les pages lues : ni description des éléments de carte, ni mode lecteur d'écran. Il faut supposer que la carte est un dessin sans arbre d'accessibilité et prévoir, comme pour notre carte dessinée, une **liste des étapes** lisible par TalkBack à côté, et des gestes alternatifs aux gestes tactiles (boutons pour zoomer et passer d'une étape à l'autre, 56 dp). À essayer sur un téléphone avant de promettre quoi que ce soit.

### Comment ça se brancherait chez nous

- Le fond de la carte est déjà un emplacement remplaçable (récit 87) et la projection des points est isolée dans une fonction testée. Un fond Mapbox serait un second composant `MapboxMap` qui reçoit les mêmes étapes (latitude, longitude) et pose ses repères par-dessus ; la carte dessinée reste le composant par défaut.
- **Secours sans réseau** : si le jeton manque (build de test, fork), si le téléphone n'a pas de réseau, ou si la carte met trop de temps à charger, on affiche la carte dessinée, sans message d'erreur. Un interrupteur dans Administration > Modules permettrait de couper Mapbox pour toute la famille sans nouvelle APK.
- Les étapes sans position restent traitées comme aujourd'hui.
- Plus lourd que nos autres choix : une dépendance native, un compte à responsabilité (celui de l'admin), un logo imposé, de la télémétrie à encadrer.

### Recommandation

- **Mapbox est réalisable et gratuit pour 13 personnes** (0 €, 13 MAU contre 25 000 gratuits). Rien n'empêche de s'y brancher techniquement : minSdk, Compose, jeton public rangé par la CI.
- Mais trois points à lever **avant** tout code, par l'admin : (1) la carte bancaire et le plafond de dépense à l'inscription, car sans plafond une fuite de jeton se paie ; (2) la mention de la licence « production » pour un usage familial, par écrit auprès du support ; (3) la télémétrie à désactiver ou à déclarer, avec des enfants dans l'appli.
- **Ordre proposé** : garder la carte dessinée comme base ; si l'admin veut des rues, essayer d'abord Mapbox sur un seul voyage, derrière un interrupteur de module, avec la carte dessinée en secours. Si le point (2) ou le poids de l'APK déçoit, MapLibre avec OpenFreeMap (section 2) donne le même composant sans compte ni jeton, mais sans engagement de durée.
- Le hors ligne complet reste impossible avec Mapbox (pas de données embarquées) ; seule la voie 3 (PMTiles) le permet sans réserve.
