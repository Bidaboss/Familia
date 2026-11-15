// Bonnes nouvelles rules (PRD, récit 32, carte AVIS-12). Fictional family: adult1, adult2 and admin2
// (chef) in h1, admin1 (admin principal), adult3 in h2; kid1 (child with the app, parent adult1) and
// kid2 (child without the app, parents adult2 and admin1), both in h1.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  arrayRemove, arrayUnion, collection, deleteDoc, deleteField, doc, getDoc, getDocs, limit, orderBy, query, serverTimestamp, setDoc,
  Timestamp, updateDoc,
} from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

/** A valid new good news by [by], for [forMemberId] (themselves by default). */
function news(by, overrides = {}) {
  return {
    text: 'Premier tour de vélo sans roulettes', photoIds: ['p1', 'p2'],
    authorId: by.memberId, authorName: by.memberId, forMemberId: by.memberId, householdId: 'h1',
    mentionIds: [], reactions: {}, hiddenByIds: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    ...overrides,
  };
}

const ref = (db, id = 'n1') => doc(db, `goodNews/${id}`);

/** Writes a good news without the rules, as if it was published earlier. */
async function stored(data, id = 'n1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(ref(ctx.firestore(), id), {
      ...data, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult2', 'admin1'],
    }));
  });
});

describe('publish', () => {
  test('a member publishes their own good news, with the members named', async () => {
    await assertSucceeds(setDoc(ref(as(env, ADULT)), news(ADULT, { mentionIds: ['kid1', 'adult2'] })));
  });

  test('a child with the app publishes their own, without validation (AVIS-12)', async () => {
    await assertSucceeds(setDoc(ref(as(env, KID)), news(KID)));
  });

  test('an adult publishes for a child of their household, not for an adult or another household', async () => {
    await assertSucceeds(setDoc(ref(as(env, ADULT)), news(ADULT, { forMemberId: 'kid2' })));
    await assertFails(setDoc(ref(as(env, ADULT), 'n2'), news(ADULT, { forMemberId: 'adult2' })));
    await assertFails(setDoc(ref(as(env, NEIGHBOUR), 'n3'), news(NEIGHBOUR, { forMemberId: 'kid2' })));
    await assertFails(setDoc(ref(as(env, KID), 'n4'), news(KID, { forMemberId: 'kid2' })));
  });

  test('nobody publishes in the name of someone else', async () => {
    await assertFails(setDoc(ref(as(env, ADULT)), news(ADULT, { authorId: 'adult2' })));
    await assertFails(setDoc(ref(as(env, ADULT)), news(ADULT, { authorName: 'Quelqu’un' })));
  });

  test('someone outside the family or with an unverified address is refused', async () => {
    await assertFails(setDoc(ref(as(env, STRANGER)), news({ memberId: 'stranger' })));
    await assertFails(setDoc(ref(as(env, ADULT, { verified: false })), news(ADULT)));
  });

  test('a text and one to three photos, nothing else (récit 32)', async () => {
    const db = as(env, ADULT);
    for (const bad of [
      { text: '' }, { text: '   ' }, { text: 'x'.repeat(501) }, { photoIds: [] }, { photoIds: ['a', 'b', 'c', 'd'] },
      { photoIds: ['../x'] }, { photoIds: [3] }, { householdId: 'h2' }, { reactions: { adult1: 'heart' } },
      { mentionIds: 'kid1' }, { createdAt: Timestamp.fromMillis(0) }, { extra: true },
    ]) {
      await assertFails(setDoc(ref(db), news(ADULT, bad)), JSON.stringify(bad));
    }
    await assertSucceeds(setDoc(ref(db), news(ADULT, { text: 'x'.repeat(500), photoIds: ['a', 'b_2', 'C3'] })));
  });
});

describe('read', () => {
  test('the whole family reads the good news, newest first, by pages of 20', async () => {
    await stored(news(ADULT));
    await stored(news(NEIGHBOUR, { householdId: 'h2' }), 'n22');
    await assertSucceeds(getDoc(ref(as(env, KID))));
    const page = await assertSucceeds(getDocs(query(collection(as(env, NEIGHBOUR), 'goodNews'), orderBy('createdAt', 'desc'), limit(20))));
    if (page.size !== 2) throw new Error(`expected 2 good news, got ${page.size}`);
  });

  test('nobody outside the family reads them', async () => {
    await stored(news(ADULT));
    await assertFails(getDoc(ref(as(env, STRANGER))));
    await assertFails(getDoc(ref(env.unauthenticatedContext().firestore())));
  });
});

describe('react', () => {
  test('a member sets, changes and removes their own reaction', async () => {
    await stored(news(ADULT));
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(ref(db), { 'reactions.adult2': 'heart' }));
    await assertSucceeds(updateDoc(ref(db), { 'reactions.adult2': 'party' }));
    await assertSucceeds(updateDoc(ref(db), { 'reactions.adult2': deleteField() }));
    await assertSucceeds(updateDoc(ref(as(env, ADULT)), { 'reactions.adult1': 'clap' }));
  });

  test('only the six fixed reactions, and only one\'s own', async () => {
    await stored(news(ADULT, { reactions: { adult1: 'wow' } }));
    const db = as(env, OTHER_ADULT);
    await assertFails(updateDoc(ref(db), { 'reactions.adult2': 'fire' }));
    await assertFails(updateDoc(ref(db), { 'reactions.adult2': '❤️' }));
    await assertFails(updateDoc(ref(db), { 'reactions.adult1': deleteField() }));
    await assertFails(updateDoc(ref(db), { 'reactions.kid1': 'heart' }));
    await assertFails(updateDoc(ref(db), { 'reactions.adult2': 'heart', text: 'Autre chose' }));
    await assertFails(updateDoc(ref(as(env, STRANGER)), { 'reactions.stranger': 'heart' }));
  });
});

describe('hide for me (PRD rév. 158)', () => {
  test('the member it is for and the members named hide it for themselves, then show it again', async () => {
    await stored(news(ADULT, { forMemberId: 'kid1', mentionIds: ['adult2'] }));
    await assertSucceeds(updateDoc(ref(as(env, KID)), { hiddenByIds: arrayUnion('kid1') }));
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), { hiddenByIds: arrayUnion('adult2') }));
    await assertSucceeds(updateDoc(ref(as(env, KID)), { hiddenByIds: arrayRemove('kid1') }));
  });

  test('only one\'s own id, only when named, and nothing else changes', async () => {
    await stored(news(ADULT, { forMemberId: 'kid1', mentionIds: ['adult2'], hiddenByIds: ['adult2'] }));
    await assertFails(updateDoc(ref(as(env, KID)), { hiddenByIds: arrayUnion('adult1') }));
    await assertFails(updateDoc(ref(as(env, KID)), { hiddenByIds: arrayRemove('adult2') }));
    await assertFails(updateDoc(ref(as(env, ADMIN)), { hiddenByIds: arrayUnion('admin1') }));
    await assertFails(updateDoc(ref(as(env, KID)), { hiddenByIds: arrayUnion('kid1'), text: 'Autre' }));
  });

  test('a new good news starts with nobody hiding it', async () => {
    await assertFails(setDoc(ref(as(env, ADULT)), news(ADULT, { hiddenByIds: ['adult1'] })));
  });
});

describe('modify', () => {
  test('the author changes the text, the photos and the members named', async () => {
    await stored(news(ADULT));
    await assertSucceeds(updateDoc(ref(as(env, ADULT)), {
      text: 'Et sans tomber', photoIds: ['p1'], mentionIds: ['kid1'], updatedAt: serverTimestamp(),
    }));
  });

  test('nobody else modifies it, and the author never changes who it is for or when it was made', async () => {
    await stored(news(ADULT, { forMemberId: 'kid2' }));
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { text: 'Autre', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(as(env, ADMIN)), { text: 'Autre', updatedAt: serverTimestamp() }));
    const db = as(env, ADULT);
    await assertFails(updateDoc(ref(db), { forMemberId: 'adult1', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(db), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(db), { photoIds: [], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(db), { text: 'Sans la date' }));
  });
});

describe('remove', () => {
  test('the author removes their own good news', async () => {
    await stored(news(OTHER_ADULT));
    await assertSucceeds(deleteDoc(ref(as(env, OTHER_ADULT))));
  });

  test('the responsible adults of a child remove what the child published (AVIS-12)', async () => {
    await stored(news(KID));
    await assertFails(deleteDoc(ref(as(env, OTHER_ADULT))));
    await assertSucceeds(deleteDoc(ref(as(env, ADULT))));
  });

  test('the responsible adults of the child it is for remove it too', async () => {
    await stored(news(ADULT, { forMemberId: 'kid2' }));
    await assertSucceeds(deleteDoc(ref(as(env, OTHER_ADULT))));
  });

  test('a chef de famille removes in their household only, the admin principal everywhere', async () => {
    await stored(news(ADULT));
    await stored(news(NEIGHBOUR, { householdId: 'h2' }), 'n2');
    await assertFails(deleteDoc(ref(as(env, SECOND_ADMIN), 'n2')));
    await assertSucceeds(deleteDoc(ref(as(env, SECOND_ADMIN))));
    await assertSucceeds(deleteDoc(ref(as(env, ADMIN), 'n2')));
  });

  test('another member does not remove it', async () => {
    await stored(news(ADULT));
    await assertFails(deleteDoc(ref(as(env, OTHER_ADULT))));
    await assertFails(deleteDoc(ref(as(env, KID))));
    await assertFails(deleteDoc(ref(as(env, NEIGHBOUR))));
  });
});

describe('grouped reactions (PRD rév. 174)', () => {
  test('goodNewsDigests stays closed to the app: only the Cloud Functions use it', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'goodNewsDigests/n1'), { authorId: 'adult1', notifiedIds: [] });
    });
    await assertFails(getDoc(doc(as(env, ADULT), 'goodNewsDigests/n1')));
    await assertFails(setDoc(doc(as(env, ADULT), 'goodNewsDigests/n2'), { authorId: 'adult1' }));
  });
});

describe('notification setting', () => {
  test('a member turns the good news notifications off, alone or with the reminder', async () => {
    const settings = doc(as(env, ADULT), 'members/adult1/settings/notifications');
    await assertSucceeds(setDoc(settings, { goodNews: false }));
    await assertSucceeds(setDoc(settings, { dayBeforeReminder: true, goodNews: true }));
    await assertFails(setDoc(settings, { goodNews: 'non' }));
  });
});
