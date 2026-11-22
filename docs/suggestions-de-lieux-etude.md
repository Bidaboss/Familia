# Suggestions de lieux à visiter, restaurants et hôtels selon la destination (étude du 22/11/2026)

Étude seulement, aucun code. Kévin veut, comme Polarsteps, des idées de lieux à visiter, de restaurants et d'hôtels une fois la destination choisie. Sources : pages officielles lues le 22/11/2026. « Non lu » veut dire que la page n'a pas pu être lue (bloquée, introuvable ou tronquée) : rien n'est supposé, à vérifier avant de s'engager. Principe de départ de Kévin : on n'enregistre rien chez nous sans autorisation, et on préfère un lien « Ouvrir sur… » à une copie du contenu.

## Les options, une par une

### Tripadvisor (API « Content API »)
- **Ce qui est lu** : paiement à l'usage (« Pay only for what you use », « Pay monthly, cancel anytime »), jusqu'à 50 appels par seconde, un plafond quotidien réglable. Cinq services : recherche de lieu, recherche à proximité, détails (nom, adresse, note, liens vers la fiche Tripadvisor), photos et avis (jusqu'à 5 de chaque par lieu).
- **Non lu** : gratuité et quota mensuel, carte bancaire exigée ou non, règles d'affichage et d'attribution, durée de stockage permise, publicité. La page des tarifs est bloquée aux outils automatiques ; l'ancienne page dit seulement qu'elle est périmée et renvoie à un nouveau portail (docs.terra.tripadvisor.com) non lu.
- **Pour nous** : on ne peut pas affirmer que c'est gratuit sans carte. Qualité très bonne pour restaurants et hôtels dans le monde. Vie privée : la zone cherchée part chez Tripadvisor. **Pas retenu** tant que gratuité, carte et stockage ne sont pas confirmés ; en attendant, un lien vers Tripadvisor (non lu : les conditions de lien sortant, mais un simple lien web n'envoie rien).

### Google Maps
- **Lien d'ouverture sans clé** (lu) : `https://www.google.com/maps/search/?api=1&query=...` (et `query_place_id` en option). « You don't need a Google API key to use Maps URLs. » 2 048 caractères au plus. Android l'ouvre dans l'appli Google Maps si elle est installée.
- **API Places** : facturation à activer sur le projet (« you must enable billing on each of your projects »), le crédit mensuel cité par la page se terminait en février 2025. **Écarté** : exige un compte de facturation.
- **Pour nous** : un lien « Restaurants près de Lyon » ou « Hôtels à Lyon » qui ouvre Google Maps, rien de copié, rien d'enregistré, rien de plus dans l'APK, une phrase qui part chez Google au moment où on touche le lien (un navigateur, pas Familia).

### Wikivoyage et Wikipédia (textes sur la destination)
- **Lu** : les interfaces de lecture n'ont « pas de limite dure » mais demandent des requêtes en série et un User-Agent descriptif ; la recherche autour d'un point (`geosearch`) existe sur toutes les Wikipédias et Wikivoyage ; licence **CC BY-SA 4.0** : citer l'auteur, le lien vers la licence et indiquer les changements ; le texte repris sous cette licence doit rester sous la même (partage à l'identique).
- **Non lu** : le résumé d'une page (`/api/rest_v1/page/summary`), l'édition française de Wikivoyage, les conditions d'utilisation de Wikimedia, le détail des paramètres et rayons de `geosearch`.
- **Pour nous** : gratuit, sans clé ni carte, très peu de données envoyées (le nom de la destination). Idéal pour **un court texte « À savoir sur Lyon »** (3 ou 4 lignes + « Lire sur Wikipédia/Wikivoyage ») plutôt que des adresses. Attribution et lien obligatoires. Pas de note ni de prix. Qualité bonne pour les grandes destinations, maigre pour les petits lieux ; Wikivoyage est plus fourni en anglais qu'en français (non lu).

### OpenStreetMap par Overpass
- **Lu** : l'instance publique principale limite à 10 000 requêtes, 1 Go et 10 minutes de calcul par jour ; « si vous l'utilisez régulièrement, divisez par 100 » ; User-Agent obligatoire ; pause de 30 s après une erreur 429 ; « n'attendez pas de fiabilité élevée » ; l'usage commercial doit passer par un serveur à soi ou payant. Licence ODbL : afficher l'attribution « © les contributeurs d'OpenStreetMap » et dire que les données sont sous ODbL ; changer les données impose la même licence. OpenStreetMap « ne peut pas fournir d'API de cartes gratuite à des tiers » (tuiles).
- **Étiquettes** : `amenity=restaurant` (nom, cuisine, horaires, téléphone, site), `tourism=hotel` (nom, étoiles, site), `tourism=attraction` (lieux d'intérêt ; la page signale que l'étiquette est discutée).
- **Pour nous** : gratuit, sans clé ni carte. 13 personnes restent très loin de la limite, même divisée par 100 (100 requêtes par jour). Pas de notes, pas d'avis, pas de photos ; qualité inégale selon l'endroit (bonne en France, plus mince ailleurs). Vie privée : la zone cherchée part vers un serveur communautaire. Rien à stocker : affichage seulement.

### Foursquare Places
- **Lu** : jusqu'à 10 000 appels gratuits sur les services « Pro » (recherche, détails, autocomplétion), puis 15 $ pour 1 000 appels de 501 à 100 000 ; photos et conseils en « Premium » à 18,75 $ pour 1 000.
- **Non lu** : carte bancaire exigée, règle de cache, attribution (la FAQ est bloquée).
- **Pour nous** : 10 000 appels gratuits, c'est « beaucoup » pour 13 personnes, mais le palier de 500 appels à 0 $ et ensuite payant, la carte non confirmée et la règle de cache non lue : **pas retenu**.

### Mapbox, recherche par catégories (Search Box), relue le 22/11 après #487

Oui, Mapbox propose des suggestions de lieux, avec une réserve forte sur la règle de stockage et un point sur les prix qu'on n'a pas pu lire.

- **Certain (texte de la documentation officielle)** :
  - Recherche par catégorie autour d'un point : `GET https://api.mapbox.com/search/searchbox/v1/category/{catégorie}` avec le jeton ; paramètres `proximity` (longitude,latitude), `limit` (jusqu'à 25), `language` (anglais par défaut, `fr` possible), `bbox`, `radius`, `country`, `show_closed_pois`, `exclude_fields` (`photos`, `reviews`). La liste des catégories : `GET .../v1/list/category` (on y voit « restaurant » et « lodging » ; les « sites à visiter » n'ont pas été vérifiés).
  - La réponse est un `FeatureCollection` : pour chaque lieu, la catégorie (`poi_category`), la marque, la distance, et `metadata` avec au moins le téléphone, le site web et les horaires d'ouverture ; la note (`rating`), l'accessibilité en fauteuil et la popularité apparaissent dans les exemples. Le site, l'adresse et le nom viennent des propriétés usuelles.
  - Un lieu précis se relit avec `retrieve` et des jeux d'attributs (`photos`, `visit`, `venue`) ; la liste complète des champs de `metadata` n'est pas écrite (nombre d'avis, niveau de prix et photos : **non vérifiés** comme champs).
  - L'estimation de trajet (`eta_type`) est payante en plus : on ne l'utilise pas.
- **Plutôt sûr (résumés de pages tronquées, à relire)** :
  - La recherche par catégorie se facture à la requête (sans jeton de session) ; la saisie « suggest » puis « retrieve » forme une session facturée une fois et demande un `session_token` (un identifiant tiré au hasard par saisie, 180 secondes pour finir, 50 appels « suggest » au plus). **Pour des catégories autour d'un point, un jeton de session n'est donc pas nécessaire.**
  - Limite : 10 requêtes par seconde.
  - **Stockage** : les données ne sont disponibles « que pour un usage temporaire » selon les conditions de Mapbox ; garder une position demande de contacter les ventes. Pour nous (« affichage seulement, rien enregistré ») ce n'est pas bloquant, à condition de ne rien copier dans Firestore ni dans une base locale durable, seulement un cache de session en mémoire.
- **Non lu** : le gratuit et le prix à la requête pour la recherche par catégorie, le plafond d'un compte « demo » sans carte, et la section « restrictions et limites » en toutes lettres. Kévin peut les lire dans sa console Mapbox (page Facturation et page Prix du produit « Search Box API »).
- **Vie privée** : la zone autour de l'étape (point + rayon) part vers Mapbox ; rien d'autre. Aucun jeton de session n'identifie la personne.
- **Qualité** : bonne en France (données de fournisseurs), à vérifier à l'étranger sur quelques destinations connues avant de s'engager.
- **Pour nous** : c'est l'option la plus proche de Polarsteps : des restaurants et des hôtels avec téléphone, site et horaires, sans composer plusieurs sources. Elle passe par le même jeton public que la carte, sans nouvelle clé. Elle devient la **première API à essayer** après les liens (PR 1), à la condition que Kévin lise le gratuit et le plafond demo de ce produit dans sa console, et que les résultats ne soient jamais enregistrés. Sans note ni avis fiables (les avis ne sont pas confirmés), on garde « Ouvrir sur Google Maps » sur chaque ligne pour les avis.

## Tableau de synthèse

| Option | Coût sans carte | Quota face à 13 personnes | On garde quelque chose ? | Poids de l'APK | Ce qui part chez le tiers | Qualité (France / étranger) |
|---|---|---|---|---|---|---|
| Lien Google Maps | 0 | sans objet | non | aucun | rien d'ici (c'est Google Maps qui s'ouvre) | très bonne / très bonne |
| Texte Wikivoyage/Wikipédia | 0, sans clé | « pas de limite dure » | non (affichage, attribution) | aucun (HTTP simple) | le nom de la destination | bonne / inégale |
| OpenStreetMap (Overpass) | 0, sans clé | très large | non | aucun | la zone cherchée | bonne / inégale, sans notes |
| Tripadvisor API | non confirmé | 50 appels/s | non lu | aucun | la zone cherchée | très bonne / très bonne |
| Foursquare | 10 000 appels gratuits, carte non confirmée | large au début | non lu | aucun | la zone cherchée | bonne / bonne |
| Mapbox Search Box (catégories) | non lu, à lire dans la console | 10 requêtes/s | « usage temporaire » : affichage seulement | aucun | la zone autour de l'étape | bonne en France, à vérifier ailleurs |

## Recommandation

Commencer par **les liens**, qui donnent à Kévin 80 % de ce qu'il demande pour zéro risque : pas de clé, pas de quota, pas de stockage, pas d'attribution à gérer, et le contenu reste chez Google ou Tripadvisor. Ajouter ensuite des suggestions **seulement si elles restent gratuites et sans carte** : un texte Wikivoyage/Wikipédia sur la destination, puis des lieux OpenStreetMap, affichés sans les garder. Mapbox Search Box (catégories) vient juste après les liens, une fois son gratuit et son plafond demo lus par Kévin ; Tripadvisor et Foursquare : rien tant que la gratuité sans carte et la règle de stockage ne sont pas lues sur leur page officielle (Kévin peut les lire dans son compte, comme pour Mapbox).

## Découpage en PR (une à la fois)

1. **Liens « Ouvrir »** : sur la page d'un voyage et d'une étape avec lieu, trois boutons de 56 dp : « Restaurants près de… » (Google Maps), « Hôtels à… » (Google Maps), « Voir sur Tripadvisor » (recherche web). Aucune donnée, aucun appel, module éteint par défaut. Gestes pour Kévin : ouvrir un voyage avec une destination et toucher un bouton.
2. **« À savoir sur… »** : un court texte de Wikivoyage ou de Wikipédia, mis en cache sur le téléphone seulement, avec « Lire sur Wikipédia » et l'attribution CC BY-SA ; repli silencieux sans réseau. Phrase dans l'aide : ce qui part (le nom de la destination).
3. **Lieux proches par Mapbox (catégories : restaurants, hôtels) ou, si Kévin refuse Mapbox, par OpenStreetMap** (si Kévin les veut) : restaurants, hôtels et lieux d'intérêt autour d'une étape, affichés sous forme de liste d'au plus 10 lignes avec « © OpenStreetMap », jamais enregistrés, avec une pause de 30 s sur erreur et une limite côté appli. Bouton « Ouvrir sur Google Maps » sur chaque ligne.
4. **Plus tard, si Kévin le demande** : une API à note (Tripadvisor ou Foursquare) seulement après lecture écrite de leur gratuité sans carte, de leur règle de stockage et de leurs obligations d'affichage.

Aucune règle ni function dans les trois premiers temps : pas de « Publier Firebase ».
