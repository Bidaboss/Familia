# Publier une nouvelle version de Familia

Chaque fois que l'appli s'ouvre ou revient à l'écran, même si elle était restée ouverte en arrière-plan, elle lit le document `config/app` dans Firestore. Si ce document annonce une version plus récente que celle qui est installée, un encadré « Nouvelle version disponible » apparaît en haut de l'appli, avec un bouton « Mettre à jour ». Ce bouton télécharge le nouvel APK directement depuis le Drive, vérifie sa taille et son empreinte, puis lance l'installation d'Android : on n'a plus qu'à toucher « Mettre à jour » dans la fenêtre d'Android. La première fois, Android demande d'autoriser Familia à installer des applis : l'appli explique ce réglage et y conduit. Si quelque chose échoue (pas de réseau, fichier différent), l'appli ouvre le dossier Drive de Familia, où le workflow « APK de publication » dépose chaque nouvelle version, comme avant. Le lien de ce dossier est gravé dans l'appli au moment où GitHub la fabrique. Il vient du secret GitHub `FAMILIA_DOWNLOAD_URL`, pour que le dépôt public ne le montre pas. Il n'y a rien à régler dans l'appli, et c'est le même lien que dans l'invitation.

## Ce qui protège la famille

- Le lien est gravé dans l'APK signé. Seul l'admin principal peut changer le secret `FAMILIA_DOWNLOAD_URL`, dans les réglages du dépôt GitHub.
- Seuls le workflow « APK de publication » et toi, depuis la console Firebase, modifiez la version et l'APK annoncés dans `config/app`. Depuis l'appli, l'admin principal ne peut changer que les modules allumés (`features`), et rien d'autre (`firebase/tests/config.test.mjs`).
- L'appli n'installe un APK téléchargé que s'il a exactement la taille et l'empreinte SHA-256 annoncées par le workflow. Sinon, elle l'efface et ouvre le Drive.
- Android vérifie lui-même la signature de l'APK au moment de l'installation : il refuse une mise à jour qui n'est pas signée avec la même clé que l'appli installée. Un faux fichier ne peut donc pas remplacer Familia.

## À faire une seule fois : laisser GitHub déposer l'APK sur le Drive

Le workflow utilise le compte de service `github-deploy`, celui qui publie déjà les règles Firebase. Il faut lui ouvrir le dossier Drive. Un compte de service n'a pas d'espace Drive à lui : il ne peut pas créer de fichier, seulement remplacer le contenu d'un fichier que tu as déposé. C'est pour ça que le dossier doit garder l'APK que tu y as mis la première fois.

1. Sur ordinateur, ouvre le dossier Drive de Familia (le lien du secret `FAMILIA_DOWNLOAD_URL`).
2. Clique sur le nom du dossier en haut, puis **Partager**.
3. Dans « Ajouter des personnes », colle `github-deploy@familia-93286.iam.gserviceaccount.com`.
4. Choisis le rôle **Éditeur**. Décoche **Envoyer une notification**, car cette adresse ne lit pas les e-mails. Clique sur **Partager**.
5. Vérifie qu'il n'y a qu'un seul APK dans le dossier.
6. Pour que l'appli télécharge l'APK elle-même, l'APK doit être partagé à **Tous les utilisateurs disposant du lien**, en Lecteur (clic droit sur l'APK, **Partager**, puis « Accès général »). C'est déjà le cas si le dossier l'est.

## À faire une seule fois : le secret du lien Drive

1. Dans le Drive, ouvre le dossier de Familia et copie son adresse dans la barre du navigateur. Elle ressemble à `https://drive.google.com/drive/folders/…`.
2. Sur GitHub, ouvre le dépôt, puis **Settings**, **Secrets and variables**, **Actions**, et **New repository secret**.
3. Nom : `FAMILIA_DOWNLOAD_URL`. Valeur : l'adresse copiée. Clique sur **Add secret**.

**Attendu :** le workflow « APK de publication » passe l'étape « Check that everything needed is there ». Sans ce secret, il s'arrête et dit qu'il manque.

Si l'adresse du compte de service n'est pas reconnue, retrouve-la dans la console Google Cloud, sous **IAM et administration**, puis **Comptes de service** : c'est la colonne « E-mail » de `github-deploy`.

L'API Google Drive s'active toute seule au premier lancement. Ce lancement attend une minute de plus.

## Publier une version

1. Sur GitHub, ouvre l'onglet **Actions**, choisis **APK de publication** à gauche, puis **Run workflow** (branche `main`).
2. C'est tout. En 5 à 10 minutes, le workflow fait tout seul :
   - il numérote la version, sans rien à modifier ni pull request à faire ;
   - il fabrique et signe l'APK ;
   - il remplace l'APK du dossier Drive, sous le nom `familia-0.2.<numéro>.apk` ; le lien ne change pas ;
   - il annonce la version dans `config/app`, avec l'identifiant, la taille et l'empreinte de l'APK.
   - il envoie aussi les textes de « Quoi de neuf ? » (`docs/whats-new`, voir son README) dans `config/app.whatsNew`. Si un de ces textes a un problème, il s'arrête avant de toucher au Drive : corrige ou retire le fichier signalé, puis relance.

   **Attendu :** à la prochaine ouverture de Familia, chacun voit « Nouvelle version disponible ». « Mettre à jour » télécharge l'APK (une barre montre l'avancement), puis Android propose de l'installer par-dessus l'appli. Les données ne bougent pas, puisqu'elles sont dans Firebase.

### Détails techniques

- **Le numéro de version** (`versionCode`) est le nombre de commits de `main`. Il monte à chaque fusion. Relancer le workflow sans nouvelle fusion est refusé, avec un message clair, car il n'y a rien de nouveau à publier.
- **Le nom de version** est `0.2.<numéro>`. Pour marquer une étape (par exemple 1.0 à Noël), change la base dans `app/build.gradle.kts`.
- **L'ordre des étapes protège la famille :** l'APK est déposé sur le Drive avant d'être annoncé. Si le dépôt échoue, personne ne voit l'encadré.
- **Copie de secours :** l'APK reste téléchargeable 30 jours dans l'artefact **familia-apk** de l'exécution.
- **Les APK de test** des pull requests ont une autre clé et un autre nom (`fr.familia.app.test`). Ils ne peuvent pas remplacer l'appli de la famille.
- **En cas d'échec,** le message dit ce qui manque : un secret, le partage du dossier, ou un deuxième APK dans le dossier.
- **Coût :** environ 8 minutes de GitHub Actions par publication, sous le plafond de 5 $.

« Plus tard » cache l'encadré jusqu'à ce que l'appli revienne à l'écran.

Chacun peut aussi vérifier à la main : dans l'onglet **Famille**, la ligne **Vérifier les mises à jour** interroge directement le serveur. Elle répond « Tu as la dernière version », ou bien elle propose « Mettre à jour ». Hors ligne, elle demande de se connecter à Internet.
