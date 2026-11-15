// Names the admin principal (PRD, récit 1 ter), in config/family, which nothing else writes.
// Run by hand by the "Créer le premier admin" workflow, with the service account key in
// GOOGLE_APPLICATION_CREDENTIALS. Two cases:
// - empty family: creates one household, one adult member (chef de famille of that household)
//   with their memberEmails entry, and names them admin principal, in a single batch;
// - family with members: the address must belong to an adult member, who becomes admin
//   principal again (lost access, or the switch from the two-admin model). Nobody else changes.
// Nothing about the person is written to the logs.
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const firstName = (process.env.FIRST_NAME ?? '').trim();
const email = (process.env.EMAIL ?? '').trim().toLowerCase();
const householdName = (process.env.HOUSEHOLD ?? '').trim();

function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail("L'adresse Google n'a pas la bonne forme.");

initializeApp({ projectId: process.env.PROJECT_ID });
const db = getFirestore();
const config = db.collection('config').doc('family');

const existing = await db.collection('members').limit(1).get();
if (!existing.empty) {
  const index = await db.collection('memberEmails').doc(email).get();
  const memberId = index.exists ? index.get('memberId') : null;
  const member = memberId ? await db.collection('members').doc(memberId).get() : null;
  if (!member?.exists) fail("Cette adresse n'est pas celle d'un membre de la famille.");
  if (member.get('profile') !== 'adult') fail("L'admin principal doit être un adulte.");
  const batch = db.batch();
  batch.set(config, { ownerMemberId: member.id });
  batch.update(member.ref, { isAdmin: true });
  await batch.commit();
  console.log('Admin principal nommé. Rouvre l’appli pour voir le menu Administration.');
  process.exit(0);
}

if (firstName.length < 1 || firstName.length > 40) fail('Le prénom doit faire de 1 à 40 caractères.');
if (householdName.length < 1 || householdName.length > 40) fail('Le nom du foyer doit faire de 1 à 40 caractères.');

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
batch.set(config, { ownerMemberId: member.id });
await batch.commit();
console.log('Premier admin créé. Tu peux te connecter dans l’appli avec cette adresse Google.');
