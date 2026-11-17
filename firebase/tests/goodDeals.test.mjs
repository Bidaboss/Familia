// Rules of the bons plans et bonnes adresses (PRD, récit 45). Fictional family: admin1 (admin
// principal), adult1, adult2 in h1, adult3 in h2; kid1, a child with the app (parent adult1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { arrayRemove, arrayUnion, deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const DAY = 24 * 60 * 60 * 1000;

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
  });
});

/** A valid new bon plan by [author], ending in 7 days. */
function deal(author, overrides = {}) {
  return {
    kind: 'deal',
    title: 'Pneus à -30 %',
    text: 'Jusqu\'à dimanche au garage de la gare.',
    link: 'https://exemple.fr/promo',
    place: 'Garage de la gare',
    phone: null,
    category: null,
    endsAt: Timestamp.fromMillis(Date.now() + 7 * DAY),
    photo: null,
    interestedIds: [],
    recommendedIds: [],
    authorId: author.memberId,
    authorName: author.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

/** A valid new bonne adresse by [author]. */
function address(author, overrides = {}) {
  return deal(author, {
    kind: 'address',
    title: 'Dr Martin, dentiste',
    text: 'Très doux avec les enfants.',
    link: null,
    place: 'Lyon',
    phone: '04 78 00 00 00',
    category: 'health',
    endsAt: null,
    ...overrides,
  });
}

async function stored(id, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `goodDeals/${id}`), {
      ...data, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

describe('publish a bon plan or a bonne adresse (récit 45)', () => {
  test('an adult publishes in their own name, and the whole family reads it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'goodDeals/d1'), deal(ADULT)));
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'goodDeals/a1'), address(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'goodDeals/d1')));
    await assertSucceeds(getDoc(doc(as(env, KID), 'goodDeals/a1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'goodDeals/d1')));
  });

  test('a child sees everything but does not publish', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'goodDeals/d1'), deal(KID)));
    await assertFails(setDoc(doc(as(env, KID), 'goodDeals/a1'), address(KID)));
  });

  test('a bon plan ends within 3 months, never in the past', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'goodDeals/d1'), deal(ADULT, { endsAt: Timestamp.fromMillis(Date.now() + 90 * DAY) })));
    await assertFails(setDoc(doc(db, 'goodDeals/d2'), deal(ADULT, { endsAt: Timestamp.fromMillis(Date.now() + 93 * DAY) })));
    await assertFails(setDoc(doc(db, 'goodDeals/d3'), deal(ADULT, { endsAt: Timestamp.fromMillis(Date.now() - DAY) })));
    await assertFails(setDoc(doc(db, 'goodDeals/d4'), deal(ADULT, { endsAt: null })));
  });

  test('each kind keeps to its own fields', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'goodDeals/d1'), deal(ADULT, { category: 'health' })));
    await assertFails(setDoc(doc(db, 'goodDeals/d2'), deal(ADULT, { phone: '0600000000' })));
    await assertFails(setDoc(doc(db, 'goodDeals/a1'), address(ADULT, { endsAt: Timestamp.fromMillis(Date.now() + DAY) })));
    await assertFails(setDoc(doc(db, 'goodDeals/a2'), address(ADULT, { category: 'secret' })));
    await assertFails(setDoc(doc(db, 'goodDeals/a3'), address(ADULT, { photo: 'goodDeals/a3/adult1/abc' })));
    await assertFails(setDoc(doc(db, 'goodDeals/a4'), address(ADULT, { kind: 'other' })));
  });

  test('not in the name of someone else, not empty, no odd link, phone or field', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'goodDeals/d1'), deal(OTHER_ADULT)));
    await assertFails(setDoc(doc(db, 'goodDeals/d2'), deal(ADULT, { title: ' ' })));
    await assertFails(setDoc(doc(db, 'goodDeals/d3'), deal(ADULT, { text: 'x'.repeat(501) })));
    await assertFails(setDoc(doc(db, 'goodDeals/d4'), deal(ADULT, { link: 'javascript:alert(1)' })));
    await assertFails(setDoc(doc(db, 'goodDeals/d5'), deal(ADULT, { interestedIds: ['adult2'] })));
    await assertFails(setDoc(doc(db, 'goodDeals/d6'), deal(ADULT, { secret: true })));
    await assertFails(setDoc(doc(db, 'goodDeals/a1'), address(ADULT, { phone: 'appelle-moi' })));
    await assertSucceeds(setDoc(doc(db, 'goodDeals/a2'), address(ADULT, { phone: null, place: null, text: '' })));
  });

  test('the photo of a bon plan is one the author sent to their own folder of this deal', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'goodDeals/d1'), deal(ADULT, { photo: 'goodDeals/d1/adult1/abc123' })));
    await assertFails(setDoc(doc(db, 'goodDeals/d2'), deal(ADULT, { photo: 'goodDeals/d2/adult2/abc123' })));
    await assertFails(setDoc(doc(db, 'goodDeals/d3'), deal(ADULT, { photo: 'goodDeals/d1/adult1/abc123' })));
  });
});

describe('change, remove and mark a bon plan (récit 45)', () => {
  test('the author or the admin principal edits and removes it, nobody else', async () => {
    await stored('d1', deal(ADULT));
    const edit = { title: 'Pneus à -40 %', updatedAt: serverTimestamp() };
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'goodDeals/d1'), edit));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'goodDeals/d1'), edit));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'goodDeals/d1'), edit));
    await assertFails(updateDoc(doc(as(env, ADULT), 'goodDeals/d1'), { kind: 'address', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'goodDeals/d1'), { interestedIds: ['adult2'], updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'goodDeals/d1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'goodDeals/d1')));
  });

  test('an ended bon plan can still be edited, but its end cannot move into the past', async () => {
    await stored('d1', deal(ADULT, { endsAt: Timestamp.fromMillis(Date.now() - DAY) }));
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'goodDeals/d1'), { text: 'C\'est fini.', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'goodDeals/d1'), { endsAt: Timestamp.fromMillis(Date.now() - 2 * DAY), updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'goodDeals/d1'), { endsAt: Timestamp.fromMillis(Date.now() + DAY), updatedAt: serverTimestamp() }));
  });

  test('« Ça m\'intéresse »: each member, children too, adds or removes only their own name', async () => {
    await stored('d1', deal(ADULT));
    await assertSucceeds(updateDoc(doc(as(env, KID), 'goodDeals/d1'), { interestedIds: arrayUnion('kid1') }));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'goodDeals/d1'), { interestedIds: arrayUnion('adult2') }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'goodDeals/d1'), { interestedIds: arrayRemove('kid1') }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'goodDeals/d1'), { interestedIds: ['kid1', 'adult2', 'adult3', 'admin1'] }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'goodDeals/d1'), { interestedIds: arrayUnion('adult3'), title: 'Autre' }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'goodDeals/d1'), { recommendedIds: arrayUnion('adult3') }));
    await assertSucceeds(updateDoc(doc(as(env, KID), 'goodDeals/d1'), { interestedIds: arrayRemove('kid1') }));
  });

  test('« Je recommande aussi »: an adult but the author adds or removes their own name', async () => {
    await stored('a1', address(ADULT));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'goodDeals/a1'), { recommendedIds: arrayUnion('adult3') }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'goodDeals/a1'), { recommendedIds: arrayUnion('adult1') }));
    await assertFails(updateDoc(doc(as(env, KID), 'goodDeals/a1'), { recommendedIds: arrayUnion('kid1') }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'goodDeals/a1'), { interestedIds: arrayUnion('adult2') }));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'goodDeals/a1'), { recommendedIds: arrayRemove('adult3') }));
  });
});
