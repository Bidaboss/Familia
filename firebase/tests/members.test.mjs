// Lot 2 rules: only family members (recognised by their verified Google address) can read the
// family; the only write is a member linking their own Firebase account on first sign-in.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(env); });

describe('reading the family', () => {
  test('a visitor who is not signed in reads nothing', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'members/adult1')));
    await assertFails(getDoc(doc(db, 'households/h1')));
    await assertFails(getDoc(doc(db, `memberEmails/${ADULT.email}`)));
  });

  test('a Google account outside the family reads nothing about the family', async () => {
    const db = as(env, STRANGER);
    await assertFails(getDoc(doc(db, 'members/adult1')));
    await assertFails(getDoc(doc(db, 'households/h1')));
    await assertFails(getDoc(doc(db, `memberEmails/${ADULT.email}`)));
  });

  test('a Google account outside the family may check its own address, and finds nothing', async () => {
    const snap = await assertSucceeds(getDoc(doc(as(env, STRANGER), `memberEmails/${STRANGER.email}`)));
    if (snap.exists()) throw new Error('stranger should not be in the family');
  });

  test('an unverified address is treated as outside the family', async () => {
    const db = as(env, ADULT, { verified: false });
    await assertFails(getDoc(doc(db, 'members/adult1')));
  });

  test('a member reads members, households and the email index', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(getDoc(doc(db, 'members/adult2')));
    await assertSucceeds(getDoc(doc(db, 'households/h1')));
    await assertSucceeds(getDoc(doc(db, `memberEmails/${OTHER_ADULT.email}`)));
  });

  test('a member is recognised whatever the casing of the Google address', async () => {
    const db = as(env, { uid: ADULT.uid, email: 'Adult1@Example.COM' });
    await assertSucceeds(getDoc(doc(db, 'members/adult1')));
  });

  test('collections not opened yet stay closed', async () => {
    await assertFails(getDoc(doc(as(env, ADULT), 'giftSpaces/g1')));
  });
});

describe('linking the account on first sign-in', () => {
  test('a member links their own Firebase account', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: ADULT.uid }));
  });

  test('a member cannot link an account other than their own', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: 'someone-else' }));
  });

  test('a Google account outside the family cannot write any uid', async () => {
    await assertFails(updateDoc(doc(as(env, STRANGER), 'members/adult1'), { uid: STRANGER.uid }));
  });

  test('a member cannot link another member', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult2'), { uid: ADULT.uid }));
  });

  test('a linked account cannot be replaced', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { uid: ADULT.uid }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { uid: ADULT.uid + '-bis' }));
  });

  test('linking cannot be used to change other fields, such as becoming admin', async () => {
    await assertFails(updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: ADULT.uid, isAdmin: true }));
  });
});

// The 21st: the whole family installs and signs in at the same minute.
describe('everyone at the same minute', () => {
  const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

  async function stored(path) {
    let data = null;
    await env.withSecurityRulesDisabled(async (ctx) => {
      const snap = await getDoc(doc(ctx.firestore(), path));
      data = snap.exists() ? snap.data() : null;
    });
    return data;
  }

  test('the same account opened on two phones links once, and the second phone still reads the family', async () => {
    const results = await Promise.allSettled([
      updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: ADULT.uid }),
      updateDoc(doc(as(env, ADULT), 'members/adult1'), { uid: ADULT.uid }),
    ]);
    if (!results.some((r) => r.status === 'fulfilled')) throw new Error('no link at all');
    if ((await stored('members/adult1')).uid !== ADULT.uid) throw new Error('wrong link');
    // The phone refused reads the link already there (what the app does) and goes on.
    const again = await assertSucceeds(getDoc(doc(as(env, ADULT), 'members/adult1')));
    if (again.get('uid') !== ADULT.uid) throw new Error('second phone does not see the link');
  });

  test('everyone links their own account at the same moment, without touching anyone else', async () => {
    const all = [ADULT, OTHER_ADULT, ADMIN, SECOND_ADMIN, NEIGHBOUR];
    await Promise.all(all.map((m) => assertSucceeds(updateDoc(doc(as(env, m), `members/${m.memberId}`), { uid: m.uid }))));
    for (const m of all) {
      if ((await stored(`members/${m.memberId}`)).uid !== m.uid) throw new Error(`${m.memberId} not linked to their own account`);
    }
    const ids = [];
    await env.withSecurityRulesDisabled(async (ctx) => {
      (await getDocs(collection(ctx.firestore(), 'members'))).forEach((d) => ids.push(d.id));
    });
    if (ids.length !== all.length) throw new Error(`members after the sign-ins: ${ids.length}, no extra one expected`);
  });

  test('a member removed while signed in reads nothing more, and finds their address gone', async () => {
    const admin = as(env, ADMIN);
    const batch = writeBatch(admin);
    batch.delete(doc(admin, 'members/adult1'));
    batch.delete(doc(admin, `memberEmails/${ADULT.email}`));
    await assertSucceeds(batch.commit());
    const db = as(env, ADULT);
    await assertFails(getDoc(doc(db, 'members/adult2')));
    // What the app reads again to tell a removal apart from a cut.
    const entry = await assertSucceeds(getDoc(doc(db, `memberEmails/${ADULT.email}`)));
    if (entry.exists()) throw new Error('the address should be gone');
  });

  test('a child with the app (Family Link) links their own account and reads the family', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
      await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: [ADULT.memberId], birthDate: '2015-01-10' }));
    });
    await assertSucceeds(updateDoc(doc(as(env, KID), 'members/kid1'), { uid: KID.uid }));
    await assertSucceeds(getDoc(doc(as(env, KID), 'members/adult1')));
  });

  test('an address not yet added to the family opens nothing, even signed in with Google', async () => {
    const snap = await assertSucceeds(getDoc(doc(as(env, STRANGER), `memberEmails/${STRANGER.email}`)));
    if (snap.exists()) throw new Error('not in the family');
    await assertFails(getDocs(collection(as(env, STRANGER), 'members')));
  });
});

describe('managing members is reserved to admins', () => {
  test('a member who is not admin creates or deletes nothing', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'members/new'), { firstName: 'x' }));
    await assertFails(deleteDoc(doc(db, 'members/adult2')));
    await assertFails(setDoc(doc(db, 'households/h2'), { name: 'x' }));
    await assertFails(setDoc(doc(db, `memberEmails/${STRANGER.email}`), { memberId: 'adult1' }));
  });

  test('a stranger cannot add themselves to the family', async () => {
    await assertFails(setDoc(doc(as(env, STRANGER), `memberEmails/${STRANGER.email}`), { memberId: 'adult1' }));
  });
});
