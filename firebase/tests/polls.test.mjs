// Rules of polls/{pollId} and their votes (PRD, récits 2 bis et 2 ter). Fictional data only.
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

const CHILD = { memberId: 'child1', email: 'child1@example.com', uid: 'uid-child1' };

let env;

before(async () => {
  env = await createEnv();
});

after(async () => {
  await env.cleanup();
});

function poll(overrides = {}) {
  return {
    kind: 'date',
    question: 'Repas de Noël',
    options: ['2026-12-19', '2026-12-20 12:30'],
    multiple: false,
    askedIds: [OTHER_ADULT.memberId, CHILD.memberId],
    viewerIds: [ADULT.memberId, OTHER_ADULT.memberId, CHILD.memberId],
    eventId: null,
    closesAt: null,
    closed: false,
    chosen: null,
    createdEventId: null,
    authorId: ADULT.memberId,
    authorName: ADULT.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

const freePoll = (overrides = {}) => poll({ kind: 'choice', question: 'Barbecue ou raclette ?', options: ['Barbecue', 'Raclette', 'Les deux'], ...overrides });

function vote(memberId, answers, by = memberId) {
  return { memberId, answers, votedBy: by, updatedAt: serverTimestamp() };
}

function choice(memberId, choices, by = memberId) {
  return { memberId, choices, votedBy: by, updatedAt: serverTimestamp() };
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${CHILD.email}`), { memberId: CHILD.memberId });
    await setDoc(doc(db, `members/${CHILD.memberId}`), adultDoc(CHILD, { profile: 'childWithApp', parentIds: [ADULT.memberId] }));
    await setDoc(doc(db, 'polls/p1'), poll({ createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await setDoc(doc(db, 'polls/p2'), freePoll({ createdAt: Timestamp.now(), updatedAt: Timestamp.now(), multiple: true }));
    await setDoc(doc(db, 'polls/old'), poll({ createdAt: Timestamp.now(), updatedAt: Timestamp.now(), closesAt: Timestamp.fromDate(new Date('2020-01-01')) }));
  });
});

test('an adult asks a date poll or a free poll; a child does not', async () => {
  const db = as(env, ADULT);
  await assertSucceeds(setDoc(doc(db, 'polls/new'), poll()));
  await assertSucceeds(setDoc(doc(db, 'polls/new2'), freePoll({ multiple: true })));
  await assertFails(setDoc(doc(as(env, CHILD), 'polls/new3'), poll({ authorId: CHILD.memberId, authorName: CHILD.memberId, viewerIds: [CHILD.memberId, OTHER_ADULT.memberId], askedIds: [OTHER_ADULT.memberId] })));
});

test('a poll asked from an event names it (récit 2 quater)', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'events/e1'), { title: 'Repas', authorId: ADULT.memberId });
  });
  await assertSucceeds(setDoc(doc(as(env, ADULT), 'polls/ofEvent'), poll({ eventId: 'e1' })));
  await assertSucceeds(getDocs(query(collection(as(env, OTHER_ADULT), 'polls'), where('viewerIds', 'array-contains', OTHER_ADULT.memberId))));
});

test('a poll keeps its shape', async () => {
  const db = as(env, ADULT);
  const bad = [
    poll({ options: ['2026-12-19'] }),
    poll({ options: ['2026-12-19', '2026-12-20', '2026-12-21', '2026-12-22', '2026-12-23', '2026-12-24'] }),
    poll({ options: ['samedi', 'dimanche'] }),
    poll({ multiple: true }),
    freePoll({ options: ['a', 'b', 'c', 'd', 'e', 'f', 'g'] }),
    freePoll({ options: ['a', ' '] }),
    poll({ viewerIds: [OTHER_ADULT.memberId, ADULT.memberId, CHILD.memberId] }),
    poll({ viewerIds: [ADULT.memberId, OTHER_ADULT.memberId] }),
    poll({ closed: true }),
    poll({ chosen: 0 }),
    poll({ createdEventId: 'e1' }),
    poll({ eventId: 'missing' }),
    poll({ authorName: 'Quelqu\'un' }),
  ];
  for (const p of bad) await assertFails(setDoc(doc(db, 'polls/bad'), p));
});

test('only the author, the members asked and their adults, and the admin principal read a poll', async () => {
  await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'polls/p1')));
  await assertSucceeds(getDoc(doc(as(env, CHILD), 'polls/p1')));
  await assertSucceeds(getDoc(doc(as(env, ADMIN), 'polls/p1')));
  await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'polls/p1')));
  await assertSucceeds(getDocs(query(collection(as(env, OTHER_ADULT), 'polls'), where('viewerIds', 'array-contains', OTHER_ADULT.memberId))));
  await assertFails(getDocs(collection(as(env, NEIGHBOUR), 'polls')));
});

test('one answer per date, by the member asked or their responsible adult, while it is open', async () => {
  await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), `polls/p1/votes/${OTHER_ADULT.memberId}`), vote(OTHER_ADULT.memberId, ['yes', 'no'])));
  await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), `polls/p1/votes/${OTHER_ADULT.memberId}`), vote(OTHER_ADULT.memberId, ['maybe', 'yes'])));
  await assertSucceeds(setDoc(doc(as(env, ADULT), `polls/p1/votes/${CHILD.memberId}`), vote(CHILD.memberId, ['yes', 'yes'], ADULT.memberId)));
  await assertSucceeds(setDoc(doc(as(env, CHILD), `polls/p1/votes/${CHILD.memberId}`), vote(CHILD.memberId, ['no', 'yes'])));
  await assertFails(setDoc(doc(as(env, OTHER_ADULT), `polls/p1/votes/${CHILD.memberId}`), vote(CHILD.memberId, ['yes', 'yes'], OTHER_ADULT.memberId)));
  await assertFails(setDoc(doc(as(env, ADULT), `polls/p1/votes/${ADULT.memberId}`), vote(ADULT.memberId, ['yes', 'yes'])));
  await assertFails(setDoc(doc(as(env, OTHER_ADULT), `polls/p1/votes/${OTHER_ADULT.memberId}`), vote(OTHER_ADULT.memberId, ['yes'])));
  await assertFails(setDoc(doc(as(env, OTHER_ADULT), `polls/p1/votes/${OTHER_ADULT.memberId}`), vote(OTHER_ADULT.memberId, ['yes', 'oui'])));
  await assertFails(setDoc(doc(as(env, OTHER_ADULT), `polls/old/votes/${OTHER_ADULT.memberId}`), vote(OTHER_ADULT.memberId, ['yes', 'no'])));
});

test('a free poll takes one choice, or several when the author allowed it', async () => {
  const db = as(env, OTHER_ADULT);
  const path = `polls/p2/votes/${OTHER_ADULT.memberId}`;
  await assertSucceeds(setDoc(doc(db, path), choice(OTHER_ADULT.memberId, [0, 2])));
  await assertFails(setDoc(doc(db, path), choice(OTHER_ADULT.memberId, [0, 0])));
  await assertFails(setDoc(doc(db, path), choice(OTHER_ADULT.memberId, [3])));
  await assertFails(setDoc(doc(db, path), choice(OTHER_ADULT.memberId, [])));
  await env.withSecurityRulesDisabled((ctx) => updateDoc(doc(ctx.firestore(), 'polls/p2'), { multiple: false }));
  await assertFails(setDoc(doc(db, path), choice(OTHER_ADULT.memberId, [0, 2])));
  await assertSucceeds(setDoc(doc(db, path), choice(OTHER_ADULT.memberId, [1])));
});

test('the votes are named: everyone who sees the poll reads them', async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), `polls/p1/votes/${OTHER_ADULT.memberId}`), vote(OTHER_ADULT.memberId, ['yes', 'no'])));
  await assertSucceeds(getDocs(collection(as(env, CHILD), 'polls/p1/votes')));
  await assertFails(getDocs(collection(as(env, NEIGHBOUR), 'polls/p1/votes')));
});

test('the author closes the poll or chooses the date, and nothing else changes', async () => {
  const db = as(env, ADULT);
  await assertFails(updateDoc(doc(db, 'polls/p1'), { question: 'Autre chose', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'polls/p1'), { closed: true, updatedAt: serverTimestamp() }));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'events/mine'), { title: 'Repas de Noël', authorId: ADULT.memberId });
    await setDoc(doc(ctx.firestore(), 'events/theirs'), { title: 'Autre', authorId: OTHER_ADULT.memberId });
  });
  await assertFails(updateDoc(doc(db, 'polls/p1'), { closed: true, chosen: 5, createdEventId: 'mine', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'polls/p1'), { closed: true, chosen: 1, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'polls/p1'), { closed: true, chosen: 1, createdEventId: 'theirs', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(db, 'polls/p1'), { closed: true, chosen: 1, createdEventId: 'mine', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'polls/p1'), { chosen: 0, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'polls/p2'), { closed: true, chosen: 0, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(db, 'polls/p2'), { closed: true, updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(as(env, OTHER_ADULT), `polls/p2/votes/${OTHER_ADULT.memberId}`), choice(OTHER_ADULT.memberId, [1])));
});

test('the author and the admin principal delete a poll', async () => {
  await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'polls/p1')));
  await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'polls/p1')));
  await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'polls/p2')));
});
