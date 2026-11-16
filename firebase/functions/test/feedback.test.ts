// « Une idée ? Un souci ? » (récit 6 sexies): the text of the notification (pure logic), then who
// is told, against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { FeedbackData, feedbackNotice, onFeedbackChanged, onFeedbackCreated, screenshotToDelete, statusNotice } from '../src/feedback';
import { Sender } from '../src/store';

function feedback(overrides: Partial<FeedbackData> = {}): FeedbackData {
  return { kind: 'idea', text: 'Un bouton pour les anniversaires', authorId: 'ines', authorName: 'Inès', ...overrides };
}

describe('text', () => {
  test('an idea, then a problem, on the admin channel', () => {
    const idea = feedbackNotice(feedback());
    assert.equal(idea.title, 'Idée de Inès');
    assert.equal(idea.body, 'Un bouton pour les anniversaires');
    assert.equal(idea.channel, 'admin');
    assert.equal(feedbackNotice(feedback({ kind: 'problem' })).title, 'Souci signalé par Inès');
    assert.equal(idea.feedback, 'list');
  });

  test('a new state tells the author, with the word of the admin when there is one', () => {
    assert.equal(statusNotice(feedback({ status: 'new' })), null);
    const planned = statusNotice(feedback({ status: 'planned', note: 'Pour décembre' }));
    assert.equal(planned?.title, 'Ta demande est prévue');
    assert.equal(planned?.body, 'Pour décembre');
    assert.equal(planned?.channel, 'feedback');
    assert.equal(planned?.feedback, 'mine');
    assert.equal(statusNotice(feedback({ status: 'done', note: null }))?.body, 'Un bouton pour les anniversaires');
    assert.equal(statusNotice(feedback({ status: 'declined' }))?.title, 'Ta demande n\'est pas retenue');
  });

  test('a long text is cut, on one line', () => {
    const body = feedbackNotice(feedback({ text: `Ligne\n\n${'a'.repeat(300)}` })).body;
    assert.equal(body.length, 120);
    assert.ok(body.startsWith('Ligne a'));
    assert.ok(body.endsWith('…'));
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'feedback-test');
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
    await db.recursiveDelete(db.collection('members'));
    for (const id of ['noe', 'ines']) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', householdId: 'h1', parentIds: [], uid: null });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
    await db.doc('config/family').set({ ownerMemberId: 'noe' });
  });

  test('the admin principal is told, and only them', async () => {
    const { sent, send } = fakeSender();
    await onFeedbackCreated(db, send, feedback());
    assert.deepEqual(sent.map((s) => s.token), ['token-noe']);
  });

  test('the author is told of a new state, not of the same one again', async () => {
    const first = fakeSender();
    await onFeedbackChanged(db, first.send, feedback({ status: 'new' }), feedback({ status: 'done' }));
    assert.deepEqual(first.sent.map((s) => s.token), ['token-ines']);
    const second = fakeSender();
    await onFeedbackChanged(db, second.send, feedback({ status: 'done' }), feedback({ status: 'done', note: 'Un mot' }));
    assert.equal(second.sent.length, 0);
  });

  test('the admin principal is not told of their own answer to themselves', async () => {
    const { sent, send } = fakeSender();
    await onFeedbackChanged(db, send, feedback({ authorId: 'noe', status: 'new' }), feedback({ authorId: 'noe', status: 'planned' }));
    assert.equal(sent.length, 0);
  });

  test('not when they wrote it themselves', async () => {
    const { sent, send } = fakeSender();
    await onFeedbackCreated(db, send, feedback({ authorId: 'noe', authorName: 'Noé' }));
    assert.equal(sent.length, 0);
  });
});

describe('screenshot of a removed request', () => {
  const base: FeedbackData = { kind: 'problem', text: 'x', authorId: 'adult1', authorName: 'Camille' };

  test('only the file at the place of this request', () => {
    assert.equal(screenshotToDelete('f1', { ...base, screenshot: 'feedback/adult1/f1.jpg' }), 'feedback/adult1/f1.jpg');
    assert.equal(screenshotToDelete('f1', { ...base, screenshot: 'feedback/adult2/f1.jpg' }), null);
    assert.equal(screenshotToDelete('f1', { ...base, screenshot: 'trips/t1/adult1/1.jpg' }), null);
    assert.equal(screenshotToDelete('f1', base), null);
    assert.equal(screenshotToDelete('f1', undefined), null);
  });
});
