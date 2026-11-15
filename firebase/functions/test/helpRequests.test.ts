// Coups de main: who is told what (pure logic), then the scheduled reminder and the cleanup
// against the Firestore emulator (npm run test:functions in firebase/). Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  HelpMember, helpMessages, HelpNotice, HelpRequestData, HelpSender, helpWhen, onHelpRequestWritten, reopenRequestsOf,
  sendStillOpenReminders,
} from '../src/helpRequests';

const members: HelpMember[] = [
  { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [] },
  { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [] },
  { id: 'jules', firstName: 'Jules', profile: 'adult', parentIds: [] },
  { id: 'noe', firstName: 'Noé', profile: 'childWithApp', parentIds: ['camille', 'jules'] },
];

function request(overrides: Partial<HelpRequestData> = {}): HelpRequestData {
  return {
    kind: 'childcare', title: 'Garder Noé', date: '2026-12-02', startTime: '14:00', endTime: '18:00', place: null,
    authorId: 'camille', authorName: 'Camille', forMemberId: 'noe', notifyIds: ['ines', 'jules'],
    status: 'open', volunteerId: null, volunteerName: null, declinedBy: null, thankedAt: null,
    ...overrides,
  };
}

const today = '2026-11-30';
const to = (messages: ReturnType<typeof helpMessages>) => messages.flatMap((m) => m.recipientIds);

describe('who is told what', () => {
  test('a new request tells the people warned, never its author', () => {
    const messages = helpMessages('r1', undefined, request(), 'camille', members, today);
    assert.deepEqual(to(messages), ['ines', 'jules']);
    assert.equal(messages[0].notice.helpRequestId, 'r1');
    assert.match(messages[0].notice.body, /Camille demande de l'aide pour Noé : « Garder Noé », mercredi 2 décembre de 14/);
  });

  test('« Je m’en occupe » tells the author and the other parent, not the volunteer', () => {
    const taken = request({ status: 'taken', volunteerId: 'jules', volunteerName: 'Jules' });
    const messages = helpMessages('r1', request(), taken, 'jules', members, today);
    assert.deepEqual(to(messages), ['camille']);
    assert.equal(messages[0].notice.title, 'Jules s\'occupe de « Garder Noé »');
    const byInes = request({ status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' });
    assert.deepEqual(to(helpMessages('r1', request(), byInes, 'ines', members, today)), ['camille', 'jules']);
  });

  test('the volunteer\'s word is the body of the notice (spec rév. 8)', () => {
    const taken = request({ status: 'taken', volunteerId: 'jules', volunteerName: 'Jules', volunteerNote: ' Je passe vers 17 h ' });
    assert.equal(helpMessages('r1', request(), taken, 'jules', members, today)[0].notice.body, 'Jules : Je passe vers 17 h');
    const blank = request({ status: 'taken', volunteerId: 'jules', volunteerName: 'Jules', volunteerNote: '  ' });
    assert.ok(helpMessages('r1', request(), blank, 'jules', members, today)[0].notice.body.startsWith('C\'est réglé'));
  });

  test('a volunteer who withdraws tells everyone warned again', () => {
    const taken = request({ status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' });
    assert.deepEqual(to(helpMessages('r1', taken, request(), 'ines', members, today)), ['camille', 'jules']);
  });

  test('a cancellation tells the volunteer only', () => {
    const taken = request({ status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' });
    assert.deepEqual(to(helpMessages('r1', taken, { ...taken, status: 'cancelled' }, 'camille', members, today)), ['ines']);
    assert.deepEqual(to(helpMessages('r1', request(), request({ status: 'cancelled' }), 'camille', members, today)), []);
  });

  test('« Je ne peux pas » tells the author', () => {
    const single = request({ notifyIds: ['ines'], forMemberId: 'camille' });
    const messages = helpMessages('r1', single, { ...single, declinedBy: 'ines' }, 'ines', members, today);
    assert.deepEqual(to(messages), ['camille']);
    assert.equal(messages[0].notice.title, 'Inès ne peut pas');
  });

  test('widening tells only the new people', () => {
    const before = request({ notifyIds: ['ines'], forMemberId: 'camille', declinedBy: 'ines' });
    const after = { ...before, notifyIds: ['ines', 'jules'], declinedBy: null };
    assert.deepEqual(to(helpMessages('r1', before, after, 'camille', members, today)), ['jules']);
  });

  test('a taken request moved to another time tells its volunteer, who keeps it', () => {
    const taken = request({ status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' });
    const moved = { ...taken, startTime: '15:00' };
    assert.deepEqual(to(helpMessages('r1', taken, moved, 'camille', members, today)), ['ines']);
    assert.deepEqual(to(helpMessages('r1', taken, { ...taken, kind: 'other' }, 'camille', members, today)), []);
  });

  test('« Merci » reaches the volunteer, even after the date', () => {
    const taken = request({ status: 'taken', volunteerId: 'ines', volunteerName: 'Inès', date: '2026-11-28' });
    const messages = helpMessages('r1', taken, { ...taken, thankedAt: new Date() }, 'camille', members, today);
    assert.deepEqual(to(messages), ['ines']);
    assert.equal(messages[0].notice.title, 'Camille te dit merci !');
  });

  test('a past request tells nobody otherwise', () => {
    const past = request({ date: '2026-11-28' });
    assert.deepEqual(to(helpMessages('r1', undefined, past, 'camille', members, today)), []);
  });

  test('a member who left is never told', () => {
    assert.deepEqual(to(helpMessages('r1', undefined, request({ notifyIds: ['ines', 'gone'] }), 'camille', members, today)), ['ines']);
  });

  test('times read like in the app', () => {
    assert.equal(helpWhen({ date: '2026-12-02', startTime: null, endTime: null }), 'mercredi 2 décembre');
    assert.equal(helpWhen({ date: '2026-12-02', startTime: '09:00', endTime: null }), 'mercredi 2 décembre dès 9 h 00');
  });
});

describe('against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'help-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  function fakeSender() {
    const sent: { token: string; notice: HelpNotice }[] = [];
    const send: HelpSender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  // 2026-11-30 at 18 h in Paris.
  const now = new Date('2026-11-30T17:00:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('helpRequests'));
    const batch = db.batch();
    for (const m of members) {
      batch.set(db.doc(`members/${m.id}`), { firstName: m.firstName, profile: m.profile, parentIds: m.parentIds, uid: `uid-${m.id}` });
      batch.set(db.doc(`members/${m.id}/devices/token-${m.id}`), { updatedAt: new Date() });
    }
    batch.set(db.doc('helpRequests/open'), request({ date: '2026-12-01' }));
    batch.set(db.doc('helpRequests/taken'), request({ date: '2026-12-01', status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' }));
    batch.set(db.doc('helpRequests/later'), request({ date: '2026-12-05', status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' }));
    batch.set(db.doc('helpRequests/past'), request({ date: '2026-11-20', status: 'taken', volunteerId: 'ines', volunteerName: 'Inès' }));
    await batch.commit();
  });

  test('the evening before, the author of a request still open is reminded', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await sendStillOpenReminders(db, send, now), 1);
    assert.equal(sent[0].token, 'token-camille');
    assert.equal(sent[0].notice.helpRequestId, 'open');
    assert.equal(sent[0].notice.channel, 'reminders');
  });

  test('the author of a change is recognised by their account and not told', async () => {
    const { sent, send } = fakeSender();
    const created = request({ notifyIds: ['ines', 'jules'], forMemberId: 'camille' });
    await onHelpRequestWritten(db, send, 'r1', undefined, created, 'uid-camille', now);
    assert.deepEqual(sent.map((s) => s.token), ['token-ines', 'token-jules']);
  });

  test('a volunteer removed from the family frees their upcoming requests only', async () => {
    assert.equal(await reopenRequestsOf(db, 'ines', now), 2);
    assert.equal((await db.doc('helpRequests/later').get()).get('status'), 'open');
    assert.equal((await db.doc('helpRequests/later').get()).get('volunteerId'), null);
    assert.equal((await db.doc('helpRequests/past').get()).get('status'), 'taken');
  });
});
