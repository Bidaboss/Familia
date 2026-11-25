// Lot 4 rules: events and the PRD "Droits par profil". Fictional family:
// admin1 (admin), adult1 and adult2 in h1, adult3 in h2, kid1 (child with the app, parent adult1),
// kid2 (child without the app, parents adult2 and admin1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, deleteField, doc, getDoc, serverTimestamp, setDoc, updateDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const ADULT3 = { memberId: 'adult3', email: 'adult3@example.com', uid: 'uid-adult3' };
const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'households/h2'), { name: 'Foyer 2' });
    await setDoc(doc(db, `memberEmails/${ADULT3.email}`), { memberId: ADULT3.memberId });
    await setDoc(doc(db, `members/${ADULT3.memberId}`), adultDoc(ADULT3, { householdId: 'h2' }));
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult2', 'admin1'],
    }));
  });
});

/** A valid new event by [author]; override the target or any field. */
function event(author, overrides = {}) {
  return {
    title: 'Repas',
    date: '2026-12-24',
    time: '19:30',
    place: null,
    note: null,
    targetType: 'family',
    targetMemberId: null,
    householdId: null,
    authorId: author.memberId,
    authorName: author.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

/** Stores an event directly, as if [author] had created it earlier. */
async function stored(id, author, overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `events/${id}`), {
      ...event(author, overrides),
      createdAt: Timestamp.fromMillis(0),
      updatedAt: Timestamp.fromMillis(0),
    });
  });
}

const self = (m) => ({ targetType: 'self', targetMemberId: m.memberId });
const forChild = (id) => ({ targetType: 'child', targetMemberId: id });
const forHousehold = (id) => ({ targetType: 'household', householdId: id });

describe('reading', () => {
  test('every member reads the planning, a stranger does not', async () => {
    await stored('e1', ADULT);
    await assertSucceeds(getDoc(doc(as(env, KID), 'events/e1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'events/e1')));
  });
});

describe('« @Prénom » in the note (l’admin, 19/11)', () => {
  test('an event names up to 20 members, each once, and its author changes them', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'events/m1'), event(ADULT, { note: '@Noé le pain', mentionIds: [OTHER_ADULT.memberId] })));
    await assertFails(setDoc(doc(db, 'events/m2'), event(ADULT, { mentionIds: ['a', 'a'] })));
    await assertFails(setDoc(doc(db, 'events/m3'), event(ADULT, { mentionIds: Array.from({ length: 21 }, (_, i) => `m${i}`) })));
    await stored('m4', ADULT);
    await assertSucceeds(updateDoc(doc(db, 'events/m4'), { note: '@Noé', mentionIds: [OTHER_ADULT.memberId], updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'events/m4'), { note: null, mentionIds: deleteField(), updatedAt: serverTimestamp() }));
  });
});

describe('creating', () => {
  test('an adult creates for themselves, their household, the family and their child', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'events/a'), event(ADULT, self(ADULT))));
    await assertSucceeds(setDoc(doc(db, 'events/b'), event(ADULT, forHousehold('h1'))));
    await assertSucceeds(setDoc(doc(db, 'events/c'), event(ADULT)));
    await assertSucceeds(setDoc(doc(db, 'events/d'), event(ADULT, forChild('kid1'))));
  });

  test('an adult cannot create for another household or for someone else\'s child', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/a'), event(ADULT, forHousehold('h2'))));
    await assertFails(setDoc(doc(db, 'events/b'), event(ADULT, forChild('kid2'))));
    await assertFails(setDoc(doc(db, 'events/c'), event(ADULT, forChild('adult2'))));
  });

  test('a child with the app creates for themselves only', async () => {
    const db = as(env, KID);
    await assertSucceeds(setDoc(doc(db, 'events/a'), event(KID, self(KID))));
    await assertFails(setDoc(doc(db, 'events/b'), event(KID)));
    await assertFails(setDoc(doc(db, 'events/c'), event(KID, forHousehold('h1'))));
  });

  test('an admin creates for any household or child', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'events/a'), event(ADMIN, forHousehold('h2'))));
    await assertSucceeds(setDoc(doc(db, 'events/b'), event(ADMIN, forChild('kid1'))));
  });

  test('nobody creates in someone else\'s name', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'events/a'), event(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, STRANGER), 'events/b'), event({ memberId: 'stranger' })));
  });

  test('the author name is the author\'s own first name', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'events/a'), event(KID, { ...self(KID), authorName: 'Maman' })));
  });

  test('a title and a date are required, and the shape is checked', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/a'), event(ADULT, { title: '' })));
    await assertFails(setDoc(doc(db, 'events/b'), event(ADULT, { date: null })));
    await assertFails(setDoc(doc(db, 'events/c'), event(ADULT, { date: '24/12/2026' })));
    await assertFails(setDoc(doc(db, 'events/d'), event(ADULT, { time: '25:00' })));
    await assertFails(setDoc(doc(db, 'events/e'), event(ADULT, { extra: true })));
    await assertFails(setDoc(doc(db, 'events/f'), event(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(doc(db, 'events/g'), event(ADULT, { targetType: 'self', targetMemberId: 'adult2' })));
  });
});

describe('editing and deleting', () => {
  test('the author edits and deletes their event', async () => {
    await stored('e1', OTHER_ADULT);
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'events/e1')));
  });

  test('another adult cannot edit or delete it', async () => {
    await stored('e1', OTHER_ADULT);
    const db = as(env, ADULT3);
    await assertFails(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'events/e1')));
  });

  test('an admin edits and deletes any event', async () => {
    await stored('e1', OTHER_ADULT);
    const db = as(env, ADMIN);
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'events/e1')));
  });

  test('a parent manages the events their child wrote and those written for their child', async () => {
    await stored('byKid', KID, self(KID));
    await stored('forKid', ADMIN, forChild('kid1'));
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'events/byKid'), { time: null, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'events/forKid')));
  });

  test('a child cannot touch an adult\'s event, even outside the app', async () => {
    await stored('e1', ADULT);
    const db = as(env, KID);
    await assertFails(updateDoc(doc(db, 'events/e1'), { title: 'Piscine', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'events/e1')));
  });

  test('a child cannot widen their own event to the whole family', async () => {
    await stored('e1', KID, self(KID));
    await assertFails(updateDoc(doc(as(env, KID), 'events/e1'), {
      targetType: 'family', targetMemberId: null, updatedAt: serverTimestamp(),
    }));
  });

  test('the author, author name and creation time never change', async () => {
    await stored('e1', ADULT);
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'events/e1'), { authorId: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'events/e1'), { authorName: 'Autre', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'events/e1'), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'events/e1'), { title: 'Sans date de modif' }));
  });

  test('the events of a removed member stay readable, and an admin can still manage them', async () => {
    await stored('e1', OTHER_ADULT);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), 'members/adult2'));
      await deleteDoc(doc(ctx.firestore(), 'memberEmails/adult2@example.com'));
    });
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'events/e1')));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'events/e1')));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'events/e1'), { note: 'Gardé', updatedAt: serverTimestamp() }));
  });
});

describe('cover photo (récit 44)', () => {
  const cover = (eventId, member, photo = 'c1') => `events/${eventId}/cover/${member}/${photo}`;

  test('the author adds a cover of their own folder when creating, then changes and removes it', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'events/n1'), event(ADULT, { coverPhoto: cover('n1', 'adult1') })));
    await assertSucceeds(updateDoc(doc(db, 'events/n1'), { coverPhoto: cover('n1', 'adult1', 'c2'), updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'events/n1'), { coverPhoto: null, updatedAt: serverTimestamp() }));
  });

  test('a manager of the event changes it; another adult, even without touching the rest, cannot', async () => {
    await stored('e1', OTHER_ADULT, { coverPhoto: cover('e1', 'adult2') });
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'events/e1'), { coverPhoto: cover('e1', 'admin1'), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT3), 'events/e1'), { coverPhoto: cover('e1', 'adult3'), updatedAt: serverTimestamp() }));
  });

  test('editing the rest of the event keeps the cover named by someone else', async () => {
    await stored('e1', OTHER_ADULT, { coverPhoto: cover('e1', 'adult2') });
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
  });

  test('never a photo of someone else, of another event, or another file', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/n1'), event(ADULT, { coverPhoto: cover('n1', 'adult2') })));
    await assertFails(setDoc(doc(db, 'events/n1'), event(ADULT, { coverPhoto: cover('n2', 'adult1') })));
    await assertFails(setDoc(doc(db, 'events/n1'), event(ADULT, { coverPhoto: 'goodNews/adult1/p1/c1' })));
    await assertFails(setDoc(doc(db, 'events/n1'), event(ADULT, { coverPhoto: `${cover('n1', 'adult1')}.jpg` })));
    await assertFails(setDoc(doc(db, 'events/n1'), event(ADULT, { coverPhoto: 42 })));
  });
});

describe('place (récit 74)', () => {
  test('a place is « Autre » text, an address or a household of the family', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'events/p1'), event(ADULT, { place: 'Au parc' })));
    await assertSucceeds(setDoc(doc(db, 'events/p2'), event(ADULT, { place: '3 rue des Lilas, Nantes', placeKind: 'address' })));
    // Another household than one's own: the place is not the target.
    await assertSucceeds(setDoc(doc(db, 'events/p3'), event(ADULT, { place: 'Foyer 2', placeKind: 'household', placeHouseholdId: 'h2' })));
    await assertSucceeds(setDoc(doc(db, 'events/p4'), event(ADULT, { placeKind: null, placeHouseholdId: null })));
  });

  test('an unknown household, a missing one or a wrong shape is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/p1'), event(ADULT, { place: 'Foyer 9', placeKind: 'household', placeHouseholdId: 'h9' })));
    await assertFails(setDoc(doc(db, 'events/p2'), event(ADULT, { place: 'Foyer 2', placeKind: 'household' })));
    await assertFails(setDoc(doc(db, 'events/p3'), event(ADULT, { place: 'Foyer 2', placeKind: 'address', placeHouseholdId: 'h2' })));
    await assertFails(setDoc(doc(db, 'events/p4'), event(ADULT, { place: null, placeKind: 'address' })));
    await assertFails(setDoc(doc(db, 'events/p5'), event(ADULT, { place: 'Ici', placeKind: 'garden' })));
    await assertFails(setDoc(doc(db, 'events/p6'), event(ADULT, { place: 'Ici', placeHouseholdId: 'h2' })));
  });

  test('an event of before keeps working, and its place can become a household and back', async () => {
    await stored('e1', ADULT, { place: 'Chez nous' });
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), {
      place: 'Foyer 1', placeKind: 'household', placeHouseholdId: 'h1', updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), {
      place: 'Au parc', placeKind: deleteField(), placeHouseholdId: deleteField(), updatedAt: serverTimestamp(),
    }));
  });
});
