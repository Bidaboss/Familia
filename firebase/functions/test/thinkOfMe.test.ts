import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import { cheersNotice, deleteMomentsOf, invitationNotice, onMomentCheerCreated, startMoments } from '../src/thinkOfMe';

describe('Pensez à moi, without the emulator', () => {
  test('the notices name the people, never a number', () => {
    const invite = invitationNotice('Camille', 'Examen de piano', 'camille');
    assert.equal(invite.title, 'Pensez à Camille aujourd\'hui');
    assert.equal(invite.body, '« Examen de piano » : envoie-lui un encouragement.');
    assert.equal(invite.channel, 'thinkOfMe');
    assert.equal(invite.thinkOfMeOf, 'camille');
    assert.equal(cheersNotice('camille', 'm1', ['Inès']).body, 'Inès pense à toi.');
    assert.equal(cheersNotice('camille', 'm1', ['Inès', 'Lou', 'Noé']).body, 'Inès, Lou et Noé pensent à toi.');
    assert.equal(cheersNotice('camille', 'm1', ['Inès']).direct, true);
    assert.equal(cheersNotice('camille', 'm1', ['Inès']).tag, 'thinkOfMe-m1');
  });
});

describe('Pensez à moi against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'think-of-me-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  // 10 h in Paris, Saturday 12/12/2026.
  const morning = new Date('2026-12-12T09:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  const moment = (forMemberId: string, overrides: Record<string, unknown> = {}) => ({
    forMemberId, authorId: forMemberId, authorName: forMemberId, title: 'Examen de piano', day: '2026-12-12',
    status: 'accepted', createdAt: morning, ...overrides,
  });

  beforeEach(async () => {
    for (const c of ['members', 'moments', 'momentCheers']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { thinkOfMe: true } });
    for (const [id, profile] of [['camille', 'adult'], ['ines', 'adult'], ['noe', 'childWithApp'], ['lou', 'childWithoutApp']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id === 'ines' ? 'Inès' : id, profile, parentIds: [], householdId: 'h1' });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: morning });
    }
    batch.set(db.doc('moments/m1'), moment('camille'));
    batch.set(db.doc('moments/waiting'), moment('noe', { status: 'pending', authorId: 'ines' }));
    batch.set(db.doc('moments/later'), moment('ines', { day: '2026-12-13' }));
    await batch.commit();
  });

  test('on the day, the family is invited once, not the person; a moment waiting for its teenager waits', async () => {
    const { sent, send } = fakeSender();
    assert.deepEqual(await startMoments(db, send, morning), ['m1']);
    assert.ok((await db.doc('moments/m1').get()).get('startedAt'));
    assert.equal((await db.doc('moments/waiting').get()).get('startedAt'), undefined);
    assert.deepEqual(sent.map((s) => s.token), ['token-ines', 'token-noe']);
    assert.equal(sent[0].notice.title, 'Pensez à camille aujourd\'hui');
    // No reminder: a second run of the day, or the next day, sends nothing more.
    assert.deepEqual(await startMoments(db, send, new Date('2026-12-12T15:00:00Z')), []);
    assert.deepEqual(await startMoments(db, send, new Date('2026-12-13T09:00:00Z')), ['later']);
    assert.equal(sent.filter((s) => s.notice.thinkOfMeOf === 'camille').length, 2);
  });

  test('module off: nothing starts', async () => {
    await db.doc('config/app').set({ features: { thinkOfMe: false } });
    const { sent, send } = fakeSender();
    assert.deepEqual(await startMoments(db, send, morning), []);
    assert.equal(sent.length, 0);
  });

  test('each encouragement tells the person, with the names of all who wrote', async () => {
    const { sent, send } = fakeSender();
    const first = { momentId: 'm1', forMemberId: 'camille', authorId: 'ines', authorName: 'Inès', text: 'Courage !' };
    await db.doc('momentCheers/m1-ines').set(first);
    assert.equal(await onMomentCheerCreated(db, send, first), true);
    const second = { ...first, authorId: 'noe', authorName: 'Noé' };
    await db.doc('momentCheers/m1-noe').set(second);
    await onMomentCheerCreated(db, send, second);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille', 'token-camille']);
    assert.equal(sent[1].notice.body, 'Inès et Noé pensent à toi.');
  });

  test('a member removed takes their moments and encouragements', async () => {
    await db.doc('momentCheers/m1-ines').set({ momentId: 'm1', forMemberId: 'camille', authorId: 'ines', authorName: 'Inès', text: 'x' });
    await deleteMomentsOf(db, 'camille');
    assert.equal((await db.doc('moments/m1').get()).exists, false);
    assert.equal((await db.doc('momentCheers/m1-ines').get()).exists, false);
    assert.equal((await db.doc('moments/later').get()).exists, true);
  });
});
