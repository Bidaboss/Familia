# La carte du voyage façon Polarsteps : le globe animé (étude du 22/11/2026)

Étude seulement, aucun code. Kévin a choisi la variante A de la maquette (DESIGN-63, `design/voyages/spec-carte-globe.md`) : on entre sur un globe, la caméra vole d'étape en étape, le trajet se trace, on finit sur la carte à plat avec une feuille par étape, et une liste « Le trajet » pour TalkBack et « réduire les animations ». Sources : pages officielles du SDK Maps pour Android (docs.mapbox.com/android/maps, lues le 22/11/2026). « Non indiqué » veut dire que la page lue ne le dit pas : à mesurer ou vérifier avant de s'engager, rien n'est inventé ici.

## 1. Le SDK Maps pour Android avec le globe

- **Version** : 11.35.2 sur la page d'installation, 11.35.1 sur la présentation (les pages ne concordent pas : lire le dépôt Maven avant de fixer). Artefact `com.mapbox.maps:android-ndk27` (pages mémoire de 16 Ko), extension Compose `maps-compose-ndk27`. Dépôt Maven `api.mapbox.com/downloads/v2/releases/maven`, sans identifiants sur la page d'installation (à confirmer au premier build).
- **minSdk** : 21 requis, nous sommes à 26 : compatible. OpenGL ES 3, Kotlin 1.6 ou plus.
- **Globe** : projections `globe` et `mercator` (depuis la 11.32). Avec le style **Standard**, le globe est la valeur par défaut ; avec les autres styles (dont Streets v12), la carte est en Mercator. On peut forcer : `style.setProjection(projection(ProjectionName.GLOBE))`. Quand on zoome, la carte passe du globe au Mercator (le niveau de zoom n'est pas indiqué). Le globe montre une hémisphère à la fois ; l'exemple officiel « rotating globe » le fait tourner avec `easeTo` (ZOOM 1,5, un tour en 120 s, il s'arrête au zoom 5). Non indiqué : si `streets-v12` s'affiche bien en globe forcé, à essayer.
- **Poids dans l'APK** : non indiqué sur les pages. À mesurer sur un build (bibliothèques natives par architecture). Leviers : ne garder que les architectures utiles (`abiFilters`, tous les téléphones de la famille sont des Android récents), et ne pas ajouter `cronet-embedded` (ça augmente l'APK ; on peut aussi exclure la dépendance à Google Play Services, HTTP/2 au lieu de HTTP/3).
- **Réseau** : indispensable pour les tuiles. Un cache disque existe (`map_data.db`, sans garantie de durée) et des régions hors ligne se téléchargent à la main (750 paquets de tuiles au plus, aucune interface fournie). Sans réseau, on retombe sur la carte dessinée (§4).
- **Télémétrie** : la page de Mapbox dit que les SDK collectent « device location and select related data », dépersonnalisées, et que l'appli doit permettre à chacun de la refuser (l'opposition est fournie avec le bouton d'attribution). **Le nom de l'interface Android pour la couper n'est dans aucune page lue.** Avec des enfants dans l'appli, c'est une **condition d'entrée** : pas de code livré tant qu'on n'a pas vérifié, sur le SDK lui-même, que la collecte se coupe par réglage, et qu'elle est coupée par défaut chez nous. Si ce n'est pas possible, on s'en tient à l'image statique (déjà en ligne, sans SDK, sans télémétrie).
- **Comptage et plafond** : facturation par utilisateurs actifs par mois : 13 chez nous contre 25 000 gratuits, mais une réinstallation compte pour un de plus. Pour le compte « demo access » sans carte, Kévin a lu qu'au plafond toutes les API s'arrêtent sans facturation ; les chiffres ne sont sur aucune page vue (à lire dans la console). Le risque est une carte qui cesse de répondre, pas un coût. D'où le repli du §4.

## 2. Le survol : la caméra, le trait, le coût sur un vieux téléphone

- **Caméra** : `flyTo` (zoom et déplacement combinés) ou `easeTo`, avec durée et courbe ; un seul mouvement de haut niveau à la fois (un nouveau annule le précédent) ; un écouteur de fin permet d'enchaîner l'étape suivante. L'extension Compose ne gère pas les animations de bas niveau : le survol se ferait avec une `MapView` classique dans `AndroidView`, entourée de nos boutons en Compose.
- **Le trait qui se trace** : `PolylineAnnotation` en Compose ou `createPolylineAnnotationManager` en vue classique. Pas d'API d'animation de ligne trouvée ; l'exemple officiel « animate a line » refait la ligne 200 fois, toutes les 50 ms (avec la bibliothèque Turf). Pour nous : un tronçon plein par étape déjà passée (au plus quelques dizaines de segments), et le tronçon en cours qui s'allonge en 20 à 30 pas pendant le vol ; le pointillé « devant » est un seul trait fixe.
- **Coût** : le style Standard (immeubles en 3D, éclairage) est le plus lourd ; pour un survol, `streets-v12` forcé en globe, sans pente au-delà de 45°, est plus léger. La documentation donne des outils de mesure (`PerformanceStatistics`, expérimental, et `MapboxTracing`, sortie vers Perfetto) mais aucun chiffre pour un vieux téléphone : **il faut mesurer sur le plus ancien téléphone de la famille**. Décision proposée : mesurer les images perdues sur les 3 premières secondes du premier vol et, si c'est mauvais, descendre d'un cran (§4) et retenir le choix sur ce téléphone.
- **Jamais seul** : le survol ne se lance qu'à la demande (« Lancer le voyage »), comme dans la maquette.

## 3. TalkBack et « réduire les animations »

- **Détection** : `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (réglage « supprimer les animations » d'Android) ou TalkBack en route (`AccessibilityManager.isTouchExplorationEnabled`). Dans les deux cas : ni globe, ni vol, on ouvre « Le trajet » (A4).
- **L'équivalent** : une liste Compose pure, sans Mapbox, bâtie à partir des étapes, des mots et des photos qu'on a déjà : « Étape 4 sur 5, La Samoure, 14 avril, 1 mot, 3 photos », l'étape choisie se déplie. Elle sert aussi à tout le monde depuis la fin du survol (« Le trajet en liste »). C'est la première brique à livrer, elle ne dépend de rien.
- **La carte elle-même** : une `MapView` n'est pas accessible à TalkBack. On la cache aux lecteurs d'écran avec une description d'ensemble (« carte du trajet, 5 étapes, de Lyon à l'île d'Oléron ») ; Précédent, Pause, Suivant et « Voir la carte » sont de vrais boutons Compose de 56 dp.
- Ce que dit la documentation sur l'accessibilité de la carte du SDK : rien. À essayer avec TalkBack sur un téléphone.

## 4. Les replis : on ne perd jamais la carte

Échelle, du plus riche au plus simple. On descend d'un cran quand quelque chose manque (module, jeton, réseau, plafond atteint, téléphone lent) :

1. **Globe** (SDK, A) : le survol complet.
2. **Carte à plat inclinée** (SDK, variante B de la maquette) : même fin, même liste, sans globe.
3. **Diaporama d'images statiques** (sans SDK) : une image des rues par étape (l'API déjà en ligne, #481, une requête par étape), fondu entre elles, trait dessiné par nous avec le calcul `projectStreets`. Pas de globe ni de vol, mais un survol correct et gratuit. À proposer si le SDK s'avère trop lourd.
4. **Carte dessinée sur le paysage** (la base) : sans jeton, sans réseau ou quand Mapbox refuse.

Le choix passe par `mapGround` (déjà en place, étendu à plusieurs niveaux) et par un interrupteur de module à part pour le globe : l'éteindre ramène tout le monde au niveau 3 sans nouvelle APK. Le niveau retenu sur un téléphone lent est gardé dans les préférences de l'appli, pour ne pas réessayer à chaque fois.

## Recommandation

- **Faisable, gratuit pour 13 personnes**, mais en gardant deux portes de sortie mesurées avant d'avancer : le poids de l'APK et la télémétrie coupable par réglage. Si l'un des deux échoue, on reste sur l'image statique (déjà livrée) et on fait le niveau 3.
- Le survol est un affichage : aucune donnée, règle ni function nouvelle, donc **pas de « Publier Firebase »**. Le secret `MAPBOX_PUBLIC_TOKEN` est déjà rangé.
- **Coût attendu** : 0 € (13 utilisateurs actifs contre 25 000 gratuits ; 50 000 images statiques par mois gratuites pour le niveau 3). Seul risque : le plafond du compte « demo », qui coupe sans facturer.

## Découpage en PR (une à la fois)

1. **« Le trajet » en liste + le choix du mode** : la liste Compose pour TalkBack et « réduire les animations », la détection, l'écran de fin à plat avec la feuille par étape (mot, photos). Sans SDK, livrable seul, utile à tous. Gestes pour Kévin : ouvrir un voyage avec TalkBack ou avec les animations coupées.
2. **Le moteur du survol, sans SDK** : suite d'images clés (caméra, durée, trait) calculée à partir des étapes, commandes Précédent / Pause / Suivant / Passer, entièrement testée sur ordinateur.
3. **Essai du SDK** (porte de sortie) : dépendance, `abiFilters`, télémétrie coupée, un globe fixe derrière un interrupteur de module éteint ; la CI donne la taille de l'APK avant et après, et on lit le nom exact de l'opposition à la télémétrie. Si ce PR est refusé, on s'arrête là.
4. **Le vol** : la caméra et le trait, brancher le moteur de la PR 2 sur la `MapView`, globe seulement quand le module est allumé.
5. **Mesure et repli automatique** : images perdues sur les premières secondes, descente d'un cran, mémorisation par téléphone, niveau 3 (diaporama).

Questions restantes pour Kévin : survol à la demande seulement (recommandé) ; basculer seul vers un niveau plus simple si le téléphone est lent (recommandé) ; accepter qu'une partie de l'APK grandisse si la mesure du PR 3 le justifie (le seuil à fixer après mesure).
