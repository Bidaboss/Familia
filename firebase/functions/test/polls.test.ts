// Sondages (récits 2 bis et 2 ter): the texts and the result (pure logic), then the closing, the
// sign-up of those who said yes and the cleanup against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { formatTime, Notice } from '../src/logic';
import {
  closeDuePolls, deletePollVotes, deleteVotesOf, formatOption, newPollNotice, onPollClosed, onPollCreated, PollData,
  resultNotice, VoteData, winners,
} from '../src/polls';
import { Sender } from '../src/store';

function poll(overrides: Partial<PollData> = {}): PollData {
  return {
    kind: 'choice', question: 'Barbecue ou raclette ?', options: ['Barbecue', 'Raclette'], multiple: false,
    askedIds: ['camille', 'ines', 'noe'], viewerIds: ['camille', 'ines', 'noe'], closed: false, chosen: null,
    createdEventId: null, authorId: 'camille', authorName: 'Camille', ...overrides,
  };
}

const vote = (memberId: string, choices: number[]): VoteData => ({ memberId, choices, votedBy: memberId });

describe('texts and result', () => {
  test('a new poll says who asks what', () => {
    assert.equal(newPollNotice('p1', poll()).body, 'Camille te demande ton avis : « Barbecue ou raclette ? »');
    assert.match(newPollNotice('p1', poll({ kind: 'date', question: 'Repas de Noël' })).body, /propose des dates pour « Repas de Noël »/);
    assert.equal(newPollNotice('p1', poll()).pollId, 'p1');
  });

  test('a date option reads the French way', () => {
    assert.equal(formatOption('2026-12-20 12:30'), `dimanche 20 décembre à ${formatTime('12:30')}`);
    assert.equal(formatOption('2026-12-19'), 'samedi 19 décembre');
  });

  test('the result names the winner, or the tie, or nobody', () => {
    const p = poll({ options: ['Barbecue', 'Raclette', 'Crêpes'], multiple: true });
    assert.deepEqual(winners(p, [vote('a', [1]), vote('b', [1, 2]), vote('c', [0])]), [1]);
    assert.equal(resultNotice('p1', p, [vote('a', [1]), vote('b', [1, 2])]).body, '« Barbecue ou raclette ? » : Raclette l\'emporte (2 voix).');
    assert.match(resultNotice('p1', p, [vote('a', [0]), vote('b', [2])]).body, /égalité entre Barbecue et Crêpes \(1 voix chacun\)/);
    assert.match(resultNotice('p1', p, []).body, /^Personne n'a voté/);
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'polls-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('polls'));
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('events'));
    for (const id of ['camille', 'ines', 'noe']) await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    await db.doc('config/app').set({ features: { polls: true } });
    await db.doc('config/family').set({ ownerMemberId: 'ines' });
  });

  test('module off: a new poll and its result reach the admin principal only', async () => {
    await db.doc('config/app').set({ features: { polls: false } });
    const created = fakeSender();
    await onPollCreated(db, created.send, 'p1', poll());
    assert.deepEqual(created.sent.map((s) => s.token), ['token-ines']);
    const closed = fakeSender();
    await onPollClosed(db, closed.send, 'p1', poll(), poll({ closed: true }), 'ines');
    assert.equal(closed.sent.length, 0);
  });

  test('module off: nothing reaches the family when the admin principal is not asked', async () => {
    await db.doc('config/app').delete();
    const { sent, send } = fakeSender();
    await onPollCreated(db, send, 'p1', poll({ askedIds: ['camille', 'noe'], viewerIds: ['camille', 'noe'] }));
    const p = poll({ kind: 'date', options: ['2026-12-19', '2026-12-20'] });
    await onPollClosed(db, send, 'p1', p, { ...p, closed: true }, null);
    assert.equal(sent.length, 0);
  });

  test('a new poll reaches the members asked, not its author', async () => {
    const { sent, send } = fakeSender();
    await onPollCreated(db, send, 'p1', poll());
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-noe']);
  });

  test('the end date closes the poll once', async () => {
    await db.doc('polls/due').set({ ...poll(), closesAt: Timestamp.fromDate(new Date('2026-11-20T10:00:00Z')) });
    await db.doc('polls/later').set({ ...poll(), closesAt: Timestamp.fromDate(new Date('2026-11-30T10:00:00Z')) });
    await db.doc('polls/old').set({ ...poll(), closed: true, closesAt: Timestamp.fromDate(new Date('2026-11-01T10:00:00Z')) });
    assert.equal(await closeDuePolls(db, new Date('2026-11-21T08:00:00Z')), 1);
    assert.equal((await db.doc('polls/due').get()).get('closed'), true);
    assert.equal((await db.doc('polls/later').get()).get('closed'), false);
    assert.equal(await closeDuePolls(db, new Date('2026-11-21T08:30:00Z')), 0);
  });

  test('a free poll closed by its author: the others get the result', async () => {
    await db.doc('polls/p1/votes/ines').set(vote('ines', [1]));
    const { sent, send } = fakeSender();
    await onPollClosed(db, send, 'p1', poll(), poll({ closed: true }), 'camille');
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-noe']);
    assert.match(sent[0].notice.body, /Raclette l'emporte/);
  });

  test('a date poll closed by its end date asks its author to choose', async () => {
    const { sent, send } = fakeSender();
    const p = poll({ kind: 'date', options: ['2026-12-19', '2026-12-20'] });
    await onPollClosed(db, send, 'p1', p, { ...p, closed: true }, null);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.match(sent[0].notice.body, /choisis la date/);
  });

  test('the date chosen signs up those who said yes to it', async () => {
    const p = poll({ kind: 'date', options: ['2026-12-19', '2026-12-20'] });
    await db.doc('polls/p1/votes/ines').set({ memberId: 'ines', answers: ['no', 'yes'], votedBy: 'ines' });
    await db.doc('polls/p1/votes/noe').set({ memberId: 'noe', answers: ['yes', 'maybe'], votedBy: 'camille' });
    const { sent, send } = fakeSender();
    await onPollClosed(db, send, 'p1', p, { ...p, closed: true, chosen: 1, createdEventId: 'e1' }, 'camille');
    const rsvps = await db.collection('events/e1/rsvps').get();
    assert.deepEqual(rsvps.docs.map((d) => [d.id, d.get('status'), d.get('answeredBy')]), [['ines', 'yes', 'ines']]);
    assert.equal(sent.length, 0);
  });

  test('deleting a poll or removing a member deletes the votes', async () => {
    await db.doc('polls/p1/votes/ines').set(vote('ines', [0]));
    await db.doc('polls/p2/votes/ines').set(vote('ines', [1]));
    await db.doc('polls/p2/votes/noe').set(vote('noe', [1]));
    await deletePollVotes(db, 'p1');
    assert.equal((await db.collection('polls/p1/votes').get()).size, 0);
    await deleteVotesOf(db, 'ines');
    assert.deepEqual((await db.collection('polls/p2/votes').get()).docs.map((d) => d.id), ['noe']);
  });
});
