// Commentaires d'un événement (récit 28): the text of the notification (pure logic), then who is
// told and the cleanup, against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import {
  CommentData, commentMentionNotice, commentNotice, commentRecipients, deleteCommentMutesOf, deleteEventComments, onCommentCreated,
} from '../src/comments';
import { Sender } from '../src/store';

function comment(overrides: Partial<CommentData> = {}): CommentData {
  return { text: 'Je prends le gâteau', authorId: 'ines', authorName: 'Inès', ...overrides };
}

describe('text', () => {
  test('it says who and which event, then the comment', () => {
    const n = commentNotice('e1', 'Pique-nique', comment());
    assert.equal(n.title, 'Inès a commenté « Pique-nique »');
    assert.equal(n.body, 'Je prends le gâteau');
    assert.equal(n.channel, 'comments');
    assert.equal(n.eventId, 'e1');
  });

  test('a member named « @Prénom » is told directly, with the event', () => {
    const n = commentMentionNotice('e1', 'Pique-nique', comment({ text: '@Noé tu prends le pain ?' }));
    assert.equal(n.title, 'Inès t\'a nommé dans un commentaire');
    assert.equal(n.body, '« Pique-nique » : @Noé tu prends le pain ?');
    assert.equal(n.direct, true);
    assert.equal(n.eventId, 'e1');
  });

  test('a long comment is cut, on one line', () => {
    const body = commentNotice('e1', 'Pique-nique', comment({ text: `Ligne\n\n${'a'.repeat(300)}` })).body;
    assert.equal(body.length, 120);
    assert.ok(body.startsWith('Ligne a'));
    assert.ok(body.endsWith('…'));
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'comments-test');
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

  const people = ['camille', 'ines', 'noe', 'lou', 'jules'];

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('events'));
    await db.recursiveDelete(db.collection('members'));
    for (const id of people) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', householdId: 'h1', parentIds: [], uid: null });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
    // Nina has no phone: her parent Jules answers for her.
    await db.doc('members/nina').set({ firstName: 'Nina', profile: 'childWithoutApp', householdId: 'h1', parentIds: ['jules'], uid: null });
    await db.doc('events/e1').set({ title: 'Pique-nique', authorId: 'camille', authorName: 'Camille' });
    await db.doc('config/app').set({ features: { comments: true } });
    await db.doc('config/family').set({ ownerMemberId: 'noe' });
  });

  test('the author of the event, earlier commenters and those coming, never the writer', async () => {
    await db.doc('events/e1/comments/c0').set(comment({ authorId: 'lou', authorName: 'Lou' }));
    await db.doc('events/e1/rsvps/noe').set({ status: 'maybe' });
    await db.doc('events/e1/rsvps/ines').set({ status: 'yes' });
    await db.doc('events/e1/rsvps/nina').set({ status: 'yes' });
    await db.doc('events/e1/comments/c1').set(comment());
    assert.deepEqual(await commentRecipients(db, 'e1', 'c1', comment()), ['camille', 'jules', 'lou', 'noe']);
  });

  test('those who said « Je ne viens pas » and those who muted are left out', async () => {
    await db.doc('events/e1/rsvps/noe').set({ status: 'no' });
    await db.doc('events/e1/rsvps/lou').set({ status: 'yes' });
    await db.doc('events/e1/commentMutes/camille').set({ mutedAt: new Date() });
    assert.deepEqual(await commentRecipients(db, 'e1', 'c1', comment()), ['lou']);
  });

  test('a member who left is not told', async () => {
    await db.doc('events/e1/comments/c0').set(comment({ authorId: 'ancien', authorName: 'Sam' }));
    assert.deepEqual(await commentRecipients(db, 'e1', 'c1', comment()), ['camille']);
  });

  test('module on: everyone told gets the notice', async () => {
    await db.doc('events/e1/rsvps/lou').set({ status: 'yes' });
    const { sent, send } = fakeSender();
    await onCommentCreated(db, send, 'e1', 'c1', comment());
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-camille', 'token-lou']);
    assert.equal(sent[0].notice.title, 'Inès a commenté « Pique-nique »');
  });

  test('who is named hears « t\'a nommé » once, never the writer nor a child without the app', async () => {
    await db.doc('events/e1/rsvps/lou').set({ status: 'yes' });
    const { sent, send } = fakeSender();
    await onCommentCreated(db, send, 'e1', 'c1', comment({ mentionIds: ['lou', 'jules', 'ines', 'nina', 'lou'] }));
    const byToken = new Map(sent.map((s) => [s.token, s.notice.title]));
    assert.deepEqual([...byToken.keys()].sort(), ['token-camille', 'token-jules', 'token-lou']);
    assert.equal(byToken.get('token-lou'), 'Inès t\'a nommé dans un commentaire');
    assert.equal(byToken.get('token-jules'), 'Inès t\'a nommé dans un commentaire');
    assert.equal(byToken.get('token-camille'), 'Inès a commenté « Pique-nique »');
    assert.equal(sent.length, 3);
  });

  test('module off: only the admin principal, when concerned', async () => {
    await db.doc('config/app').set({ features: { comments: false } });
    await db.doc('events/e1/rsvps/noe').set({ status: 'yes' });
    const first = fakeSender();
    await onCommentCreated(db, first.send, 'e1', 'c1', comment());
    assert.deepEqual(first.sent.map((s) => s.token), ['token-noe']);
    await db.doc('events/e1/rsvps/noe').delete();
    const second = fakeSender();
    await onCommentCreated(db, second.send, 'e1', 'c2', comment());
    assert.equal(second.sent.length, 0);
  });

  test('a deleted event takes its comments and mutes', async () => {
    await db.doc('events/e1/comments/c0').set(comment());
    await db.doc('events/e1/commentMutes/lou').set({ mutedAt: new Date() });
    await deleteEventComments(db, 'e1');
    assert.equal((await db.collection('events/e1/comments').get()).size, 0);
    assert.equal((await db.collection('events/e1/commentMutes').get()).size, 0);
  });

  test('a member removed: their mutes go, their comments stay', async () => {
    await db.doc('events/e1/comments/c0').set(comment({ authorId: 'lou', authorName: 'Lou' }));
    await db.doc('events/e1/commentMutes/lou').set({ mutedAt: new Date() });
    await db.doc('events/e1/commentMutes/noe').set({ mutedAt: new Date() });
    await deleteCommentMutesOf(db, 'lou');
    assert.equal((await db.doc('events/e1/commentMutes/lou').get()).exists, false);
    assert.equal((await db.doc('events/e1/commentMutes/noe').get()).exists, true);
    assert.equal((await db.doc('events/e1/comments/c0').get()).exists, true);
  });
});
