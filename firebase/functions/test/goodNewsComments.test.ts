// Commentaires des bonnes nouvelles (récit 67): who is told (pure logic), then the count, the
// sending and the cleanup against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { GoodNewsNotice, GoodNewsSender } from '../src/goodNews';
import {
  deleteGoodNewsComments, GoodNewsCommentData, goodNewsCommentMessages, onGoodNewsCommentCreated, onGoodNewsCommentDeleted,
} from '../src/goodNewsComments';

const NOON = new Date('2026-11-18T11:00:00Z');

function comment(overrides: Partial<GoodNewsCommentData> = {}): GoodNewsCommentData {
  return { text: 'Champion !', authorId: 'jules', authorName: 'Jules', ...overrides };
}

const everyone = new Set(['camille', 'ines', 'noe', 'jules', 'lou']);

describe('who is told', () => {
  test('the author and the member it is for, then the earlier commenters, never the one who writes', () => {
    const messages = goodNewsCommentMessages('n1', { authorId: 'camille', forMemberId: 'noe' }, comment(), ['ines', 'jules', 'ines'], new Set(), everyone);
    assert.deepEqual(messages.map((m) => m.recipientIds), [['camille', 'noe'], ['ines']]);
    assert.equal(messages[0].notice.title, 'Jules a commenté ta Bonne nouvelle');
    assert.equal(messages[1].notice.title, 'Jules a aussi commenté la Bonne nouvelle');
    assert.equal(messages[0].notice.channel, 'comments');
    assert.equal(messages[0].notice.goodNewsId, 'n1');
    assert.equal(messages[0].notice.body, 'Champion !');
  });

  test('not those who muted the comments, hid the news or have no app', () => {
    const messages = goodNewsCommentMessages(
      'n1', { authorId: 'camille', forMemberId: 'noe', hiddenByIds: ['noe'] }, comment(), ['ines', 'lou', 'nina'],
      new Set(['ines']), everyone,
    );
    assert.deepEqual(messages.map((m) => m.recipientIds), [['camille'], ['lou']]);
  });

  test('the author commenting their own news tells only the others', () => {
    const messages = goodNewsCommentMessages('n1', { authorId: 'camille', forMemberId: 'camille' }, comment({ authorId: 'camille', authorName: 'Camille' }), ['ines'], new Set(), everyone);
    assert.deepEqual(messages.map((m) => m.recipientIds), [['ines']]);
  });

  test('a « Défi réussi » of Familia has no author: only the commenters', () => {
    assert.deepEqual(goodNewsCommentMessages('n1', {}, comment(), [], new Set(), everyone), []);
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'goodNewsComments');
  const db = getFirestore(app);
  after(async () => { await deleteApp(app); });

  const people = ['camille', 'ines', 'noe', 'jules'];

  function fakeSender() {
    const sent: [string, GoodNewsNotice][] = [];
    const send: GoodNewsSender = async (token, notice) => {
      sent.push([token, notice]);
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('goodNews'));
    await db.doc('config/presentation').delete();
    for (const id of people) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', householdId: 'h1', parentIds: [] });
      await db.doc(`members/${id}/devices/phone-${id}`).set({});
    }
    await db.doc('members/nina').set({ firstName: 'Nina', profile: 'childWithoutApp', householdId: 'h1', parentIds: ['jules'] });
    await db.doc('config/family').set({ ownerMemberId: 'camille' });
    await db.doc('config/app').set({ features: { newsFeed: true } });
    await db.doc('goodNews/n1').set({ text: 'Ceinture jaune', authorId: 'camille', authorName: 'Camille', forMemberId: 'noe', hiddenByIds: [] });
    await db.doc('goodNews/n1/comments/c0').set(comment({ authorId: 'ines', authorName: 'Ines' }));
    await db.doc('goodNews/n1/commentMutes/noe').set({ mutedAt: NOON });
  });

  test('the count goes up, and the phones of those concerned ring', async () => {
    await db.doc('goodNews/n1/comments/c1').set(comment());
    const { sent, send } = fakeSender();
    await onGoodNewsCommentCreated(db, send, 'n1', 'c1', comment(), NOON);
    assert.deepEqual(sent.map(([token]) => token), ['phone-camille', 'phone-ines']);
    assert.equal((await db.doc('goodNews/n1').get()).get('commentCount'), 1);
  });

  test('until the fil is switched on, only the admin principal is told', async () => {
    await db.doc('config/app').set({ features: {} });
    const { sent, send } = fakeSender();
    await onGoodNewsCommentCreated(db, send, 'n1', 'c1', comment(), NOON);
    assert.deepEqual(sent.map(([token]) => token), ['phone-camille']);
  });

  test('the count goes down, never below 0, and never brings back a removed news', async () => {
    await db.doc('goodNews/n1').update({ commentCount: 1 });
    await onGoodNewsCommentDeleted(db, 'n1');
    await onGoodNewsCommentDeleted(db, 'n1');
    assert.equal((await db.doc('goodNews/n1').get()).get('commentCount'), 0);
    await onGoodNewsCommentDeleted(db, 'gone');
    assert.equal((await db.doc('goodNews/gone').get()).exists, false);
  });

  test('a removed news takes its comments and its mutes', async () => {
    await deleteGoodNewsComments(db, 'n1');
    assert.equal((await db.collection('goodNews/n1/comments').get()).size, 0);
    assert.equal((await db.collection('goodNews/n1/commentMutes').get()).size, 0);
  });
});
