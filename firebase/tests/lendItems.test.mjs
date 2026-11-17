// Rules of « On se prête » (PRD, récit 51). Fictional family: admin1 (admin principal), adult1,
// adult2 in h1, adult3 in h2; kid1, a child with the app (parent adult1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
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

const FREE = {
  status: 'available', borrowerId: null, borrowerName: null, from: null, until: null,
  askedAt: null, lentAt: null, remindedAt: null,
};

/** A valid new thing to lend by [owner]. */
function item(owner, overrides = {}) {
  return {
    name: 'Appareil à raclette',
    note: 'À rendre propre.',
    category: 'kitchen',
    photo: null,
    ownerId: owner.memberId,
    ownerName: owner.memberId,
    ...FREE,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function stored(id, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `lendItems/${id}`), {
      ...data, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

/** The ask of [who], with optional dates. */
function ask(who, overrides = {}) {
  return {
    status: 'asked', borrowerId: who.memberId, borrowerName: who.memberId,
    from: null, until: null, askedAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

const asked = (who) => ({
  status: 'asked', borrowerId: who.memberId, borrowerName: who.memberId,
  from: null, until: Timestamp.fromMillis(Date.now() + 5 * DAY), askedAt: Timestamp.fromMillis(0),
});

const release = { ...FREE, updatedAt: serverTimestamp() };

describe('add a thing to lend (récit 51)', () => {
  test('an adult adds theirs, and the whole family reads it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1')));
    await assertSucceeds(getDoc(doc(as(env, KID), 'lendItems/i1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'lendItems/i1')));
  });

  test('a child sees the list but adds nothing', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'lendItems/i1'), item(KID)));
  });

  test('only in their own name, free, in a known category, with no money', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { ownerName: 'Quelqu\'un' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { status: 'lent' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { borrowerId: 'adult2' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { category: 'money' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { price: 10 })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { name: ' ' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { name: 'x'.repeat(61) })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { note: 'x'.repeat(301) })));
  });

  test('a photo only from the caller\'s own folder of this item', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'lendItems/i1'), item(ADULT, { photo: 'lendItems/i1/adult1/p1' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i2'), item(ADULT, { photo: 'lendItems/i2/adult2/p1' })));
    await assertFails(setDoc(doc(as(env, ADULT), 'lendItems/i3'), item(ADULT, { photo: 'lendItems/i1/adult1/p1' })));
  });
});

describe('change or remove a thing (récit 51)', () => {
  test('the owner and the admin principal change it, the loan stays', async () => {
    await stored('i1', item(ADULT, asked(OTHER_ADULT)));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { name: 'Raclette 6 personnes', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'lendItems/i1'), { category: 'other', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1'), { name: 'À moi', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { ownerId: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { name: 'Raclette', borrowerId: null, updatedAt: serverTimestamp() }));
  });

  test('the owner and the admin principal remove it, nobody else', async () => {
    await stored('i1', item(ADULT));
    await stored('i2', item(ADULT));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'lendItems/i1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'lendItems/i2')));
  });
});

describe('« Je voudrais l\'emprunter » (récit 51)', () => {
  test('an adult who is not the owner asks, in their own name, with or without dates', async () => {
    await stored('i1', item(ADULT));
    await stored('i2', item(ADULT));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR)));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i2'), ask(OTHER_ADULT, {
      from: Timestamp.fromMillis(Date.now() + DAY), until: Timestamp.fromMillis(Date.now() + 4 * DAY),
    })));
  });

  test('never the owner, a child, someone else\'s name, nor a thing already asked or lent', async () => {
    await stored('i1', item(ADULT));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), ask(ADULT)));
    await assertFails(updateDoc(doc(as(env, KID), 'lendItems/i1'), ask(KID)));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(OTHER_ADULT)));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR, { borrowerName: 'Autre' })));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR, { lentAt: serverTimestamp() })));
    await stored('i2', item(ADULT, asked(OTHER_ADULT)));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i2'), ask(NEIGHBOUR)));
  });

  test('dates within a year, the return not before the start', async () => {
    await stored('i1', item(ADULT));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR, {
      from: Timestamp.fromMillis(Date.now() + 5 * DAY), until: Timestamp.fromMillis(Date.now() + 2 * DAY),
    })));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR, {
      until: Timestamp.fromMillis(Date.now() + 400 * DAY),
    })));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR, {
      until: Timestamp.fromMillis(Date.now() - 3 * DAY),
    })));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), ask(NEIGHBOUR, { until: 'demain' })));
  });
});

describe('« D\'accord », « Pas cette fois » and « Rendu » (récit 51)', () => {
  test('the owner says « D\'accord »: the thing is lent to the one who asked', async () => {
    await stored('i1', item(ADULT, asked(OTHER_ADULT)));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1'), { status: 'lent', lentAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { status: 'lent', lentAt: serverTimestamp(), borrowerId: 'adult3', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { status: 'lent', lentAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  });

  test('a free thing is never lent without an ask', async () => {
    await stored('i1', item(ADULT));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { status: 'lent', lentAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  });

  test('« Pas cette fois » by the owner, or the borrower drops the ask; nobody else', async () => {
    await stored('i1', item(ADULT, asked(OTHER_ADULT)));
    await stored('i2', item(ADULT, asked(OTHER_ADULT)));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), release));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), release));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i2'), release));
  });

  test('« Rendu » by the owner or the borrower frees the thing entirely', async () => {
    const lent = { ...asked(OTHER_ADULT), status: 'lent', lentAt: Timestamp.fromMillis(0), remindedAt: Timestamp.fromMillis(0) };
    await stored('i1', item(ADULT, lent));
    await stored('i2', item(ADULT, lent));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'lendItems/i1'), release));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { ...release, borrowerId: 'adult2' }));
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1'), release));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'lendItems/i2'), release));
  });

  test('nobody writes the reminder but the server', async () => {
    const lent = { ...asked(OTHER_ADULT), status: 'lent', lentAt: Timestamp.fromMillis(0), remindedAt: null };
    await stored('i1', item(ADULT, lent));
    await assertFails(updateDoc(doc(as(env, ADULT), 'lendItems/i1'), { remindedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'lendItems/i1'), { until: Timestamp.fromMillis(Date.now() + 30 * DAY), updatedAt: serverTimestamp() }));
  });
});
