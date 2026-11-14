# Publier une nouvelle version de Familia

Chaque fois que l'appli s'ouvre, elle lit le document `config/app` dans Firestore. Si ce document annonce une version plus récente que celle qui est installée, un encadré « Nouvelle version disponible » apparaît en haut de l'appli, avec un bouton « Mettre à jour ». Ce bouton ouvre le lien de l'APK.

## Ce qui protège la famille

- Le lien doit commencer par `https://`. Sinon, l'appli ne propose rien.
- Seul toi, depuis la console Firebase, peux modifier `config/app`. Les règles interdisent toute écriture depuis l'appli, même à un admin (`firebase/tests/config.test.mjs`).
- Android vérifie lui-même la signature de l'APK au moment de l'installation : il refuse une mise à jour qui n'est pas signée avec la même clé que l'appli installée. Un faux fichier ne peut donc pas remplacer Familia.

## Publier une version, étape par étape

1. Dans `app/build.gradle.kts`, augmente `versionCode` de 1 (par exemple de 1 à 2) et change `versionName` (par exemple de « 0.1.0 » à « 0.2.0 »). Ces changements passent par une pull request, comme le reste du code.
2. Une fois la pull request fusionnée, récupère l'APK signé avec la clé de publication. La façon de le fabriquer sera décrite quand la clé de signature sera choisie (carte DEV-5).
3. Dépose l'APK dans le dossier Drive de Familia, puis copie son lien de partage, en « Tous les utilisateurs disposant du lien ».
4. Dans la console Firebase, ouvre **Firestore**, puis la collection `config` et le document `app`. Crée-les s'ils n'existent pas encore. Renseigne :

   | Champ | Type | Exemple |
   |---|---|---|
   | `latestVersionCode` | number | `2` |
   | `latestVersionName` | string | `0.2.0` |
   | `downloadUrl` | string | `https://drive.google.com/file/d/…/view` |

5. Ouvre Familia sur ton téléphone. **Attendu :** l'encadré « Nouvelle version 0.2.0 disponible » apparaît. Touche « Mettre à jour », télécharge le fichier, puis ouvre-le pour l'installer par-dessus l'appli. Tes données ne bougent pas, puisqu'elles sont dans Firebase.

« Plus tard » cache l'encadré jusqu'à la prochaine ouverture de l'appli.
