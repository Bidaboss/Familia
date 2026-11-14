// Creates the first admin of an empty family: one household, one adult admin member and their
// memberEmails entry, in a single batch. Run once by the "Créer le premier admin" workflow, with
// the service account key in GOOGLE_APPLICATION_CREDENTIALS. Refuses if any member exists, so it
// can never overwrite the family. Nothing about the person is written to the logs.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const firstName = (process.env.FIRST_NAME ?? '').trim();
const email = (process.env.EMAIL ?? '').trim().toLowerCase();
const householdName = (process.env.HOUSEHOLD ?? '').trim();

function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

if (firstName.length < 1 || firstName.length > 40) fail('Le prénom doit faire de 1 à 40 caractères.');
if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail("L'adresse Google n'a pas la bonne forme.");
if (householdName.length < 1 || householdName.length > 40) fail('Le nom du foyer doit faire de 1 à 40 caractères.');

initializeApp({ projectId: process.env.PROJECT_ID });
const db = getFirestore();

const existing = await db.collection('members').limit(1).get();
if (!existing.empty) fail("La famille a déjà des membres : ajoute les suivants depuis l'appli.");

const household = db.collection('households').doc();
const member = db.collection('members').doc();
const batch = db.batch();
batch.set(household, { name: householdName });
batch.set(member, {
  firstName,
  email,
  birthDate: null,
  householdId: household.id,
  profile: 'adult',
  isAdmin: true,
  parentIds: [],
  uid: null,
});
batch.set(db.collection('memberEmails').doc(email), { memberId: member.id });
await batch.commit();
console.log('Premier admin créé. Tu peux te connecter dans l’appli avec cette adresse Google.');
