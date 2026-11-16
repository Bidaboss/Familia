// « On m'a offert » (PRD, récit 10 bis): the archive of the revealed wishes. Written by the Cloud
// Function only; read by its owner and the adults who keep their lists; the owner says thank you.
// Fictional family: adult1, adult2, admin1 (admin principal) in h1, adult3 in h2, kid2 (child
// without the app, parent adult2).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
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
