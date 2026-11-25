// A chef de famille manages the events of their own household and renames it (PRD, « Périmètre
// d'un chef de famille », carte DEV-8); since récit 114 (C2), the members are the admin principal's. SECOND_ADMIN is chef of h1; NEIGHBOUR lives in h2.
import { after, afterEach, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, NEIGHBOUR, SECOND_ADMIN, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await seed(env); });
afterEach(async () => { await env.clearFirestore(); });

const chef = () => as(env, SECOND_ADMIN);

function addMember(db, m, overrides = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, `members/${m.memberId}`), adultDoc(m, overrides));
  batch.set(doc(db, `memberEmails/${m.email}`), { memberId: m.memberId });
  return batch.commit();
}

function removeMember(db, m) {
  const batch = writeBatch(db);
  batch.delete(doc(db, `members/${m.memberId}`));
  batch.delete(doc(db, `memberEmails/${m.email}`));
  // The app also removes the photo in the same batch (récit 3 bis).
  batch.delete(doc(db, `memberPhotos/${m.memberId}`));
  return batch.commit();
}

const NEW = { memberId: 'new1', email: 'new1@example.com' };

describe('members', () => {
  test('only the admin principal adds a member, a chef never does', async () => {
    await assertSucceeds(addMember(as(env, ADMIN), NEW));
    await assertFails(addMember(chef(), { memberId: 'new2', email: 'new2@example.com' }));
    await assertFails(addMember(chef(), { memberId: 'new3', email: 'new3@example.com' }, { householdId: 'h2' }));
  });

  test('a chef never names a chef', async () => {
    await assertFails(updateDoc(doc(chef(), 'members/adult1'), { isAdmin: true }));
  });

  test('a chef no longer edits anyone of their household, children included (récit 114, C2)', async () => {
    await assertFails(updateDoc(doc(chef(), 'members/adult1'), { firstName: 'Nouveau prénom' }));
    await assertFails(updateDoc(doc(chef(), 'members/adult3'), { firstName: 'Nouveau prénom' }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'members/kid9'), adultDoc({ memberId: 'kid9', email: null },
        { profile: 'childWithoutApp', email: null, parentIds: ['adult1'] }));
    });
    await assertFails(updateDoc(doc(chef(), 'members/kid9'), { firstName: 'Noa' }));
    await assertFails(updateDoc(doc(chef(), 'members/kid9'), { profile: 'childWithApp' }));
    await assertFails(updateDoc(doc(chef(), 'members/kid9'), { parentIds: ['admin2'] }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'members/kid9'), { firstName: 'Noa' }));
  });

  test('a chef never moves a member to another household', async () => {
    await assertFails(updateDoc(doc(chef(), 'members/adult1'), { householdId: 'h2' }));
  });

  test('a chef no longer changes an address, not even their own: invitations are the admin principal\'s', async () => {
    const db = chef();
    const own = writeBatch(db);
    own.update(doc(db, 'members/admin2'), { email: 'admin2.new@example.com' });
    own.delete(doc(db, 'memberEmails/admin2@example.com'));
    own.set(doc(db, 'memberEmails/admin2.new@example.com'), { memberId: 'admin2' });
    await assertFails(own.commit());
    const admin = as(env, ADMIN);
    const byAdmin = writeBatch(admin);
    byAdmin.update(doc(admin, 'members/admin2'), { email: 'admin2.new@example.com' });
    byAdmin.delete(doc(admin, 'memberEmails/admin2@example.com'));
    byAdmin.set(doc(admin, 'memberEmails/admin2.new@example.com'), { memberId: 'admin2' });
    await assertSucceeds(byAdmin.commit());
  });

  test('a chef never touches the admin principal', async () => {
    await assertFails(updateDoc(doc(chef(), 'members/admin1'), { firstName: 'Autre' }));
    await assertFails(removeMember(chef(), ADMIN));
  });

  test('only the admin principal removes a member, a chef never does', async () => {
    await assertFails(removeMember(chef(), { memberId: 'adult1', email: 'adult1@example.com' }));
    await assertFails(removeMember(chef(), NEIGHBOUR));
    await assertSucceeds(removeMember(as(env, ADMIN), { memberId: 'adult1', email: 'adult1@example.com' }));
  });
});

describe('households', () => {
  test('a chef renames their own household only, and never creates or deletes one', async () => {
    await assertSucceeds(updateDoc(doc(chef(), 'households/h1'), { name: 'Les Un' }));
    // Récit 114: the address of the household is the admin principal's.
    await assertFails(updateDoc(doc(chef(), 'households/h1'), { address: '1 rue des Lilas' }));
    await assertFails(updateDoc(doc(chef(), 'households/h1'), { name: 'Les Un bis', address: '1 rue des Lilas' }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'households/h1'), { address: '1 rue des Lilas' }));
    await assertFails(updateDoc(doc(chef(), 'households/h2'), { name: 'Les Deux' }));
    await assertFails(setDoc(doc(chef(), 'households/h3'), { name: 'Foyer 3' }));
    await assertFails(deleteDoc(doc(chef(), 'households/h2')));
  });
});

describe('events', () => {
  function familyEvent(author) {
    return {
      title: 'Repas', date: '2026-12-24', time: null, place: null, note: null,
      targetType: 'family', targetMemberId: null, householdId: null,
      authorId: author.memberId, authorName: author.memberId,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    };
  }

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'events/byH1'), { ...familyEvent({ memberId: 'adult1' }), createdAt: new Date(), updatedAt: new Date() });
      await setDoc(doc(db, 'events/byH2'), { ...familyEvent(NEIGHBOUR), createdAt: new Date(), updatedAt: new Date() });
    });
  });

  test('a chef edits and deletes the events written by their household, even for the whole family', async () => {
    await assertSucceeds(updateDoc(doc(chef(), 'events/byH1'), { title: 'Repas de Noël', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(chef(), 'events/byH1')));
  });

  test('a chef cannot edit or delete the events of another household', async () => {
    await assertFails(updateDoc(doc(chef(), 'events/byH2'), { title: 'Autre', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(chef(), 'events/byH2')));
  });

  test('the admin principal edits every event', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'events/byH2'), { title: 'Autre', updatedAt: serverTimestamp() }));
  });

  test('a chef creates events for their own household only, like any adult', async () => {
    const forH2 = { ...familyEvent(SECOND_ADMIN), targetType: 'household', householdId: 'h2' };
    await assertFails(setDoc(doc(chef(), 'events/new'), forH2));
    await assertSucceeds(setDoc(doc(chef(), 'events/new'), { ...forH2, householdId: 'h1' }));
  });
});

describe('config', () => {
  test('every member reads who the admin principal is', async () => {
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'config/family')));
  });
});
