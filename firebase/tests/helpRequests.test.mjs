// Coups de main rules (PRD, récits 13 à 18). Fictional family: adult1, adult2 and admin2 (chef) in
// h1, admin1 (admin principal), adult3 in h2; kid1 (child with the app, parent adult1) and kid2
// (child without the app, parents adult2 and admin1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, runTransaction, serverTimestamp, setDoc, Timestamp,
  updateDoc, where,
} from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

/** A day [offset] days from today, as AAAA-MM-JJ. */
function day(offset) {
  return new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);
}

/** A valid new request by [by], for themselves, sent to [notifyIds]. */
function request(by, notifyIds, overrides = {}) {
  const authorId = overrides.authorId ?? by.memberId;
  return {
    kind: 'childcare', title: 'Garder les enfants', date: day(3), startTime: '18:00', endTime: '22:00',
    place: null, note: null, authorId, authorName: authorId, forMemberId: authorId,
    notifyIds, viewerIds: [authorId, ...notifyIds],
    status: 'open', volunteerId: null, volunteerName: null, declinedBy: null, thankedAt: null,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    ...overrides,
  };
}

const ref = (db, id = 'r1') => doc(db, `helpRequests/${id}`);

/** Writes a request without the rules, as if it was created earlier. */
async function stored(data, id = 'r1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(ref(ctx.firestore(), id), {
      ...data, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

const now = () => ({ updatedAt: serverTimestamp() });

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

describe('asking for help (récit 13)', () => {
  test('an adult asks two people for themselves', async () => {
    await assertSucceeds(setDoc(ref(as(env, ADULT)), request(ADULT, ['adult2', 'adult3'])));
  });

  test('an adult asks for their child, the other parent is warned', async () => {
    const r = request(OTHER_ADULT, ['admin1'], { forMemberId: 'kid2', title: 'Qui peut garder kid2 ?' });
    await assertSucceeds(setDoc(ref(as(env, OTHER_ADULT)), r));
  });

  test('the request of a child must warn all their parents (récit 14)', async () => {
    const r = request(OTHER_ADULT, ['adult1'], { forMemberId: 'kid2' });
    await assertFails(setDoc(ref(as(env, OTHER_ADULT)), r));
  });

  test('a child with the app asks for themselves, their parent warned', async () => {
    await assertSucceeds(setDoc(ref(as(env, KID)), request(KID, ['adult1', 'adult2'])));
    await assertFails(setDoc(ref(as(env, KID), 'r2'), request(KID, ['adult2'])));
  });

  test('a child cannot ask for someone else', async () => {
    await assertFails(setDoc(ref(as(env, KID)), request(KID, ['adult1'], { forMemberId: 'adult2' })));
  });

  test('an adult cannot ask for another adult nor for a child who is not theirs', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), request(ADULT, ['adult3'], { forMemberId: 'adult2' })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2', 'admin1'], { forMemberId: 'kid2' })));
  });

  test('the admin principal asks for any member', async () => {
    const r = request(ADMIN, ['adult2'], { forMemberId: 'kid2' });
    await assertSucceeds(setDoc(ref(as(env, ADMIN)), r));
  });

  test('a missing title, a bad date or a past date is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { title: '' })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { date: '2026-13-01' })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { date: day(-3) })));
    await assertSucceeds(setDoc(ref(db), request(ADULT, ['adult2'], { date: day(0) })));
  });

  test('times are optional, and the end comes after the start', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(ref(db, 'a'), request(ADULT, ['adult2'], { startTime: null, endTime: null })));
    await assertSucceeds(setDoc(ref(db, 'b'), request(ADULT, ['adult2'], { endTime: null })));
    await assertFails(setDoc(ref(db, 'c'), request(ADULT, ['adult2'], { startTime: '20:00', endTime: '19:00' })));
    await assertFails(setDoc(ref(db, 'd'), request(ADULT, ['adult2'], { startTime: '25:00' })));
  });

  test('an unknown kind or an extra field is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { kind: 'cooking' })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { color: 'red' })));
  });

  test('at least one person is warned, never the author', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), request(ADULT, [])));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult1', 'adult2'])));
  });

  test('viewerIds is exactly the author and the people warned', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { viewerIds: ['adult1', 'adult2', 'adult3'] })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { viewerIds: ['adult2'] })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { viewerIds: ['adult1', 'adult2', 'adult2'] })));
  });

  test('a new request is open, without volunteer, and signed by its author', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { status: 'taken', volunteerId: 'adult2', volunteerName: 'adult2' })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { authorId: 'adult2', notifyIds: ['adult3'], viewerIds: ['adult2', 'adult3'] })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { authorName: 'Quelqu’un' })));
    await assertFails(setDoc(ref(db), request(ADULT, ['adult2'], { declinedBy: 'adult2' })));
  });

  test('a stranger cannot ask', async () => {
    await assertFails(setDoc(ref(as(env, STRANGER)), request({ memberId: 'x' }, ['adult1'])));
  });
});

describe('seeing the requests (récit 15)', () => {
  beforeEach(async () => { await stored(request(ADULT, ['adult2'])); });

  test('the author and the people warned read it', async () => {
    await assertSucceeds(getDoc(ref(as(env, ADULT))));
    await assertSucceeds(getDoc(ref(as(env, OTHER_ADULT))));
  });

  test('the others do not, not even a chef de famille', async () => {
    await assertFails(getDoc(ref(as(env, NEIGHBOUR))));
    await assertFails(getDoc(ref(as(env, SECOND_ADMIN))));
    await assertFails(getDoc(ref(as(env, KID))));
    await assertFails(getDoc(ref(as(env, STRANGER))));
  });

  test('the admin principal reads every request (backup)', async () => {
    await assertSucceeds(getDoc(ref(as(env, ADMIN))));
  });

  test('the tab query on viewerIds is allowed, a query of everything is not', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(getDocs(query(collection(db, 'helpRequests'), where('viewerIds', 'array-contains', 'adult2'))));
    await assertFails(getDocs(collection(db, 'helpRequests')));
  });
});

describe('« Je m’en occupe » (récit 16)', () => {
  beforeEach(async () => { await stored(request(ADULT, ['adult2', 'kid1'])); });

  const take = (by) => ({ status: 'taken', volunteerId: by.memberId, volunteerName: by.memberId, ...now() });

  test('an adult who was warned takes the request', async () => {
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), take(OTHER_ADULT)));
  });

  test('a child does not take a request, even when warned', async () => {
    await assertFails(updateDoc(ref(as(env, KID)), take(KID)));
  });

  test('someone not warned cannot take it, nor the author', async () => {
    await assertFails(updateDoc(ref(as(env, NEIGHBOUR)), take(NEIGHBOUR)));
    await assertFails(updateDoc(ref(as(env, ADULT)), take(ADULT)));
  });

  test('nobody takes it in the name of someone else', async () => {
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { ...take(OTHER_ADULT), volunteerId: 'adult3' }));
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { ...take(OTHER_ADULT), volunteerName: 'Autre' }));
  });

  test('taking it changes nothing else', async () => {
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { ...take(OTHER_ADULT), title: 'Autre chose' }));
  });

  test('the second volunteer is refused: the first one keeps it', async () => {
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), take(OTHER_ADULT)));
    await stored({ ...request(ADULT, ['adult2', 'admin1']), status: 'taken', volunteerId: 'adult2', volunteerName: 'adult2' });
    const db = as(env, ADMIN);
    await assertFails(runTransaction(db, async (tx) => {
      await tx.get(ref(db));
      tx.update(ref(db), take(ADMIN));
    }));
    await assertFails(updateDoc(ref(db), take(ADMIN)));
  });

  test('the volunteer withdraws, someone else cannot withdraw them', async () => {
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), take(OTHER_ADULT)));
    const reopen = { status: 'open', volunteerId: null, volunteerName: null, ...now() };
    await assertFails(updateDoc(ref(as(env, ADULT)), reopen));
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), reopen));
  });
});

describe('the volunteer\'s word (récit 16, spec rév. 8)', () => {
  beforeEach(async () => { await stored(request(ADULT, ['adult2', 'admin1'])); });

  const take = (by, note) => ({ status: 'taken', volunteerId: by.memberId, volunteerName: by.memberId, volunteerNote: note, ...now() });

  test('the volunteer adds a word when taking it, then changes or clears it', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(ref(db), take(OTHER_ADULT, 'Je passe vers 17 h avec la voiture')));
    await assertSucceeds(updateDoc(ref(db), { volunteerNote: 'Plutôt 17 h 30', ...now() }));
    await assertSucceeds(updateDoc(ref(db), { volunteerNote: null, ...now() }));
  });

  test('200 characters at most, and only the volunteer writes it', async () => {
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), take(OTHER_ADULT, 'a'.repeat(201))));
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), take(OTHER_ADULT, 'Ok')));
    await assertFails(updateDoc(ref(as(env, ADULT)), { volunteerNote: 'Merci', ...now() }));
    await assertFails(updateDoc(ref(as(env, ADMIN)), { volunteerNote: 'Moi aussi', ...now() }));
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { volunteerNote: 'Et', title: 'Autre', ...now() }));
  });

  test('withdrawing clears the word, and the author never changes it', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(ref(db), take(OTHER_ADULT, 'Je passe vers 17 h')));
    await assertFails(updateDoc(ref(db), { status: 'open', volunteerId: null, volunteerName: null, ...now() }));
    await assertSucceeds(updateDoc(ref(db), { status: 'open', volunteerId: null, volunteerName: null, volunteerNote: null, ...now() }));
    await assertFails(updateDoc(ref(as(env, ADULT)), { title: 'Autre', volunteerNote: 'x', ...now() }));
  });

  test('a new request has no word', async () => {
    await assertFails(setDoc(ref(as(env, ADULT), 'r2'), request(ADULT, ['adult2'], { volunteerNote: 'x' })));
  });
});

describe('« Je ne peux pas » (carte PRODUIT-22)', () => {
  const decline = (by) => ({ declinedBy: by.memberId, ...now() });

  test('the only person warned declines', async () => {
    await stored(request(ADULT, ['adult2']));
    await assertSucceeds(updateDoc(ref(as(env, OTHER_ADULT)), decline(OTHER_ADULT)));
  });

  test('nobody declines a request sent to several people, nor for someone else', async () => {
    await stored(request(ADULT, ['adult2', 'adult3']));
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), decline(OTHER_ADULT)));
    await stored(request(ADULT, ['adult2']), 'r2');
    await assertFails(updateDoc(ref(as(env, ADULT), 'r2'), decline(OTHER_ADULT)));
  });

  test('the author widens to other people, which clears the answer', async () => {
    await stored({ ...request(ADULT, ['adult2']), declinedBy: 'adult2' });
    await assertSucceeds(updateDoc(ref(as(env, ADULT)), {
      notifyIds: ['adult2', 'adult3'], viewerIds: ['adult1', 'adult2', 'adult3'], declinedBy: null, ...now(),
    }));
  });
});

describe('managing a request (récit 17)', () => {
  test('the author edits it, and may move it to a later date', async () => {
    await stored(request(ADULT, ['adult2']));
    await assertSucceeds(updateDoc(ref(as(env, ADULT)), { title: 'Trajet au foot', kind: 'ride', date: day(5), ...now() }));
  });

  test('a new date in the past is refused', async () => {
    await stored(request(ADULT, ['adult2']));
    await assertFails(updateDoc(ref(as(env, ADULT)), { date: day(-3), ...now() }));
  });

  test('the parent edits the request of their child, another adult does not', async () => {
    await stored(request(KID, ['adult1', 'adult2']));
    await assertSucceeds(updateDoc(ref(as(env, ADULT)), { note: 'Avec le goûter', ...now() }));
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { note: 'Non', ...now() }));
  });

  test('the admin principal edits any request, a chef de famille does not', async () => {
    await stored(request(ADULT, ['adult2']));
    await assertSucceeds(updateDoc(ref(as(env, ADMIN)), { note: 'Vu', ...now() }));
    await assertFails(updateDoc(ref(as(env, SECOND_ADMIN)), { note: 'Vu', ...now() }));
  });

  test('the author, the person helped and the volunteer never change', async () => {
    await stored({ ...request(ADULT, ['adult2', 'adult3']), status: 'taken', volunteerId: 'adult2', volunteerName: 'adult2' });
    const db = as(env, ADULT);
    await assertFails(updateDoc(ref(db), { forMemberId: 'kid1', ...now() }));
    await assertFails(updateDoc(ref(db), { volunteerId: 'adult3', volunteerName: 'adult3', ...now() }));
    await assertFails(updateDoc(ref(db), { status: 'open', ...now() }));
  });

  test('the volunteer stays among the people warned', async () => {
    await stored({ ...request(ADULT, ['adult2', 'adult3']), status: 'taken', volunteerId: 'adult2', volunteerName: 'adult2' });
    await assertFails(updateDoc(ref(as(env, ADULT)), { notifyIds: ['adult3'], viewerIds: ['adult1', 'adult3'], ...now() }));
  });

  test('the author cancels, and a cancelled request stays cancelled', async () => {
    await stored(request(ADULT, ['adult2']));
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(ref(db), { status: 'cancelled', ...now() }));
    await assertFails(updateDoc(ref(db), { status: 'open', ...now() }));
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), {
      status: 'taken', volunteerId: 'adult2', volunteerName: 'adult2', ...now(),
    }));
  });

  test('the author deletes it, a person warned does not', async () => {
    await stored(request(ADULT, ['adult2']));
    await assertFails(deleteDoc(ref(as(env, OTHER_ADULT))));
    await assertSucceeds(deleteDoc(ref(as(env, ADULT))));
  });

  test('« Merci » once, on a request that was taken', async () => {
    await stored(request(ADULT, ['adult2']), 'open');
    await assertFails(updateDoc(ref(as(env, ADULT), 'open'), { thankedAt: serverTimestamp(), ...now() }));
    await stored({ ...request(ADULT, ['adult2']), status: 'taken', volunteerId: 'adult2', volunteerName: 'adult2' });
    await assertFails(updateDoc(ref(as(env, OTHER_ADULT)), { thankedAt: serverTimestamp(), ...now() }));
    await assertSucceeds(updateDoc(ref(as(env, ADULT)), { thankedAt: serverTimestamp(), ...now() }));
    await assertFails(updateDoc(ref(as(env, ADULT)), { thankedAt: serverTimestamp(), ...now() }));
  });
});
