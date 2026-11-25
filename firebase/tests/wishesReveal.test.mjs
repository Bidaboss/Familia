// « On m'a offert » (PRD, récit 10 bis): the archive of the revealed wishes. Written by the Cloud
// Function only; read by its owner and the adults who keep their lists; the owner says thank you.
// Fictional family: adult1, adult2, admin1 (admin principal) in h1, adult3 in h2, kid2 (child
// without the app, parent adult2).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const OLD = Timestamp.fromMillis(0);

function wishes(beneficiaryId) {
  return {
    kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId, viewerIds: [],
    creatorId: beneficiaryId, creatorName: beneficiaryId, revealed: false, createdAt: OLD, updatedAt: OLD,
  };
}

function received(overrides = {}) {
  return {
    title: 'Un livre', url: null, imageUrl: null, givers: ['adult2'], joint: false,
    occasion: 'christmas', year: 2026, thanked: false, revealedAt: OLD, ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, { email: null, profile: 'childWithoutApp', parentIds: ['adult2'] }));
    await setDoc(doc(db, 'giftLists/wishes-adult1'), wishes('adult1'));
    await setDoc(doc(db, 'giftLists/wishes-adult1/received/w1'), received());
    await setDoc(doc(db, 'giftLists/wishes-kid2'), wishes('kid2'));
    await setDoc(doc(db, 'giftLists/wishes-kid2/received/k1'), received({ givers: ['adult3'] }));
  });
});

function wish(overrides = {}) {
  return {
    title: 'Un vélo', url: 'https://example.com/velo', price: 120, note: null, authorId: 'adult1', authorName: 'adult1',
    visibleToBeneficiary: true, createdAt: OLD, updatedAt: OLD, ...overrides,
  };
}

/** « Reçue » as the app writes it (rév. 232): the archive and the removal of the wish, in one batch. */
function selfReceived(item, overrides = {}) {
  return {
    title: item.title, url: item.url, imageUrl: null, giverIds: [], givers: [], joint: false,
    occasion: null, year: null, thanked: false, revealedAt: serverTimestamp(), self: true, ...overrides,
  };
}

async function markReceived(who, listId, itemId, item, overrides = {}, removeItem = true) {
  const db = as(env, who);
  const batch = writeBatch(db);
  batch.set(doc(db, `giftLists/${listId}/received/${itemId}`), selfReceived(item, overrides));
  if (removeItem) batch.delete(doc(db, `giftLists/${listId}/items/${itemId}`));
  return batch.commit();
}

describe('« Reçue », marked by the person (rév. 232)', () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'giftLists/wishes-adult1/items/bike'), wish());
      await setDoc(doc(db, 'giftLists/wishes-adult1/items/idea'), wish({ title: 'Une idée', authorId: 'adult2', visibleToBeneficiary: false }));
      await setDoc(doc(db, 'giftLists/wishes-kid2/items/doll'), wish({ title: 'Une poupée', authorId: 'adult2' }));
    });
  });

  test('the person archives a wish they got, with its removal, and no giver', async () => {
    await assertSucceeds(markReceived(ADULT, 'wishes-adult1', 'bike', wish()));
  });

  test('the adults of a child without the app do it for them; nobody else', async () => {
    await assertSucceeds(markReceived(OTHER_ADULT, 'wishes-kid2', 'doll', wish({ title: 'Une poupée' })));
    await assertFails(markReceived(OTHER_ADULT, 'wishes-adult1', 'bike', wish()));
    await assertFails(markReceived(NEIGHBOUR, 'wishes-adult1', 'bike', wish()));
  });

  test('never without removing the wish, never with a giver, never on a surprise idea', async () => {
    await assertFails(markReceived(ADULT, 'wishes-adult1', 'bike', wish(), {}, false));
    await assertFails(markReceived(ADULT, 'wishes-adult1', 'bike', wish(), { givers: ['adult2'], giverIds: ['adult2'] }));
    await assertFails(markReceived(ADULT, 'wishes-adult1', 'bike', wish(), { title: 'Autre chose' }));
    await assertFails(markReceived(ADULT, 'wishes-adult1', 'idea', wish({ title: 'Une idée' })));
    await assertFails(markReceived(ADULT, 'wishes-adult1', 'nothing', wish()));
  });
});

describe('« On m\'a offert »', () => {
  test('its owner reads it, and the adults of a child without the app; nobody else', async () => {
    await assertSucceeds(getDocs(collection(as(env, ADULT), 'giftLists/wishes-adult1/received')));
    await assertSucceeds(getDocs(collection(as(env, OTHER_ADULT), 'giftLists/wishes-kid2/received')));
    for (const who of [OTHER_ADULT, NEIGHBOUR, ADMIN]) {
      await assertFails(getDocs(collection(as(env, who), 'giftLists/wishes-adult1/received')));
    }
    await assertFails(getDocs(collection(as(env, ADULT), 'giftLists/wishes-kid2/received')));
  });

  test('only the server writes it; the owner only says thank you', async () => {
    const db = as(env, ADULT);
    const gift = doc(db, 'giftLists/wishes-adult1/received/w1');
    await assertSucceeds(updateDoc(gift, { thanked: true, thankedAt: serverTimestamp() }));
    await assertFails(updateDoc(gift, { givers: ['adult1'] }));
    await assertFails(setDoc(doc(db, 'giftLists/wishes-adult1/received/w2'), received()));
    await assertFails(deleteDoc(gift));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'giftLists/wishes-kid2/received/k1'), { thanked: true, thankedAt: serverTimestamp() }));
  });
});

describe('the occasion of a reservation of « Mes envies » (rév. 186)', () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'giftLists/wishes-adult1/items/i1'), { title: 'Un livre' });
      await setDoc(doc(db, 'giftLists/wishes-adult1/claims/i1'), { type: 'solo', holderId: 'adult2', holderName: 'adult2', createdAt: OLD });
    });
  });

  // La bascule vers les surprises: an old reservation is not changed any more, only released.
  test('nobody changes it any more, its holder still releases it', async () => {
    const claim = (who) => doc(as(env, who), 'giftLists/wishes-adult1/claims/i1');
    await assertFails(updateDoc(claim(OTHER_ADULT), { occasion: 'christmas' }));
    await assertFails(updateDoc(claim(OTHER_ADULT), { occasion: 'birthday' }));
    await assertFails(updateDoc(claim(OTHER_ADULT), { occasion: 'other' }));
    await assertFails(updateDoc(claim(OTHER_ADULT), { occasion: 'christmas', holderName: 'Autre' }));
    await assertFails(updateDoc(claim(NEIGHBOUR), { occasion: 'christmas' }));
    await assertSucceeds(deleteDoc(claim(OTHER_ADULT)));
  });
});
