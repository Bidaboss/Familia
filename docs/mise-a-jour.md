# Publier une nouvelle version de Familia

Chaque fois que l'appli s'ouvre ou revient à l'écran, même si elle était restée ouverte en arrière-plan, elle lit le document `config/app` dans Firestore. Si ce document annonce une version plus récente que celle qui est installée, un encadré « Nouvelle version disponible » apparaît en haut de l'appli, avec un bouton « Mettre à jour ». Ce bouton ouvre le dossier Drive de Familia, où tu déposes chaque nouvelle version. Le lien de ce dossier est écrit dans le code de l'appli (`app/build.gradle.kts`) : il n'y a rien à régler dans l'appli, et c'est le même lien que dans l'invitation.

## Ce qui protège la famille

- Le lien est fixé dans le code et commence par `https://`. Personne ne peut le changer sans une pull request.
- Seul toi, depuis la console Firebase, peux modifier `config/app`. Les règles interdisent toute écriture depuis l'appli, même à un admin (`firebase/tests/config.test.mjs`).
- Android vérifie lui-même la signature de l'APK au moment de l'installation : il refuse une mise à jour qui n'est pas signée avec la même clé que l'appli installée. Un faux fichier ne peut donc pas remplacer Familia.

## Publier une version, étape par étape

1. Dans `app/build.gradle.kts`, augmente `versionCode` de 1 (par exemple de 1 à 2) et change `versionName` (par exemple de « 0.1.0 » à « 0.2.0 »). Ces changements passent par une pull request, comme le reste du code.
2. Une fois la pull request fusionnée, récupère l'APK signé avec la clé de publication. La façon de le fabriquer sera décrite avec le lot 10b.
3. Dépose l'APK dans le dossier Drive de Familia. Le dossier doit être partagé en « Tous les utilisateurs disposant du lien », pour que la famille puisse l'ouvrir sans compte particulier. Ce n'est pas un risque : l'APK ne contient aucune donnée de la famille. Les données restent dans Firebase, protégées par la connexion Google et les règles de sécurité. Garde un seul APK dans le dossier, le plus récent, pour que personne n'installe une ancienne version par erreur.
4. Dans la console Firebase, ouvre **Firestore**, puis la collection `config` et le document `app`. Crée-les s'ils n'existent pas encore. Renseigne :

   | Champ | Type | Exemple |
   |---|---|---|
   | `latestVersionCode` | number | `2` |
   | `latestVersionName` | string | `0.2.0` |

5. Ouvre Familia sur ton téléphone. **Attendu :** l'encadré « Nouvelle version 0.2.0 disponible » apparaît. Touche « Mettre à jour », le dossier Drive s'ouvre : télécharge l'APK, puis ouvre-le pour l'installer par-dessus l'appli. Tes données ne bougent pas, puisqu'elles sont dans Firebase.

« Plus tard » cache l'encadré jusqu'à ce que l'appli revienne à l'écran.
