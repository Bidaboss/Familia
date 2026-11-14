# Mettre en place Firebase (une seule fois)

1. Sur console.firebase.google.com, crée le projet « Familia » (Google Analytics peut être désactivé).
2. « Utilisation et facturation » : passe en offre **Blaze** et crée une **alerte de budget à 1 €**.
3. Ajoute une appli **Android** avec le nom de package `fr.familia.app`. Renseigne l'empreinte **SHA-1** de la clé de signature (voir plus bas). Télécharge `google-services.json` et place-le dans `app/` (il peut être versionné : il identifie le projet mais n'est pas un secret, les règles protègent les données).
4. **Authentication** → Commencer → fournisseur **Google** → Activer.
5. **Firestore Database** → Créer → région **europe-west1** → mode production.
6. Publie les règles : `cd firebase && npx firebase deploy --only firestore:rules --project <id-du-projet>`.

## Créer le premier admin

Tant que l'écran d'ajout de membres (lot 3) n'existe pas, le premier admin se crée à la main dans la console Firestore :

1. Collection `households`, document `<id du foyer>` : `name` = nom du foyer.
2. Collection `members`, document `<memberId>` : les champs de `docs/data-model.md`, avec `isAdmin` = `true`, `profile` = `adult`, `uid` = null.
3. Collection `memberEmails`, document `<ton adresse Google en minuscules>` : `memberId` = le même `<memberId>`.

## Clé de signature

La CI signe les APK avec la clé stockée dans les secrets GitHub du dépôt (`Settings → Secrets and variables → Actions`) :

| Secret | Contenu |
|---|---|
| `FAMILIA_KEYSTORE_BASE64` | le fichier `.jks` encodé en base64 |
| `FAMILIA_KEYSTORE_PASSWORD` | mot de passe du fichier |
| `FAMILIA_KEY_ALIAS` | nom de la clé dans le fichier |
| `FAMILIA_KEY_PASSWORD` | mot de passe de la clé |

Garde aussi une copie du fichier et des mots de passe dans ton gestionnaire de mots de passe : si la clé est perdue, chacun doit désinstaller et réinstaller l'appli.
