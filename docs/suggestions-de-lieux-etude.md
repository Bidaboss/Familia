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

### Mapbox, recherche par catégories (Search Box)
- **Lu** : points `suggest`, `retrieve`, `category`, `list/category` (catégories dont « restaurant » et « lodging »), jusqu'à 10 résultats, 256 caractères au plus, l'attribution à afficher, facturation par « session ».
- **Non lu** : les sections « limites », « prix » et « facturation par session » n'ont pas pu être lues, donc ni le gratuit, ni la règle de stockage. La règle du géocodage voisin (étude précédente) est qu'on ne garde pas un résultat temporaire sans carte bancaire.
- **Pour nous** : **pas retenu**, pour la même raison que la recherche de lieu : un résultat qu'on ne peut pas garder et un quota non confirmé.

## Tableau de synthèse

| Option | Coût sans carte | Quota face à 13 personnes | On garde quelque chose ? | Poids de l'APK | Ce qui part chez le tiers | Qualité (France / étranger) |
|---|---|---|---|---|---|---|
| Lien Google Maps | 0 | sans objet | non | aucun | rien d'ici (c'est Google Maps qui s'ouvre) | très bonne / très bonne |
| Texte Wikivoyage/Wikipédia | 0, sans clé | « pas de limite dure » | non (affichage, attribution) | aucun (HTTP simple) | le nom de la destination | bonne / inégale |
| OpenStreetMap (Overpass) | 0, sans clé | très large | non | aucun | la zone cherchée | bonne / inégale, sans notes |
| Tripadvisor API | non confirmé | 50 appels/s | non lu | aucun | la zone cherchée | très bonne / très bonne |
| Foursquare | 10 000 appels gratuits, carte non confirmée | large au début | non lu | aucun | la zone cherchée | bonne / bonne |
| Mapbox Search Box | non confirmé | non lu | non lu | aucun | la zone cherchée | bonne / bonne |

## Recommandation

Commencer par **les liens**, qui donnent à Kévin 80 % de ce qu'il demande pour zéro risque : pas de clé, pas de quota, pas de stockage, pas d'attribution à gérer, et le contenu reste chez Google ou Tripadvisor. Ajouter ensuite des suggestions **seulement si elles restent gratuites et sans carte** : un texte Wikivoyage/Wikipédia sur la destination, puis des lieux OpenStreetMap, affichés sans les garder. Tripadvisor, Foursquare, Mapbox : rien tant que la gratuité sans carte et la règle de stockage ne sont pas lues sur leur page officielle (Kévin peut les lire dans son compte, comme pour Mapbox).

## Découpage en PR (une à la fois)

1. **Liens « Ouvrir »** : sur la page d'un voyage et d'une étape avec lieu, trois boutons de 56 dp : « Restaurants près de… » (Google Maps), « Hôtels à… » (Google Maps), « Voir sur Tripadvisor » (recherche web). Aucune donnée, aucun appel, module éteint par défaut. Gestes pour Kévin : ouvrir un voyage avec une destination et toucher un bouton.
2. **« À savoir sur… »** : un court texte de Wikivoyage ou de Wikipédia, mis en cache sur le téléphone seulement, avec « Lire sur Wikipédia » et l'attribution CC BY-SA ; repli silencieux sans réseau. Phrase dans l'aide : ce qui part (le nom de la destination).
3. **Lieux proches par OpenStreetMap** (si Kévin les veut) : restaurants, hôtels et lieux d'intérêt autour d'une étape, affichés sous forme de liste d'au plus 10 lignes avec « © OpenStreetMap », jamais enregistrés, avec une pause de 30 s sur erreur et une limite côté appli. Bouton « Ouvrir sur Google Maps » sur chaque ligne.
4. **Plus tard, si Kévin le demande** : une API à note (Tripadvisor ou Foursquare) seulement après lecture écrite de leur gratuité sans carte, de leur règle de stockage et de leurs obligations d'affichage.

Aucune règle ni function dans les trois premiers temps : pas de « Publier Firebase ».
