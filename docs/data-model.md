# Modèle de données Familia (Firestore)

Conventions : noms de collections et de champs en anglais, en camelCase. Dates sans heure au format `AAAA-MM-JJ` (texte). Adresses e-mail toujours en minuscules.

Les règles de sécurité sont dans `firebase/firestore.rules` et leurs tests dans `firebase/tests/`. Chaque lot qui ajoute une collection ajoute ses règles et ses tests.

## Reconnaître un membre (lot 2)

Un membre invité n'a pas encore de compte Firebase (pas d'UID) tant qu'il ne s'est pas connecté. Les règles le reconnaissent donc par **son adresse Google vérifiée** :

1. L'admin crée le membre dans `members/{memberId}` et l'entrée d'index `memberEmails/{email}` → `{ memberId }`.
2. À la connexion, Firebase fournit `request.auth.token.email` et `email_verified`. Les règles mettent l'adresse en minuscules et vérifient que `memberEmails/{email}` existe : c'est la fonction `isMember()`.
3. À la première connexion, l'appli écrit l'UID dans le champ `uid` du membre. C'est la seule écriture permise au membre sur son propre document, une seule fois, sans toucher aux autres champs.

Pourquoi un index séparé plutôt qu'une recherche : les règles Firestore ne peuvent pas faire de requête, seulement lire un document dont on connaît le chemin. L'adresse sert donc d'identifiant de document dans `memberEmails`.

Une personne connectée mais absente de la famille peut lire uniquement sa propre entrée `memberEmails/{son adresse}`, qui n'existe pas. L'appli affiche alors « ne fait pas partie de la famille » au lieu d'une erreur de permission.

## Collections

### `members/{memberId}`

| Champ | Type | Description |
|---|---|---|
| `firstName` | texte | Prénom affiché partout |
| `email` | texte ou null | Adresse Google, en minuscules. Null pour un enfant sans l'appli |
| `birthDate` | texte `AAAA-MM-JJ` ou null | Sert à calculer les anniversaires (lot 5) |
| `householdId` | texte | Foyer du membre |
| `profile` | `adult` \| `childWithApp` \| `childWithoutApp` | Droits du membre |
| `isAdmin` | booléen | Deux admins au plus, toujours adultes |
| `colorIndex` | entier 0 à 12, facultatif | Couleur du membre parmi les 13 de l'appli. Sans valeur, l'appli en déduit une de l'identifiant |
| `parentIds` | liste de `memberId` | Pour un enfant : de 1 à 4 adultes responsables, qui doivent exister et être adultes (un beau-parent a les mêmes droits qu'un parent). Toujours vide pour un adulte (audit du lot 7) |
| `uid` | texte ou null | UID Firebase, écrit à la première connexion |

### `memberEmails/{email}`

| Champ | Type | Description |
|---|---|---|
| `memberId` | texte | Le membre qui a cette adresse |

Écrit uniquement par les admins, **dans la même écriture groupée** (batch) que le membre :

- ajouter un membre : créer `members/{id}` et `memberEmails/{email}` ensemble ;
- changer son adresse : mettre à jour le membre, supprimer l'ancienne entrée et créer la nouvelle ;
- retirer un membre : supprimer `members/{id}` et son entrée ensemble. Sinon la personne garderait l'accès, et les règles refusent l'écriture.

## Droits des admins (lot 3)

- Deux admins au plus : Kévin et un second adulte. Un admin nomme ou retire l'autre, mais **ne peut pas changer son propre rôle ni se retirer lui-même** : il reste donc toujours au moins un admin. La limite de deux est vérifiée par l'appli.
- Un admin ajoute, modifie et retire les membres, et crée ou renomme les foyers. Un foyer n'est jamais supprimé.
- La fiche membre de l'appli (lot 3c) vérifie en plus ce que les règles ne peuvent pas vérifier : une adresse qui n'appartient à aucun autre membre, deux admins au plus, des adultes responsables qui sont bien des adultes, une date de naissance qui n'est pas dans le futur. Hors ligne, la fiche se ferme au bout de 4 secondes et l'écriture part au retour du réseau. Limite connue : si les règles la refusent à ce moment-là, le refus passe inaperçu (le cache revient simplement à l'état du serveur). Le risque est faible, puisque la fiche vérifie les mêmes contraintes avant d'écrire.
- Retirer un membre retire aussi son identifiant des `parentIds` des enfants qui le listaient, dans le même batch. L'appli refuse de retirer le seul adulte responsable d'un enfant.
- Le rôle admin est lu en direct : un admin dont le rôle est retiré perd les boutons d'ajout et de modification tout de suite.
- Un admin ne peut jamais modifier le champ `uid` : seul le membre le lie, à sa première connexion.
- Les règles vérifient la forme d'un membre : prénom de 1 à 40 caractères, adresse en minuscules, date au format `AAAA-MM-JJ`, foyer existant, un enfant a au moins un adulte responsable, un enfant sans l'appli n'a pas d'adresse, un admin est forcément adulte.

### `households/{householdId}`

| Champ | Type | Description |
|---|---|---|
| `name` | texte | Nom du foyer |

### `events/{eventId}` (lot 4)

| Champ | Type | Description |
|---|---|---|
| `title` | texte, 1 à 80 caractères | Obligatoire |
| `date` | texte `AAAA-MM-JJ` | Obligatoire |
| `time` | texte `HH:mm` ou null | Heure facultative |
| `place` | texte (120 au plus) ou null | Lieu facultatif |
| `note` | texte (1 000 au plus) ou null | Note facultative |
| `targetType` | `self` \| `child` \| `household` \| `family` | Pour qui : moi, un enfant, un foyer, toute la famille |
| `targetMemberId` | `memberId` ou null | L'auteur pour `self`, l'enfant pour `child`, sinon null |
| `householdId` | `householdId` ou null | Le foyer pour `household`, sinon null |
| `authorId` | `memberId` | L'auteur, jamais modifié |
| `authorName` | texte | Prénom de l'auteur au moment de la création : les événements d'un membre retiré restent lisibles (PRD, cas « séparation ») |
| `createdAt`, `updatedAt` | horodatage serveur | `createdAt` jamais modifié, `updatedAt` à chaque écriture |

Le destinataire est écrit dans l'événement, pour que les règles vérifient les droits sans lire d'autres événements.

Droits (PRD, « Droits par profil ») :

- **Lire** : tous les membres.
- **Créer** :
  - un admin, pour qui il veut ;
  - un adulte, pour lui, pour un enfant dont il est adulte responsable, pour son foyer ou pour toute la famille ;
  - un enfant avec l'appli, pour lui seulement.
- **Modifier, supprimer** :
  - un admin, tous les événements ;
  - chacun, les siens ;
  - un adulte, aussi ceux de ses enfants, écrits par eux ou pour eux.
  - Un nouveau destinataire doit être un de ceux que la personne a le droit de choisir.
- Un membre retiré : ses événements restent visibles, et seul un admin peut encore les modifier ou les supprimer.

### `events/{eventId}/rsvps/{memberId}` (lot 6)

Une réponse par membre et par événement (PRD, récit 4). L'identifiant du document est celui du membre concerné, donc il ne peut y avoir qu'une réponse par personne.

| Champ | Type | Description |
|---|---|---|
| `status` | `yes` \| `maybe` \| `no` | « Je viens », « Peut-être », « Je ne viens pas » |
| `answeredBy` | `memberId` | Qui a répondu : le membre lui-même ou un de ses adultes responsables |
| `updatedAt` | horodatage serveur | À chaque réponse |

Droits :

- **Lire** : tous les membres.
- **Répondre, changer ou retirer sa réponse** : le membre lui-même, ou un adulte responsable s'il s'agit d'un enfant. Personne d'autre, pas même un admin, ne répond à la place de quelqu'un.
- L'événement et le membre doivent exister.
- Quand un événement est supprimé, ses réponses sont effacées côté serveur au lot 8, par la même Cloud Function qui prévient les membres inscrits (PRD, récit 5).
