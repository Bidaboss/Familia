// V2 rules: listes et cadeaux (PRD, récits 7 à 10 bis). The surprise is kept by the server.
// Fictional family: adult1, adult2, admin1 (admin principal) and admin2 in h1, adult3 in h2,
// kid1 (child with the app, parent adult1), kid2 (child without the app, parent adult2).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where,
} from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const OLD = Timestamp.fromMillis(0);

/** A list as stored, for seeding. */
function storedList(overrides = {}) {
  return {
    kind: 'own', title: 'Noël', occasion: 'christmas', date: '2026-12-25',
    beneficiaryId: 'adult1', viewerIds: ['adult1', 'adult2', 'adult3'],
    creatorId: 'adult1', creatorName: 'adult1', revealed: false,
    createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

function storedItem(overrides = {}) {
  return {
    title: 'Un livre', url: null, price: 15, note: null,
    authorId: 'adult1', authorName: 'adult1', visibleToBeneficiary: true,
    createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult2'],
    }));
    // adult1's own Christmas list, seen by adult2 and adult3.
    await setDoc(doc(db, 'giftLists/own1'), storedList());
    await setDoc(doc(db, 'giftLists/own1/items/mine'), storedItem());
    await setDoc(doc(db, 'giftLists/own1/items/idea'), storedItem({
      title: 'Une idée surprise', authorId: 'adult2', authorName: 'adult2', visibleToBeneficiary: false,
    }));
    await setDoc(doc(db, 'giftLists/own1/claims/mine'), {
      type: 'shared', holderId: 'adult2', holderName: 'adult2', createdAt: OLD,
    });
    await setDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult2'), {
      amount: 10, memberName: 'adult2', received: false, updatedAt: OLD,
    });
    // A surprise birthday list for adult1, made by adult2.
    await setDoc(doc(db, 'giftLists/surprise1'), storedList({
      kind: 'surprise', title: 'Anniversaire de adult1', occasion: 'birthday', date: '2027-03-01',
      viewerIds: ['adult2', 'adult3'], creatorId: 'adult2', creatorName: 'adult2',
    }));
    await setDoc(doc(db, 'giftLists/surprise1/items/s1'), storedItem({
      authorId: 'adult2', authorName: 'adult2', visibleToBeneficiary: false,
    }));
    // adult1's wishes, open to the whole family.
    await setDoc(doc(db, 'giftLists/wishes-adult1'), storedList({
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, viewerIds: [],
    }));
    await setDoc(doc(db, 'giftLists/wishes-adult1/items/w1'), storedItem());
  });
});

/** A new list written by the app. */
function newList(by, overrides = {}) {
  return {
    kind: 'own', title: 'Noël 2026', occasion: 'christmas', date: '2026-12-25',
    beneficiaryId: by.memberId, viewerIds: [by.memberId, 'adult3'],
    creatorId: by.memberId, creatorName: by.memberId, revealed: false,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

function newItem(by, overrides = {}) {
  return {
    title: 'Un jeu', url: 'https://example.com/jeu', price: 29.9, note: null,
    authorId: by.memberId, authorName: by.memberId, visibleToBeneficiary: false,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

function newClaim(by, type = 'solo') {
  return { type, holderId: by.memberId, holderName: by.memberId, createdAt: serverTimestamp() };
}

function newShare(by, amount = 20) {
  return { amount, memberName: by.memberId, received: false, updatedAt: serverTimestamp() };
}

describe('creating lists (récit 7)', () => {
  test('a member creates their own list and their wishes', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(setDoc(doc(db, 'giftLists/a'), newList(OTHER_ADULT)));
    await assertSucceeds(setDoc(doc(db, 'giftLists/wishes-adult2'), newList(OTHER_ADULT, {
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, viewerIds: [],
    })));
  });

  test('« Mes envies » has the id of its member and nobody else creates it', async () => {
    const wishes = { kind: 'wishes', title: 'Mes envies', occasion: null, date: null, viewerIds: [] };
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'giftLists/wishes-other'), newList(OTHER_ADULT, wishes)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'giftLists/wishes-adult3'),
      newList(OTHER_ADULT, { ...wishes, beneficiaryId: 'adult3' })));
  });

  test('a responsible adult keeps the lists of a child without the app', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(setDoc(doc(db, 'giftLists/k'), newList(OTHER_ADULT, {
      beneficiaryId: 'kid2', viewerIds: ['kid2', 'adult2', 'adult1'],
    })));
    await assertSucceeds(setDoc(doc(db, 'giftLists/wishes-kid2'), newList(OTHER_ADULT, {
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, viewerIds: [], beneficiaryId: 'kid2',
    })));
    // Another adult does not keep them.
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/k2'), newList(ADULT, {
      beneficiaryId: 'kid2', viewerIds: ['kid2', 'adult2', 'adult1'],
    })));
  });

  test('the responsible adults of a child always see the child’s own list', async () => {
    const db = as(env, KID);
    await assertFails(setDoc(doc(db, 'giftLists/k'), newList(KID, { viewerIds: ['kid1', 'adult3'] })));
    await assertSucceeds(setDoc(doc(db, 'giftLists/k'), newList(KID, { viewerIds: ['kid1', 'adult1'] })));
  });

  test('an own list is for oneself, and its beneficiary sees it', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, 'giftLists/a'), newList(OTHER_ADULT, { beneficiaryId: 'adult3', viewerIds: ['adult2', 'adult3'] })));
    await assertFails(setDoc(doc(db, 'giftLists/b'), newList(OTHER_ADULT, { viewerIds: ['adult3'] })));
  });

  test('a surprise list never lists its beneficiary among the viewers', async () => {
    const db = as(env, OTHER_ADULT);
    const surprise = { kind: 'surprise', beneficiaryId: 'adult1', occasion: 'birthday' };
    await assertSucceeds(setDoc(doc(db, 'giftLists/s'), newList(OTHER_ADULT, { ...surprise, viewerIds: ['adult2', 'adult3'] })));
    await assertFails(setDoc(doc(db, 'giftLists/t'), newList(OTHER_ADULT, { ...surprise, viewerIds: ['adult2', 'adult1'] })));
    await assertFails(setDoc(doc(db, 'giftLists/u'), newList(OTHER_ADULT, { ...surprise, beneficiaryId: 'adult2', viewerIds: ['adult2'] })));
  });

  test('the creator is the caller, sees the list, and the list is not revealed', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, 'giftLists/a'), newList(OTHER_ADULT, { creatorId: 'adult1' })));
    await assertFails(setDoc(doc(db, 'giftLists/b'), newList(OTHER_ADULT, { creatorName: 'Autre' })));
    await assertFails(setDoc(doc(db, 'giftLists/c'), newList(OTHER_ADULT, { revealed: true })));
    await assertFails(setDoc(doc(db, 'giftLists/d'), newList(OTHER_ADULT, {
      kind: 'surprise', beneficiaryId: 'adult1', viewerIds: ['adult3'],
    })));
  });

  test('the shape is checked', async () => {
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, 'giftLists/a'), newList(OTHER_ADULT, { title: '' })));
    await assertFails(setDoc(doc(db, 'giftLists/b'), newList(OTHER_ADULT, { occasion: 'fête' })));
    await assertFails(setDoc(doc(db, 'giftLists/c'), newList(OTHER_ADULT, { date: '25/12/2026' })));
    await assertFails(setDoc(doc(db, 'giftLists/d'), newList(OTHER_ADULT, { beneficiaryId: 'ghost', viewerIds: ['adult2', 'ghost'] })));
    await assertFails(setDoc(doc(db, 'giftLists/e'), { ...newList(OTHER_ADULT), extra: 1 }));
  });

  test('a stranger creates nothing', async () => {
    await assertFails(setDoc(doc(as(env, STRANGER), 'giftLists/a'), newList({ memberId: 'adult1' })));
  });
});

describe('who sees a list', () => {
  test('the viewers read a list, the others do not', async () => {
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'giftLists/own1')));
    await assertFails(getDoc(doc(as(env, KID), 'giftLists/own1')));
    await assertFails(getDoc(doc(as(env, ADMIN), 'giftLists/own1')));
  });

  test('the whole family reads « Mes envies », a stranger does not', async () => {
    await assertSucceeds(getDoc(doc(as(env, KID), 'giftLists/wishes-adult1')));
    await assertSucceeds(getDoc(doc(as(env, KID), 'giftLists/wishes-adult1/items/w1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'giftLists/wishes-adult1')));
  });

  test('the app queries of each screen are allowed', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(getDocs(query(collection(db, 'giftLists'), where('viewerIds', 'array-contains', 'adult2'))));
    await assertSucceeds(getDocs(query(collection(db, 'giftLists'), where('kind', '==', 'wishes'))));
    await assertSucceeds(getDocs(query(collection(db, 'giftLists'),
      where('beneficiaryId', '==', 'adult2'), where('revealed', '==', true))));
    await assertFails(getDocs(collection(db, 'giftLists')));
  });
});

describe('the surprise is kept by the server (récit 10 bis)', () => {
  test('the beneficiary never reads a surprise list for them', async () => {
    const db = as(env, ADULT);
    await assertFails(getDoc(doc(db, 'giftLists/surprise1')));
    await assertFails(getDoc(doc(db, 'giftLists/surprise1/items/s1')));
    await assertFails(getDocs(collection(db, 'giftLists/surprise1/items')));
    await assertFails(getDocs(query(collection(db, 'giftLists'), where('beneficiaryId', '==', 'adult1'))));
  });

  test('on their own list, the beneficiary reads their items but not the others’ ideas', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(getDoc(doc(db, 'giftLists/own1/items/mine')));
    await assertFails(getDoc(doc(db, 'giftLists/own1/items/idea')));
    await assertFails(getDocs(collection(db, 'giftLists/own1/items')));
    await assertSucceeds(getDocs(query(collection(db, 'giftLists/own1/items'), where('visibleToBeneficiary', '==', true))));
    // Same on « Mes envies ».
    await assertSucceeds(getDocs(query(collection(db, 'giftLists/wishes-adult1/items'), where('visibleToBeneficiary', '==', true))));
  });

  test('the beneficiary reads neither the reservations nor the shares', async () => {
    const db = as(env, ADULT);
    await assertFails(getDoc(doc(db, 'giftLists/own1/claims/mine')));
    await assertFails(getDocs(collection(db, 'giftLists/own1/claims')));
    await assertFails(getDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult2')));
    await assertFails(getDocs(collection(db, 'giftLists/own1/claims/mine/shares')));
    await assertFails(getDocs(collection(db, 'giftLists/wishes-adult1/claims')));
  });

  test('the viewers read the items and the reservations', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(getDocs(collection(db, 'giftLists/own1/items')));
    await assertSucceeds(getDocs(collection(db, 'giftLists/own1/claims')));
    await assertSucceeds(getDocs(collection(db, 'giftLists/surprise1/items')));
  });

  test('the amount of a part: its author and the responsible of the joint gift only', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'giftLists/own1/claims/mine/shares/adult3'), {
        amount: 15, memberName: 'adult3', received: false, updatedAt: OLD,
      });
    });
    const holder = as(env, OTHER_ADULT);
    await assertSucceeds(getDocs(collection(holder, 'giftLists/own1/claims/mine/shares')));
    await assertSucceeds(getDoc(doc(holder, 'giftLists/own1/claims/mine/shares/adult3')));
    const participant = as(env, NEIGHBOUR);
    await assertSucceeds(getDoc(doc(participant, 'giftLists/own1/claims/mine/shares/adult3')));
    await assertFails(getDoc(doc(participant, 'giftLists/own1/claims/mine/shares/adult2')));
    await assertFails(getDocs(collection(participant, 'giftLists/own1/claims/mine/shares')));
  });

  test('the day after the occasion, the beneficiary discovers everything', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'giftLists/own1'), { revealed: true });
      await updateDoc(doc(ctx.firestore(), 'giftLists/surprise1'), { revealed: true });
    });
    const db = as(env, ADULT);
    await assertSucceeds(getDocs(collection(db, 'giftLists/own1/items')));
    await assertSucceeds(getDocs(collection(db, 'giftLists/own1/claims')));
    await assertFails(getDocs(collection(db, 'giftLists/own1/claims/mine/shares'))); // The amounts stay private.
    await assertSucceeds(getDoc(doc(db, 'giftLists/surprise1')));
    await assertSucceeds(getDocs(collection(db, 'giftLists/surprise1/items')));
    await assertSucceeds(getDocs(query(collection(db, 'giftLists'),
      where('beneficiaryId', '==', 'adult1'), where('revealed', '==', true))));
  });

  test('only the server reveals a list', async () => {
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'giftLists/own1'), { revealed: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'giftLists/surprise1'), { revealed: true, updatedAt: serverTimestamp() }));
  });

  test('the beneficiary cannot slip into the viewers of a surprise list', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'giftLists/surprise1'),
      { viewerIds: ['adult1', 'adult2'], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'giftLists/surprise1'),
      { viewerIds: ['adult1', 'adult2'], updatedAt: serverTimestamp() }));
  });

  test('a child without the app has no secret from their responsible adults', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'giftLists/kidlist'), storedList({
        beneficiaryId: 'kid2', viewerIds: ['kid2', 'adult2', 'adult1'], creatorId: 'adult2', creatorName: 'adult2',
      }));
      await setDoc(doc(ctx.firestore(), 'giftLists/kidlist/items/i'), storedItem({ authorId: 'adult1', visibleToBeneficiary: false }));
    });
    await assertSucceeds(getDocs(collection(as(env, OTHER_ADULT), 'giftLists/kidlist/items')));
    await assertSucceeds(getDocs(collection(as(env, OTHER_ADULT), 'giftLists/kidlist/claims')));
  });
});

describe('managing a list', () => {
  test('the beneficiary renames their own list and changes its viewers', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'giftLists/own1'),
      { title: 'Noël 2026', viewerIds: ['adult1', 'adult2'], updatedAt: serverTimestamp() }));
  });

  test('a viewer does not manage the list', async () => {
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1'), { title: 'X', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1')));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'giftLists/surprise1')));
  });

  test('the kind, the beneficiary and the creator never change', async () => {
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'giftLists/own1'), { kind: 'surprise', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'giftLists/own1'), { creatorName: 'X', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'giftLists/surprise1'), { beneficiaryId: 'adult3', updatedAt: serverTimestamp() }));
  });

  test('the creator deletes a list; « Mes envies » stays', async () => {
    await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), 'giftLists/surprise1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'giftLists/own1')));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'giftLists/wishes-adult1')));
  });

  test('a revealed list is archived', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'giftLists/own1'), { revealed: true });
    });
    await assertFails(updateDoc(doc(as(env, ADULT), 'giftLists/own1'), { title: 'X', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/items/new'), newItem(NEIGHBOUR)));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/claims/mine2'), newClaim(NEIGHBOUR)));
    // Nothing leaves an archived list.
    await assertFails(deleteDoc(doc(as(env, ADULT), 'giftLists/own1/items/mine')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'giftLists/own1/claims/mine')));
  });
});

describe('items (récit 8)', () => {
  test('the beneficiary adds an item they see; a viewer adds a surprise idea', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'giftLists/own1/items/n1'), newItem(ADULT, { visibleToBeneficiary: true })));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/items/n2'), newItem(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(as(env, KID), 'giftLists/wishes-adult1/items/n3'), newItem(KID)));
  });

  test('the visibility to the beneficiary cannot be forged', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/items/n'), newItem(NEIGHBOUR, { visibleToBeneficiary: true })));
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/own1/items/m'), newItem(ADULT, { visibleToBeneficiary: false })));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'giftLists/surprise1/items/o'), newItem(OTHER_ADULT, { visibleToBeneficiary: true })));
  });

  test('only the people who see the list add to it', async () => {
    await assertFails(setDoc(doc(as(env, ADMIN), 'giftLists/own1/items/n'), newItem(ADMIN)));
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/surprise1/items/n'), newItem(ADULT)));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/ghost/items/n'), newItem(NEIGHBOUR)));
  });

  test('the shape of an item is checked', async () => {
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'giftLists/own1/items/a'), newItem(NEIGHBOUR, { title: '' })));
    await assertFails(setDoc(doc(db, 'giftLists/own1/items/b'), newItem(NEIGHBOUR, { url: 'javascript:alert(1)' })));
    await assertFails(setDoc(doc(db, 'giftLists/own1/items/c'), newItem(NEIGHBOUR, { price: -1 })));
    await assertFails(setDoc(doc(db, 'giftLists/own1/items/d'), newItem(NEIGHBOUR, { authorName: 'Autre' })));
    await assertSucceeds(setDoc(doc(db, 'giftLists/own1/items/e'), newItem(NEIGHBOUR, { url: null, price: null, note: 'Taille M' })));
    await assertSucceeds(setDoc(doc(db, 'giftLists/own1/items/f'), newItem(NEIGHBOUR, { imageUrl: 'https://example.com/a.jpg' })));
    await assertFails(setDoc(doc(db, 'giftLists/own1/items/g'), newItem(NEIGHBOUR, { imageUrl: 'http://example.com/a.jpg' })));
  });

  test('the author edits or removes an item until it is reserved', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'giftLists/own1/claims/idea'), {
        type: 'solo', holderId: 'adult3', holderName: 'adult3', createdAt: OLD,
      });
      await setDoc(doc(ctx.firestore(), 'giftLists/own1/items/free'), storedItem({
        authorId: 'adult2', authorName: 'adult2', visibleToBeneficiary: false,
      }));
    });
    const db = as(env, OTHER_ADULT);
    await assertFails(updateDoc(doc(db, 'giftLists/own1/items/idea'), { title: 'X', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'giftLists/own1/items/idea')));
    await assertSucceeds(updateDoc(doc(db, 'giftLists/own1/items/free'), { title: 'X', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'giftLists/own1/items/free')));
  });

  test('the beneficiary edits their reserved item: a refusal would give the surprise away', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'giftLists/own1/items/mine'), { note: 'Bleu', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'giftLists/own1/items/mine')));
  });

  test('nobody edits the item of someone else', async () => {
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/items/mine'), { title: 'X', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/items/idea')));
  });
});

describe('reservations and joint gifts (récit 9)', () => {
  test('a viewer takes an item, then releases it', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'giftLists/own1/claims/idea'), newClaim(NEIGHBOUR)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'giftLists/own1/claims/idea'), newClaim(OTHER_ADULT)));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'giftLists/own1/claims/idea')));
    await assertSucceeds(deleteDoc(doc(db, 'giftLists/own1/claims/idea')));
  });

  test('an item is reserved once: no second claim over the first', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/claims/mine'), newClaim(NEIGHBOUR)));
  });

  test('the beneficiary never reserves on their own list', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/own1/claims/idea'), newClaim(ADULT)));
  });

  test('a reservation needs an existing item and the caller as holder', async () => {
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/ghost'), newClaim(NEIGHBOUR)));
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/idea'), { ...newClaim(NEIGHBOUR), holderId: 'adult2' }));
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/idea'), newClaim(NEIGHBOUR, 'group')));
  });

  test('a child takes an item alone, never a joint gift (carte PRODUIT-19)', async () => {
    const db = as(env, KID);
    await assertSucceeds(setDoc(doc(db, 'giftLists/wishes-adult1/claims/w1'), newClaim(KID)));
    await assertSucceeds(deleteDoc(doc(db, 'giftLists/wishes-adult1/claims/w1')));
    await assertFails(setDoc(doc(db, 'giftLists/wishes-adult1/claims/w1'), newClaim(KID, 'shared')));
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/mine/shares/kid1'), newShare(KID)));
  });

  test('adults join a joint gift and change their part', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), newShare(NEIGHBOUR)));
    await assertSucceeds(updateDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), { amount: 25, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), { received: true, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3')));
  });

  test('a part is one’s own, positive and not already received', async () => {
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult1'), newShare(NEIGHBOUR)));
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), newShare(NEIGHBOUR, 0)));
    await assertFails(setDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), { ...newShare(NEIGHBOUR), received: true }));
  });

  test('the responsible marks a part received, and nothing else', async () => {
    const db = as(env, OTHER_ADULT);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'giftLists/own1/claims/mine/shares/adult3'), {
        amount: 20, memberName: 'adult3', received: false, updatedAt: OLD,
      });
    });
    await assertSucceeds(updateDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), { received: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'giftLists/own1/claims/mine/shares/adult3'), { amount: 1, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/claims/mine/shares/adult2'), { received: true, updatedAt: serverTimestamp() }));
  });

  test('a solo reservation takes no parts', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'giftLists/own1/claims/idea'), {
        type: 'solo', holderId: 'adult2', holderName: 'adult2', createdAt: OLD,
      });
    });
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/own1/claims/idea/shares/adult3'), newShare(NEIGHBOUR)));
  });

  test('the beneficiary does not join a joint gift for them', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/own1/claims/mine/shares/adult1'), newShare(ADULT)));
  });
});

// Récit 9 ter: one list of wishes per person, and « Idées de la famille » (ideas-{member}), which
// the whole family reads but the person, never revealed to them, « Voir comme » included.
describe('the ideas of the family (récit 9 ter)', () => {
  function newIdeas(by, beneficiary) {
    return newList(by, {
      kind: 'ideas', title: 'Idées de la famille', occasion: null, date: null,
      beneficiaryId: beneficiary, viewerIds: [],
    });
  }

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'giftLists/ideas-adult1'), storedList({
        kind: 'ideas', title: 'Idées de la famille', occasion: null, date: null, viewerIds: [],
        creatorId: 'adult2', creatorName: 'adult2',
      }));
      await setDoc(doc(db, 'giftLists/ideas-adult1/items/i1'), storedItem({
        authorId: 'adult2', authorName: 'adult2', visibleToBeneficiary: false,
      }));
      await setDoc(doc(db, 'giftLists/ideas-adult1/claims/i1'), {
        type: 'solo', holderId: 'adult3', holderName: 'adult3', createdAt: OLD,
      });
    });
  });

  test('anyone but the person creates them, at their one id', async () => {
    await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), 'giftLists/ideas-adult3'), newIdeas(OTHER_ADULT, 'adult3')));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/ideas-adult3x'), newIdeas(NEIGHBOUR, 'adult2')));
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/ideas-adult1b'), newIdeas(ADULT, 'adult1')));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/ideas-adult2'), { ...newIdeas(NEIGHBOUR, 'adult2'), viewerIds: ['adult3'] }));
  });

  test('the family reads them and reserves in them; the person never reads anything of them', async () => {
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'giftLists/ideas-adult1')));
    await assertSucceeds(getDoc(doc(as(env, KID), 'giftLists/ideas-adult1/items/i1')));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'giftLists/ideas-adult1/claims/i1')));
    await assertFails(getDoc(doc(as(env, ADULT), 'giftLists/ideas-adult1')));
    await assertFails(getDoc(doc(as(env, ADULT), 'giftLists/ideas-adult1/items/i1')));
    await assertFails(getDoc(doc(as(env, ADULT), 'giftLists/ideas-adult1/claims/i1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'giftLists/ideas-adult1')));
  });

  test('an idea is never visible to the person, and the person adds none', async () => {
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/ideas-adult1/items/n1'), newItem(NEIGHBOUR)));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/ideas-adult1/items/n2'), newItem(NEIGHBOUR, { visibleToBeneficiary: true })));
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/ideas-adult1/items/n3'), newItem(ADULT)));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'giftLists/ideas-adult1/claims/n1'), newClaim(NEIGHBOUR)));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'giftLists/ideas-adult1/claims/n1'), { occasion: 'christmas' }));
  });

  test('they are permanent: nobody deletes them, and their shape stays that of a permanent list', async () => {
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'giftLists/ideas-adult1')));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'giftLists/ideas-adult1'), { occasion: 'christmas', updatedAt: serverTimestamp() }));
  });

  test('the query of the tab: the ideas of everyone but oneself', async () => {
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(getDocs(query(collection(db, 'giftLists'), where('kind', '==', 'ideas'), where('beneficiaryId', '!=', 'adult2'))));
    await assertFails(getDocs(query(collection(db, 'giftLists'), where('kind', '==', 'ideas'))));
  });

  test('a wish says the occasion it is for, or none', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'giftLists/wishes-adult1/items/o1'), newItem(ADULT, { visibleToBeneficiary: true, occasion: 'christmas' })));
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'giftLists/wishes-adult1/items/o2'), newItem(ADULT, { visibleToBeneficiary: true, occasion: null })));
    await assertFails(setDoc(doc(as(env, ADULT), 'giftLists/wishes-adult1/items/o3'), newItem(ADULT, { visibleToBeneficiary: true, occasion: 'easter' })));
  });
});
