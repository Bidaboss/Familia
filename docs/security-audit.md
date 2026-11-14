# Audit de sécurité des règles Firestore (lot 7)

Relecture complète de `firebase/firestore.rules` le 14/11/2026, avant d'ouvrir l'appli au reste de la famille. Le but est de vérifier que les droits du PRD (« Droits par profil ») tiennent côté serveur, même pour quelqu'un qui contournerait l'appli.

Les tests de l'émulateur servent de preuve : 80 tests dans `firebase/tests`, relancés par la CI à chaque pull request. Les nouveaux cas de cet audit sont dans `firebase/tests/audit.test.mjs`.

## Ce qui a été vérifié

| Sujet | Règle | Tests |
|---|---|---|
| Une personne hors de la famille, non connectée ou avec une adresse non vérifiée ne lit rien | `isMember()` : adresse Google vérifiée présente dans `memberEmails` | `members.test.mjs` |
| Un membre retiré ou dont l'adresse change perd l'accès tout de suite | L'entrée `memberEmails` part dans le même lot d'écriture que le membre | `admin.test.mjs` |
| Personne ne s'ajoute à la famille ni ne devient admin seul | Seuls les admins écrivent `members`, `memberEmails` et `households`, et un admin ne change pas son propre rôle | `admin.test.mjs`, `members.test.mjs` |
| Le dernier admin ne peut pas disparaître | Un admin ne se retire pas lui-même et ne s'enlève pas le rôle | `admin.test.mjs` |
| Un admin retiré du rôle perd ses droits tout de suite | `isAdmin()` relit le document du membre à chaque requête | `audit.test.mjs` |
| Création, modification et suppression d'événements selon le profil | `canTarget`, `canManage` | `events.test.mjs` |
| Un enfant ne modifie pas l'événement d'un adulte, même hors de l'appli | `canManage` | `events.test.mjs` |
| Personne n'écrit au nom d'un autre | `authorId`, `authorName`, `answeredBy` vérifiés face au membre connecté | `events.test.mjs`, `rsvps.test.mjs` |
| « Qui vient » : chacun pour soi, et un adulte pour ses enfants | `canAnswerFor` | `rsvps.test.mjs` |
| Les heures d'écriture viennent du serveur | `createdAt`, `updatedAt` égaux à `request.time` | `events.test.mjs`, `rsvps.test.mjs` |
| Les collections pas encore ouvertes restent fermées (cadeaux, config) | Pas de règle, donc tout est refusé | `members.test.mjs` |

## Ce que l'audit a corrigé

1. **Un adulte pouvait recevoir des « adultes responsables ».** Rien n'empêchait un admin, par erreur, de mettre `parentIds` sur un adulte. L'adulte cité aurait alors pu modifier les événements de cet adulte et répondre pour lui. Désormais, un adulte n'a aucun adulte responsable. Un enfant en a de 1 à 4, et chacun doit être un adulte qui existe, y compris quand il est ajouté dans le même lot d'écriture.
2. **Des dates impossibles étaient acceptées**, comme le 13e mois ou le 32 du mois. L'appli les cachait sans planter, mais elles n'ont pas leur place dans la base. Les dates d'événement et de naissance doivent maintenant avoir un mois de 01 à 12 et un jour de 01 à 31.

Au lot 7b, le formulaire refuse aussi de passer en profil enfant l'adulte qui est le seul responsable d'un enfant. Sinon, les règles refuseraient toute modification de cet enfant tant qu'on ne lui a pas choisi un autre adulte.

## Ce que l'audit a ouvert

- **La lecture groupée des réponses** (`collectionGroup('rsvps')`) est réservée aux membres et ne sert qu'à lire. Depuis le lot 7b, elle affiche dans chaque ligne du planning combien de membres viennent ou viendront peut-être. L'appli ne compte que les membres encore dans la famille. Les écritures passent toujours par `events/{eventId}/rsvps/{memberId}`, avec les mêmes contrôles.

## Risques acceptés

| Risque | Pourquoi on l'accepte |
|---|---|
| Le nombre d'admins n'est pas limité à deux côté serveur | Seul un admin peut nommer un admin, et les règles ne savent pas compter les documents. L'appli ne propose que deux admins. |
| Un membre peut créer beaucoup d'événements | Il faut faire partie de la famille. L'alerte de budget à 1 € préviendrait Kévin bien avant tout coût réel. |
| Toute la famille lit les adresses Google des membres | C'est voulu : la famille se connaît, et ces adresses servent à reconnaître chacun. Aucune autre donnée personnelle n'est stockée. |
| Les réponses d'un événement supprimé ou d'un membre retiré restent dans la base | Elles ne sont lisibles que par la famille, et l'appli ne les affiche pas. La Cloud Function du lot 8 les effacera. |
| Les règles acceptent des dates qui n'existent pas, comme le 31 avril ou le 30 février | `isDay` vérifie le mois (01 à 12) et le jour (01 à 31), pas le calendrier. L'appli ne saisit les dates qu'avec le sélecteur de date, et elle ignore à la lecture une date qu'elle ne sait pas lire. |
| Un enfant avec l'appli lit tout le planning | Le PRD le prévoit. Les cadeaux (V2) auront leurs propres règles, qui excluront la personne fêtée. |

## À refaire

- À chaque nouvelle collection : ajouter ses règles et ses tests dans le même lot, puis compléter ce document.
- Avant la V2 (cadeaux) : refaire un audit avec un compte de la personne fêtée (PRD, risque « La surprise fuit »).
