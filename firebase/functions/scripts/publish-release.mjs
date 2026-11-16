// Publishes a release APK for the family (docs/mise-a-jour.md), run by the "APK de publication"
// workflow with the service account key in GOOGLE_APPLICATION_CREDENTIALS:
// 1. refuses a version that is not newer than the one in config/app;
// 2. replaces the APK of the Drive folder, so the download link never changes;
// 3. then announces the version in config/app, so the "Nouvelle version" banner only shows once
//    the file is there, with the Drive file id, size and SHA-256 the app checks when it downloads
//    the APK itself (PRD, récit 6 quater), and the texts of « Quoi de neuf ? » (docs/whats-new,
//    récit 6 septies): a new file takes this version, the others keep theirs.
// The Drive folder must be shared with the service account as editor, and hold exactly one APK,
// uploaded once by the main admin. A service account has no Drive space of its own: it can replace the
// content of a file someone shared, but not create one. No dependency besides firebase-admin.
import { createHash, createSign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { mergeWhatsNew, readWhatsNew } from './whats-new.mjs';

const apkPath = process.env.APK_PATH;
const versionCode = Number(process.env.VERSION_CODE);
const versionName = (process.env.VERSION_NAME ?? '').trim();
const folderId = (process.env.FOLDER_ID ?? '').trim();
const projectId = process.env.PROJECT_ID;
const key = JSON.parse(readFileSync(process.env.GOOGLE_APPLICATION_CREDENTIALS, 'utf8'));

function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

if (!Number.isInteger(versionCode) || versionCode < 1) fail('Le numéro de version est introuvable (étape « Number the version »).');
if (!versionName) fail("Le nom de version est introuvable dans l'APK.");
if (!/^[A-Za-z0-9_-]{10,}$/.test(folderId)) fail("L'identifiant du dossier Drive est introuvable (secret GitHub FAMILIA_DOWNLOAD_URL).");

// Read before anything changes: a bad file stops here, with every problem listed.
let whatsNewFiles;
try {
  whatsNewFiles = readWhatsNew(new URL('../../../docs/whats-new/', import.meta.url));
} catch (error) {
  fail(`Les textes de « Quoi de neuf ? » (docs/whats-new) ont un problème :\n${error.message}`);
}

initializeApp({ projectId });
const configApp = getFirestore().collection('config').doc('app');
const current = await configApp.get();
const published = current.get('latestVersionCode') ?? 0;
if (versionCode <= published) {
  fail(`La version ${published} est déjà annoncée et rien de nouveau n'a été fusionné depuis : il n'y a rien à publier.`);
}

// --- Google access token for the service account (OAuth 2.0 JWT bearer flow) -------------------

async function accessToken() {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const unsigned = `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode({
    iss: key.client_email,
    scope: 'https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/cloud-platform',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  })}`;
  const signature = createSign('RSA-SHA256').update(unsigned).sign(key.private_key, 'base64url');
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!response.ok) fail(`Connexion du compte de service refusée (${response.status}).`);
  return (await response.json()).access_token;
}

const token = await accessToken();
const auth = { authorization: `Bearer ${token}` };

// The Drive API is off in a new Google Cloud project: the service account (Administrateur
// Service Usage) switches it on the first time, then waits for it to take effect.
async function drive(url, init = {}) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const response = await fetch(url, { ...init, headers: { ...auth, ...init.headers } });
    if (response.status !== 403) return response;
    const body = await response.text();
    if (!/SERVICE_DISABLED|accessNotConfigured/.test(body)) return new Response(body, { status: 403 });
    if (attempt === 0) {
      console.log("Première fois : activation de l'API Google Drive dans le projet.");
      const enable = await fetch(
        `https://serviceusage.googleapis.com/v1/projects/${projectId}/services/drive.googleapis.com:enable`,
        { method: 'POST', headers: auth },
      );
      if (!enable.ok) fail(`Impossible d'activer l'API Google Drive (${enable.status}). Active-la à la main (docs/mise-a-jour.md).`);
    }
    await new Promise((resolve) => setTimeout(resolve, 30_000));
  }
  fail("L'API Google Drive ne répond toujours pas. Relance le workflow dans quelques minutes.");
}

// --- Find the APK of the folder and replace it ---------------------------------------------------

const query = encodeURIComponent(`'${folderId}' in parents and trashed = false and name contains '.apk'`);
const list = await drive(`https://www.googleapis.com/drive/v3/files?q=${query}&fields=files(id,name)&pageSize=10`);
if (list.status === 404) fail("Le dossier Drive est introuvable : partage-le avec le compte de service (docs/mise-a-jour.md).");
if (!list.ok) fail(`Le dossier Drive ne répond pas (${list.status}). Vérifie qu'il est partagé avec le compte de service, en Éditeur.`);
const files = (await list.json()).files ?? [];
if (files.length === 0) {
  fail("Aucun APK visible dans le dossier Drive. Partage le dossier avec le compte de service en Éditeur, et garde-y l'APK actuel (docs/mise-a-jour.md).");
}
if (files.length > 1) fail(`Le dossier Drive contient ${files.length} APK. N'en garde qu'un, puis relance.`);

const apk = readFileSync(apkPath);
const boundary = `familia-${Date.now()}`;
const newName = `familia-${versionName}.apk`;
const body = Buffer.concat([
  Buffer.from(`--${boundary}\r\ncontent-type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify({ name: newName })}\r\n`),
  Buffer.from(`--${boundary}\r\ncontent-type: application/vnd.android.package-archive\r\n\r\n`),
  apk,
  Buffer.from(`\r\n--${boundary}--`),
]);
const upload = await drive(`https://www.googleapis.com/upload/drive/v3/files/${files[0].id}?uploadType=multipart&fields=id,name`, {
  method: 'PATCH',
  headers: { 'content-type': `multipart/related; boundary=${boundary}` },
  body,
});
if (upload.status === 403) fail("Le compte de service peut voir le dossier mais pas le modifier : donne-lui le rôle Éditeur.");
if (!upload.ok) fail(`L'envoi de l'APK sur le Drive a échoué (${upload.status}). Rien n'a été annoncé à la famille.`);
console.log(`APK remplacé sur le Drive : ${files[0].name} -> ${newName}.`);

// --- Announce the version ------------------------------------------------------------------------

await configApp.set({
  latestVersionCode: versionCode,
  latestVersionName: versionName,
  apkFileId: files[0].id,
  apkSize: apk.length,
  apkSha256: createHash('sha256').update(apk).digest('hex'),
  whatsNew: mergeWhatsNew(current.get('whatsNew'), whatsNewFiles, versionCode, versionName),
}, { merge: true });
console.log(`Version ${versionName} (${versionCode}) annoncée dans config/app.`);
