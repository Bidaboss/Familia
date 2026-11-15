// Lot 3 rules: admins add, edit and remove members and households. The email index always
// moves in the same batch as the member, so a removed member loses access immediately.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(env); });

const NEW = { memberId: 'new1', email: 'new1@example.com', uid: 'uid-new1' };

function addMember(db, m, data = adultDoc(m)) {
  const batch = writeBatch(db);
  batch.set(doc(db, `members/${m.memberId}`), data);
  if (data.email) batch.set(doc(db, `memberEmails/${data.email}`), { memberId: m.memberId });
  return batch.commit();
}

describe('adding a member', () => {
  test('an admin adds an adult with their email entry in one batch', async () => {
    await assertSucceeds(addMember(as(env, ADMIN), NEW));
    // The new member can now read the family.
    await assertSucceeds(getDoc(doc(as(env, NEW), 'members/admin1')));
  });

  test('a member who is not admin cannot add anyone', async () => {
    await assertFails(addMember(as(env, ADULT), NEW));
  });

  test('a member without its email entry is refused', async () => {
    await assertFails(setDoc(doc(as(env, ADMIN), `members/${NEW.memberId}`), adultDoc(NEW)));
  });

  test('an email entry pointing to another member is refused', async () => {
    const db = as(env, ADMIN);
    const batch = writeBatch(db);
    batch.set(doc(db, `members/${NEW.memberId}`), adultDoc(NEW));
    batch.set(doc(db, `memberEmails/${NEW.email}`), { memberId: 'adult1' });
    await assertFails(batch.commit());
  });

  test('an email entry alone cannot open access to a stranger', async () => {
    await assertFails(setDoc(doc(as(env, ADMIN), 'memberEmails/stranger@example.com'), { memberId: 'adult1' }));
  });

  test('a child without the app has no email and at least one responsible adult', async () => {
    const db = as(env, ADMIN);
    const child = { memberId: 'kid1' };
    await assertSucceeds(addMember(db, child, adultDoc(child, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult1', 'adult2', 'admin1'],
    })));
    await assertFails(addMember(db, { memberId: 'kid2' }, adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: [],
    })));
  });

  test('a child cannot be made admin', async () => {
    const child = { memberId: 'kid3', email: 'kid3@example.com' };
    await assertFails(addMember(as(env, ADMIN), child, adultDoc(child, {
      profile: 'childWithApp', parentIds: ['adult1'], isAdmin: true,
    })));
  });

  test('a new member cannot arrive with a linked account', async () => {
    await assertFails(addMember(as(env, ADMIN), NEW, adultDoc(NEW, { uid: 'someone' })));
  });

  test('an address with capitals is refused, it must be stored lower-case', async () => {
    const m = { memberId: 'new2', email: 'New2@Example.com' };
    await assertFails(addMember(as(env, ADMIN), m));
  });

  test('the household must exist', async () => {
    await assertFails(addMember(as(env, ADMIN), NEW, adultDoc(NEW, { householdId: 'nowhere' })));
  });
});

describe('admins', () => {
  test('an admin names another adult admin', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'members/adult1'), { isAdmin: true }));
  });

  test('an admin removes the other admin', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'members/admin2'), { isAdmin: false }));
  });

  test('a chef cannot change their own chef flag, and nobody names the admin principal from the app', async () => {
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/admin2'), { isAdmin: false }));
    await assertFails(setDoc(doc(as(env, ADMIN), 'config/family'), { ownerMemberId: 'adult1' }));
    await assertFails(setDoc(doc(as(env, SECOND_ADMIN), 'config/family'), { ownerMemberId: 'admin2' }));
  });

  test('an admin edits a member but cannot change their linked account', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { firstName: 'Nouveau prénom' }));
    await assertFails(updateDoc(doc(db, 'members/adult1'), { uid: 'someone' }));
  });
});

describe('removing a member', () => {
  function removeMember(db, m) {
    const batch = writeBatch(db);
    batch.delete(doc(db, `members/${m.memberId}`));
    batch.delete(doc(db, `memberEmails/${m.email}`));
    return batch.commit();
  }

  test('an admin removes a member and their email entry together, and the person loses access', async () => {
    await assertSucceeds(removeMember(as(env, ADMIN), ADULT));
    await assertFails(getDoc(doc(as(env, ADULT), 'members/admin1')));
  });

  test('removing the member but keeping the email entry is refused', async () => {
    await assertFails(deleteDoc(doc(as(env, ADMIN), 'members/adult1')));
  });

  test('removing the email entry alone is refused while the member still has that address', async () => {
    await assertFails(deleteDoc(doc(as(env, ADMIN), `memberEmails/${ADULT.email}`)));
  });

  test('an admin cannot remove themselves', async () => {
    await assertFails(removeMember(as(env, ADMIN), ADMIN));
  });

  test('a member who is not admin cannot remove anyone', async () => {
    await assertFails(removeMember(as(env, ADULT), OTHER_ADULT));
  });
});

describe('changing an address', () => {
  test('an admin moves the email entry with the address, and the old address loses access', async () => {
    const db = as(env, ADMIN);
    const batch = writeBatch(db);
    batch.update(doc(db, 'members/adult1'), { email: 'adult1.new@example.com' });
    batch.delete(doc(db, `memberEmails/${ADULT.email}`));
    batch.set(doc(db, 'memberEmails/adult1.new@example.com'), { memberId: 'adult1' });
    await assertSucceeds(batch.commit());
    await assertFails(getDoc(doc(as(env, ADULT), 'members/admin1')));
  });

  test('changing the address while keeping the old entry is refused', async () => {
    const db = as(env, ADMIN);
    const batch = writeBatch(db);
    batch.update(doc(db, 'members/adult1'), { email: 'adult1.new@example.com' });
    batch.set(doc(db, 'memberEmails/adult1.new@example.com'), { memberId: 'adult1' });
    await assertFails(batch.commit());
  });
});

describe('households', () => {
  test('the admin principal creates, renames and deletes a household', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'households/h3'), { name: 'Foyer 3' }));
    await assertSucceeds(updateDoc(doc(db, 'households/h3'), { name: 'Foyer trois' }));
    await assertSucceeds(deleteDoc(doc(db, 'households/h3')));
  });

  test('a member who is not chef cannot create, rename or delete a household', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'households/h3'), { name: 'Foyer 3' }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'households/h1'), { name: 'Autre' }));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'households/h1')));
  });
});

// SECOND_ADMIN is seeded so the "remove the other admin" case has someone to remove.
void SECOND_ADMIN;
