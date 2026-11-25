// Inside a surprise (plan Cadeaux et Surprise): members, proposals, ratings, takes and parts,
// « C'est offert », and « Déjà offert à X ». The beneficiary reads nothing of it, even as admin
// principal; the sums and totals are the server's; « Je ne participe pas » is final.
// Fictional family: adult1, adult2, admin1 (admin principal, the beneficiary here), adult3 (h2),
// kid1 (child with the app).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, getDocs, collection, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const OLD = Timestamp.fromMillis(0);
const S = 'surprises/s1';

function surprise(overrides = {}) {
  return {
    beneficiaryId: 'admin1', title: 'Les 40 ans', occasion: 'birthday', date: '2030-02-03',
    memberIds: ['adult2', 'adult1', 'kid1'], creatorId: 'adult2', creatorName: 'adult2', status: 'preparing',
    createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

function wishProposal(overrides = {}) {
  return {
    kind: 'wish', wishItemId: 'w1', title: 'Un livre', price: 20, url: null, authorId: 'admin1', authorName: 'admin1',
    ...overrides,
  };
}

function ideaProposal(by, overrides = {}) {
  return {
    kind: 'idea', notebookEntryId: 'e1', title: 'Un pull', price: 30, url: null, authorId: by.memberId, authorName: by.memberId,
    removed: false, createdAt: serverTimestamp(), ...overrides,
  };
}

const stamp = () => ({ updatedAt: serverTimestamp() });

async function store(path, data) {
  await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), path), data); });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, S), surprise());
    await setDoc(doc(db, 'giftLists/wishes-admin1/items/w1'), { title: 'Un livre', price: 20 });
    await setDoc(doc(db, 'notebooks/adult1/entries/e1'), { source: 'idea', title: 'Un pull' });
    await setDoc(doc(db, `${S}/proposals/wish-w1`), { ...wishProposal(), ratingSum: null, ratingCount: 1 });
    await setDoc(doc(db, `${S}/takes/wish-w1`), { kind: 'shared', ownerId: 'adult2', boughtBy: null, goal: 60, createdAt: OLD, updatedAt: OLD });
  });
});

describe('the beneficiary reads nothing, even as admin principal', () => {
  test('no line, proposal, rating, take, part, nor history of theirs', async () => {
    const db = as(env, ADMIN);
    for (const path of [`${S}/members/adult1`, `${S}/proposals/wish-w1`, `${S}/takes/wish-w1`]) {
      await assertFails(getDoc(doc(db, path)));
    }
    await assertFails(getDocs(collection(db, `${S}/proposals`)));
    await assertFails(getDoc(doc(db, `${S}/proposals/wish-w1/ratings/admin1`)));
    await assertFails(getDoc(doc(db, `${S}/takes/wish-w1/shares/admin1`)));
    await assertFails(getDocs(collection(db, 'giftHistory/admin1/items')));
    await assertSucceeds(getDocs(collection(as(env, NEIGHBOUR), 'giftHistory/admin1/items')));
  });

  test('a member reads the proposals and the takes; someone outside does not', async () => {
    await assertSucceeds(getDocs(collection(as(env, ADULT), `${S}/proposals`)));
    await assertSucceeds(getDoc(doc(as(env, KID), `${S}/takes/wish-w1`)));
    await assertFails(getDocs(collection(as(env, NEIGHBOUR), `${S}/proposals`)));
  });

  test('the server\'s own collections are closed to every phone', async () => {
    await store(`${S}/notebook/wish-w1`, { memberIds: ['adult2'] });
    await store(`${S}/offered/wish-w1`, { path: 'x', data: {} });
    for (const path of [`${S}/notebook/wish-w1`, `${S}/offered/wish-w1`]) {
      await assertFails(getDoc(doc(as(env, OTHER_ADULT), path)));
      await assertFails(setDoc(doc(as(env, OTHER_ADULT), path), { x: 1 }));
    }
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'giftHistory/admin1/items/x'), { title: 'Faux' }));
  });
});

describe('my line', () => {
  test('I validate, mute, and « Je ne participe pas » is final', async () => {
    const line = doc(as(env, ADULT), `${S}/members/adult1`);
    await assertSucceeds(setDoc(line, { validated: true, declined: false, muted: true, ...stamp() }));
    await assertSucceeds(setDoc(line, { validated: false, declined: true, muted: true, ...stamp() }));
    await assertFails(setDoc(line, { validated: false, declined: false, muted: true, ...stamp() }));
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/members/adult2`), { validated: true, ...stamp() }));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), `${S}/members/adult3`), { validated: true, ...stamp() }));
  });

  test('only the creator removes a line, to invite again', async () => {
    await store(`${S}/members/adult1`, { declined: true, updatedAt: OLD });
    await assertFails(deleteDoc(doc(as(env, ADULT), `${S}/members/adult1`)));
    await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), `${S}/members/adult1`)));
  });
});

describe('proposals', () => {
  test('a wish comes with its first rating, in one batch with merge; the next ratings write it again', async () => {
    await store('giftLists/wishes-admin1/items/w5', { title: 'Un jeu', price: 15 });
    const db = as(env, ADULT);
    const batch = writeBatch(db);
    batch.set(doc(db, `${S}/proposals/wish-w5`), wishProposal({ wishItemId: 'w5', title: 'Un jeu', price: 15 }), { merge: true });
    batch.set(doc(db, `${S}/proposals/wish-w5/ratings/adult1`), { stars: 4, ...stamp() });
    await assertSucceeds(batch.commit());
    const kid = as(env, KID);
    const again = writeBatch(kid);
    again.set(doc(kid, `${S}/proposals/wish-w5`), wishProposal({ wishItemId: 'w5', title: 'Un jeu', price: 15 }), { merge: true });
    again.set(doc(kid, `${S}/proposals/wish-w5/ratings/kid1`), { stars: 2, ...stamp() });
    await assertSucceeds(again.commit());
    await assertFails(setDoc(doc(db, `${S}/proposals/wish-w5`), { authorId: 'adult1' }, { merge: true }));
  });

  test('a member adds a wish of the person, at its id and with its live title', async () => {
    await store('giftLists/wishes-admin1/items/w2', { title: 'Un vélo', price: 200 });
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, `${S}/proposals/wish-w2`), wishProposal({ wishItemId: 'w2', title: 'Un vélo', price: 200 })));
    await assertFails(setDoc(doc(db, `${S}/proposals/wish-w3`), wishProposal({ wishItemId: 'w3', title: 'Rien' })));
    await assertFails(setDoc(doc(db, `${S}/proposals/wish-x`), wishProposal({ wishItemId: 'w2', title: 'Un vélo' })));
    await store('giftLists/wishes-admin1/items/w4', { title: 'Un sac' });
    await assertFails(setDoc(doc(db, `${S}/proposals/wish-w4`), wishProposal({ wishItemId: 'w4', title: 'Autre titre' })));
    await assertFails(setDoc(doc(db, `${S}/proposals/wish-w4`), wishProposal({ wishItemId: 'w4', title: 'Un sac', ratingSum: 15, ratingCount: 3 })));
  });

  test('an idea is added by its author alone, from their notebook, and taken back while nobody took it', async () => {
    const mine = doc(as(env, ADULT), `${S}/proposals/idea-e1`);
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), `${S}/proposals/idea-e1`), ideaProposal(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/proposals/idea-e9`), ideaProposal(ADULT, { notebookEntryId: 'e9' })));
    await assertSucceeds(setDoc(mine, ideaProposal(ADULT)));
    await assertSucceeds(updateDoc(mine, { title: 'Un pull bleu' }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), `${S}/proposals/idea-e1`), { title: 'Autre' }));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), `${S}/proposals/idea-e1`)));
    await assertSucceeds(deleteDoc(mine));
    await assertSucceeds(setDoc(mine, ideaProposal(ADULT)));
    await store(`${S}/takes/idea-e1`, { kind: 'solo', ownerId: 'adult2', boughtBy: null, goal: null, createdAt: OLD, updatedAt: OLD });
    await assertFails(deleteDoc(mine));
  });

  test('the ✕ of Résultats: any member, nothing else; never the server\'s sum', async () => {
    const p = doc(as(env, KID), `${S}/proposals/wish-w1`);
    await assertSucceeds(updateDoc(p, { removed: true }));
    await assertSucceeds(updateDoc(p, { removed: false }));
    await assertFails(updateDoc(p, { ratingSum: 15 }));
    await assertFails(updateDoc(p, { ratingCount: 9 }));
    await assertFails(updateDoc(p, { removed: true, authorId: 'adult1' }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), `${S}/proposals/wish-w1`), { removed: true }));
  });
});

describe('ratings', () => {
  test('my stars, 1 to 5, read by me alone', async () => {
    const mine = doc(as(env, ADULT), `${S}/proposals/wish-w1/ratings/adult1`);
    await assertSucceeds(setDoc(mine, { stars: 4, ...stamp() }));
    await assertSucceeds(getDoc(mine));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), `${S}/proposals/wish-w1/ratings/adult1`)));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), `${S}/proposals/wish-w1/ratings`)));
    await assertFails(setDoc(mine, { stars: 6, ...stamp() }));
    await assertFails(setDoc(mine, { stars: 2.5, ...stamp() }));
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/proposals/wish-w1/ratings/adult2`), { stars: 1, ...stamp() }));
    await assertSucceeds(setDoc(doc(as(env, KID), `${S}/proposals/wish-w1/ratings/kid1`), { stars: 5, ...stamp() }));
    await assertSucceeds(deleteDoc(mine));
  });

  test('« Je ne participe pas »: no more rating', async () => {
    await store(`${S}/members/adult1`, { declined: true, updatedAt: OLD });
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/proposals/wish-w1/ratings/adult1`), { stars: 4, ...stamp() }));
  });
});

describe('takes and parts', () => {
  test('« Je le prends » alone, a joint gift by an adult only, one per proposal', async () => {
    await store(`${S}/proposals/idea-e1`, { ...ideaProposal(ADULT), createdAt: OLD });
    const take = (who, data) => setDoc(doc(as(env, who), `${S}/takes/idea-e1`), {
      boughtBy: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...data,
    });
    await assertFails(take(KID, { kind: 'shared', ownerId: 'kid1', goal: 50 }));
    await assertFails(take(ADULT, { kind: 'solo', ownerId: 'adult2', goal: null }));
    await assertFails(take(ADULT, { kind: 'shared', ownerId: 'adult1', goal: 12.5 }));
    await assertFails(take(ADULT, { kind: 'solo', ownerId: 'adult1', goal: null, sharesTotal: 0 }));
    await assertSucceeds(take(KID, { kind: 'solo', ownerId: 'kid1', goal: null }));
    await assertFails(take(ADULT, { kind: 'solo', ownerId: 'adult1', goal: null }));
    await assertFails(deleteDoc(doc(as(env, ADULT), `${S}/takes/idea-e1`)));
    await assertSucceeds(deleteDoc(doc(as(env, KID), `${S}/takes/idea-e1`)));
  });

  test('a wish never rated is taken in the same batch as its proposal', async () => {
    await store('giftLists/wishes-admin1/items/w6', { title: 'Un casque' });
    const db = as(env, ADULT);
    const batch = writeBatch(db);
    batch.set(doc(db, `${S}/proposals/wish-w6`), wishProposal({ wishItemId: 'w6', title: 'Un casque', price: null }), { merge: true });
    batch.set(doc(db, `${S}/takes/wish-w6`), {
      kind: 'solo', ownerId: 'adult1', boughtBy: null, goal: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
  });

  test('a proposal taken out by ✕ is not taken', async () => {
    await store(`${S}/proposals/idea-e1`, { ...ideaProposal(ADULT), createdAt: OLD, removed: true });
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/takes/idea-e1`), {
      kind: 'solo', ownerId: 'adult1', boughtBy: null, goal: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });

  test('« Acheté par » myself; the goal by the owner alone; never the server\'s total', async () => {
    const t = (who) => doc(as(env, who), `${S}/takes/wish-w1`);
    await assertSucceeds(updateDoc(t(ADULT), { boughtBy: 'adult1', ...stamp() }));
    await assertFails(updateDoc(t(KID), { boughtBy: 'adult2', ...stamp() }));
    await assertFails(updateDoc(t(ADULT), { goal: 80, ...stamp() }));
    await assertSucceeds(updateDoc(t(OTHER_ADULT), { goal: 80, ...stamp() }));
    await assertFails(updateDoc(t(OTHER_ADULT), { sharesTotal: 80, ...stamp() }));
    await assertFails(updateDoc(t(OTHER_ADULT), { sharesCount: 2, ...stamp() }));
  });

  test('my part in whole euros, adults only, read by me and the owner', async () => {
    const part = doc(as(env, ADULT), `${S}/takes/wish-w1/shares/adult1`);
    await assertSucceeds(setDoc(part, { amount: 20, ...stamp() }));
    await assertSucceeds(setDoc(part, { amount: 25, ...stamp() }));
    await assertFails(setDoc(part, { amount: 12.5, ...stamp() }));
    await assertFails(setDoc(part, { amount: 0, ...stamp() }));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), `${S}/takes/wish-w1/shares/adult1`)));
    await assertFails(getDoc(doc(as(env, KID), `${S}/takes/wish-w1/shares/adult1`)));
    await assertFails(setDoc(doc(as(env, KID), `${S}/takes/wish-w1/shares/kid1`), { amount: 5, ...stamp() }));
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/takes/wish-w1/shares/adult2`), { amount: 5, ...stamp() }));
    await assertSucceeds(deleteDoc(part));
  });

  test('no part on a gift taken alone', async () => {
    await store(`${S}/takes/wish-w1`, { kind: 'solo', ownerId: 'adult2', boughtBy: null, goal: null, createdAt: OLD, updatedAt: OLD });
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/takes/wish-w1/shares/adult1`), { amount: 20, ...stamp() }));
  });
});

describe('« C\'est offert »', () => {
  const offer = (who) => updateDoc(doc(as(env, who), S), {
    status: 'offered', offeredAt: serverTimestamp(), offeredBy: who.memberId, updatedAt: serverTimestamp(),
  });

  test('not before the day', async () => {
    await assertFails(offer(ADULT));
  });

  test('from the day, one member for all, once; then nothing changes', async () => {
    await store(S, surprise({ date: '2026-01-01' }));
    await assertFails(updateDoc(doc(as(env, ADULT), S), {
      status: 'offered', offeredAt: serverTimestamp(), offeredBy: 'adult2', updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as(env, ADULT), S), {
      status: 'offered', offeredAt: serverTimestamp(), offeredBy: 'adult1', offeredHandledAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(offer(NEIGHBOUR));
    await assertFails(offer(ADMIN));
    await assertSucceeds(offer(KID));
    await assertFails(offer(ADULT));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), S), { status: 'preparing', title: 'Encore', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), `${S}/proposals/wish-w1`), { removed: true }));
    await assertFails(setDoc(doc(as(env, ADULT), `${S}/proposals/wish-w1/ratings/adult1`), { stars: 4, ...stamp() }));
  });

  test('not by a member who declined', async () => {
    await store(S, surprise({ date: '2026-01-01' }));
    await store(`${S}/members/adult1`, { declined: true, updatedAt: OLD });
    await assertFails(offer(ADULT));
  });
});
