# Sécurité des règles Firestore

Les droits décrits dans le PRD (« Droits par profil ») sont vérifiés par le serveur, dans `firebase/firestore.rules` et `firebase/storage.rules`, et pas seulement par l'appli. Quelqu'un qui contournerait l'appli a donc les mêmes limites qu'un membre qui l'utilise.

## Les principes

- Seule une personne de la famille, connectée avec une adresse Google vérifiée, lit ou écrit quoi que ce soit.
- Personne ne s'ajoute seul à la famille, ni ne se donne un rôle. Un membre retiré perd l'accès tout de suite.
- Personne n'écrit au nom d'un autre : l'auteur d'un document est toujours le membre connecté.
- Les heures d'écriture viennent du serveur, et les champs sont bornés (longueurs, dates, listes).
- Une collection sans règle reste fermée.

## Comment c'est vérifié

Les tests de l'émulateur, dans `firebase/tests`, servent de preuve. La CI les relance à chaque pull request qui touche aux règles. Les cas limites trouvés en relisant toutes les règles (lot 7) sont dans `firebase/tests/audit.test.mjs`.

## Écarts connus

- **La photo de « On m'a offert »** (rév. 237). Seuls la personne et ceux qui gardent ses listes lisent le document `giftLists/{listId}/received/{id}`. Sa photo, elle, est lisible par tout membre de la famille. La règle de Storage ne peut lire que deux documents. Vérifier les parents d'un enfant sans l'appli en demanderait un troisième. L'écart est accepté pour deux raisons. D'abord, la photo ne montre rien de nouveau, puisque toute la famille l'a déjà vue sur la liste. Ensuite, le document, qui dit qui a offert quoi, reste fermé.
  - Côté effacement, aucune appli ne supprime un document `received`. Le serveur ne les efface qu'avec leur liste (`deleteGiftListContent`). `onGiftListDeleted` retire alors tout le dossier `giftLists/{listId}/`, et les photos copiées partent avec.

## À refaire

- À chaque nouvelle collection : ajouter ses règles et ses tests dans la même pull request.
- Avant chaque module sensible (cadeaux, capsule, position) : relire les règles avec le compte de la personne qui ne doit rien voir.
