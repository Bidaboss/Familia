# Mettre en place Firebase (une seule fois)

1. Sur console.firebase.google.com, crée le projet « Familia » (Google Analytics peut être désactivé).
2. « Utilisation et facturation » : passe en offre **Blaze** et crée une **alerte de budget à 1 €**.
3. Ajoute une appli **Android** avec le nom de package `fr.familia.app`. Renseigne l'empreinte **SHA-1** de la clé de signature (voir plus bas). Télécharge `google-services.json` et place-le dans `app/` (il peut être versionné : il identifie le projet mais n'est pas un secret, les règles protègent les données).
4. **Authentication** → Commencer → fournisseur **Google** → Activer.
5. **Firestore Database** → Créer → région **europe-west1** → mode production.
6. Publie les règles : `cd firebase && npx firebase deploy --only firestore:rules --project <id-du-projet>`.

## Créer le premier admin

Après la première publication des règles (« Publier Firebase »), lance une seule fois le workflow GitHub **Créer le premier admin** (onglet Actions, branche `main`) avec ton prénom, ton adresse Google et le nom de ton foyer. Il crée le foyer, ta fiche d'admin et l'entrée `memberEmails` en une seule écriture, et refuse de tourner si la famille a déjà des membres. Les suivants s'ajoutent depuis l'appli. Les valeurs saisies au lancement restent visibles sur la page du lancement dans GitHub. C'est acceptable, car le dépôt est privé, et l'adresse est masquée dans les journaux.

En secours, la même chose à la main dans la console Firestore :

1. Collection `households`, document `<id du foyer>` : `name` = nom du foyer.
2. Collection `members`, document `<memberId>` : les champs de `docs/data-model.md`, avec `isAdmin` = `true`, `profile` = `adult`, `uid` = null.
3. Collection `memberEmails`, document `<ton adresse Google en minuscules>` : `memberId` = le même `<memberId>`.

## Clés de signature

Il y a deux clés, chacune stable :

- **La clé de test** signe les APK fabriqués par la CI à chaque pull request. Leur nom de package est `fr.familia.app.test` : ils s'installent à côté de l'appli de la famille sans jamais la remplacer.
- **La clé de publication** signe seulement les APK déposés dans le Drive pour la famille (lot 10).

Dans Firebase, ajoute une appli Android pour chaque nom de package (`fr.familia.app` et `fr.familia.app.test`), chacune avec l'empreinte SHA-1 de sa clé, sinon la connexion Google échoue.

Secrets GitHub du dépôt (`Settings → Secrets and variables → Actions`) pour la clé de test :

| Secret | Contenu |
|---|---|
| `FAMILIA_TEST_KEYSTORE_BASE64` | le fichier `.jks` de test encodé en base64 |
| `FAMILIA_TEST_KEYSTORE_PASSWORD` | mot de passe du fichier |
| `FAMILIA_TEST_KEY_ALIAS` | nom de la clé dans le fichier |
| `FAMILIA_TEST_KEY_PASSWORD` | mot de passe de la clé |

Les secrets de la clé de publication (`FAMILIA_KEYSTORE_*`) arriveront avec le lot 10.

Garde une copie des deux fichiers et de leurs mots de passe dans ton gestionnaire de mots de passe : si la clé de publication est perdue, chacun doit désinstaller et réinstaller l'appli.
