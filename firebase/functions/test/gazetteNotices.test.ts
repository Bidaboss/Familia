// La gazette, les notifications (PR 7 du brief): who is told of a comment or of a publication (pure
// logic), then who sees a card and the sending against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  GazetteCommentData, gazetteCommentMessages, gazettePostMentionMessage, gazetteTag, gazetteThreadViewers,
  notifyGazetteComment, notifyGazettePostMentions,
} from '../src/gazetteNotices';
import type { Notice } from '../src/logic';
import { eveningKind } from '../src/prefs';
import type { Sender } from '../src/store';

const NOON = new Date('2026-11-18T11:00:00Z');
const NIGHT = new Date('2026-11-18T22:30:00Z');

function comment(overrides: Partial<GazetteCommentData> = {}): GazetteCommentData {
  return { text: 'Trop beau !', authorId: 'jules', authorName: 'Jules', ...overrides };
}

const everyone = new Set(['camille', 'ines', 'noe', 'jules', 'lou']);
const byCamille = { authorId: 'camille', authorName: 'Camille' };

describe('who is told of a comment', () => {
  test('the author of the publication, then the earlier commenters, never the one who writes', () => {
    const messages = gazetteCommentMessages('post:p1', comment(), everyone, ['ines', 'jules', 'ines'], byCamille);
    assert.deepEqual(messages.map((m) => m.recipientIds), [['camille'], ['ines']]);
    assert.equal(messages[0].notice.title, 'Jules a commenté ta publication');
    assert.equal(messages[1].notice.title, 'Jules a aussi commenté la publication de Camille');
    assert.equal(messages[0].notice.channel, 'comments');
    assert.equal(messages[0].notice.gazetteThreadId, 'post:p1');
    assert.equal(messages[0].notice.tag, 'gazette:post:p1');
    assert.equal(messages[0].notice.body, 'Trop beau !');
    assert.equal(messages[0].notice.direct, undefined);
  });

  test('who is named hears it once, as « t\'a nommé », and keeps the night (récit 119)', () => {
    const messages = gazetteCommentMessages(
      'post:p1', comment({ text: '@Camille @Inès', mentionIds: ['camille', 'ines', 'jules', 'nina'] }), everyone, ['ines'], byCamille,
    );
    assert.deepEqual(messages.map((m) => m.recipientIds), [['camille', 'ines']]);
    assert.equal(messages[0].notice.title, 'Jules t\'a nommé dans un commentaire');
    assert.equal(messages[0].notice.named, true);
    assert.equal(messages[0].notice.direct, undefined);
    // A mention never waits for the résumé du soir.
    assert.equal(eveningKind(messages[0].notice), null);
  });

  test('the author answering on their own publication tells the others « sa publication »', () => {
    const messages = gazetteCommentMessages('post:p1', comment(byCamille), everyone, ['ines', 'camille'], byCamille);
    assert.deepEqual(messages.map((m) => m.recipientIds), [['ines']]);
    assert.equal(messages[0].notice.title, 'Camille a aussi commenté sa publication');
  });

  test('a card of the family has no author: only the names and the earlier commenters', () => {
    const messages = gazetteCommentMessages('deal:d1', comment({ mentionIds: ['lou'] }), everyone, ['ines'], null);
    assert.deepEqual(messages.map((m) => m.recipientIds), [['lou'], ['ines']]);
    assert.equal(messages[1].notice.title, 'Jules a aussi commenté dans la gazette');
  });

  test('nobody who does not see the card: never the member of « wishes:{him} »', () => {
    const viewers = new Set([...everyone].filter((id) => id !== 'noe'));
    const messages = gazetteCommentMessages('wishes:noe:2026-11-25', comment({ mentionIds: ['noe'] }), viewers, ['noe'], null);
    assert.deepEqual(messages, []);
  });
});

describe('who is told of a publication', () => {
  const post = { ...byCamille, text: 'Au parc avec @Inès', mentionIds: ['ines', 'camille', 'nina'] };

  test('the members it names, never its author nor a child without the app', () => {
    const message = gazettePostMentionMessage('p1', undefined, post, everyone);
    assert.deepEqual(message?.recipientIds, ['ines']);
    assert.equal(message?.notice.title, 'Camille t\'a nommé dans sa publication');
    assert.equal(message?.notice.body, 'Au parc avec @Inès');
    assert.equal(message?.notice.gazetteThreadId, 'post:p1');
    assert.equal(message?.notice.named, true);
  });

  test('an edit tells only who it newly names; a stop of the position tells nobody', () => {
    assert.deepEqual(gazettePostMentionMessage('p1', post, { ...post, mentionIds: ['ines', 'lou'] }, everyone)?.recipientIds, ['lou']);
    assert.equal(gazettePostMentionMessage('p1', post, { ...post, loc: { label: 'Annecy' } }, everyone), null);
  });

  test('a photo or a position alone says so', () => {
    assert.equal(gazettePostMentionMessage('p1', undefined, { ...post, text: '', photoIds: ['a', 'b'] }, everyone)?.notice.body, '📷 2 photos');
    assert.equal(gazettePostMentionMessage('p1', undefined, { ...post, text: '', loc: { label: 'Annecy' } }, everyone)?.notice.body, '📍 Annecy');
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'gazetteNotices');
  const db = getFirestore(app);
  after(async () => { await deleteApp(app); });

  const people = ['camille', 'ines', 'noe', 'jules'];

  function fakeSender() {
    const sent: [string, Notice][] = [];
    const send: Sender = async (token, notice) => {
      sent.push([token, notice]);
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'gazettePosts', 'gazetteThreads', 'trips', 'helpRequests', 'quietQueue']) await db.recursiveDelete(db.collection(c));
    for (const id of people) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', householdId: 'h1', parentIds: [] });
      await db.doc(`members/${id}/devices/phone-${id}`).set({});
    }
    await db.doc('members/nina').set({ firstName: 'Nina', profile: 'childWithoutApp', householdId: 'h1', parentIds: ['jules'] });
    await db.doc('members/nina/devices/phone-nina').set({});
    await db.doc('config/family').set({ ownerMemberId: 'camille' });
    await db.doc('config/app').set({ features: {} });
    await db.doc('gazettePosts/p1').set({ authorId: 'camille', authorName: 'Camille', text: 'Au parc', photoIds: [], mentionIds: [] });
    await db.doc('gazetteThreads/post:p1/comments/c0').set(comment({ authorId: 'ines', authorName: 'Ines' }));
    await db.doc('trips/t1').set({ readerIds: ['camille', 'jules'] });
    await db.doc('helpRequests/r1').set({ viewerIds: ['ines', 'jules', 'nina'] });
  });

  test('who sees a card, as the rules say', async () => {
    const withApp = ['camille', 'ines', 'noe', 'jules'];
    const sorted = async (id: string) => [...await gazetteThreadViewers(db, id, withApp)].sort();
    assert.deepEqual(await sorted('post:p1'), withApp.slice().sort());
    assert.deepEqual(await sorted('post:gone'), []);
    assert.deepEqual(await sorted('album:trips:t1:2026-11-20'), ['camille', 'jules']);
    assert.deepEqual(await sorted('film:t1:f1'), ['camille', 'jules']);
    assert.deepEqual(await sorted('wishes:noe:2026-11-25'), ['camille', 'ines', 'jules']);
    assert.deepEqual(await sorted('help:r1'), ['camille', 'ines', 'jules']);
    assert.deepEqual(await sorted('help:gone'), []);
    assert.deepEqual(await sorted('event:e1'), withApp.slice().sort());
    assert.deepEqual(await sorted('mystery:x'), []);
  });

  test('a comment reaches the author and the earlier commenters, at noon', async () => {
    const { sent, send } = fakeSender();
    await db.doc('gazetteThreads/post:p1/comments/c1').set(comment());
    await notifyGazetteComment(db, send, 'post:p1', 'c1', comment(), NOON);
    assert.deepEqual(sent.map(([token, n]) => [token, n.title]), [
      ['phone-camille', 'Jules a commenté ta publication'],
      ['phone-ines', 'Jules a aussi commenté la publication de Camille'],
    ]);
    assert.equal(sent[0][1].gazetteThreadId, 'post:p1');
  });

  test('at night a mention waits for 8 h, as the rest (récit 119)', async () => {
    const { sent, send } = fakeSender();
    await notifyGazetteComment(db, send, 'post:p1', 'c1', comment({ mentionIds: ['noe'] }), NIGHT);
    assert.deepEqual(sent, []);
    const held = (await db.doc('quietQueue/noe').get()).get('items') as { title: string }[];
    assert.deepEqual(held.map((n) => n.title), ['Jules t\'a nommé dans un commentaire']);
  });

  test('a comment on a removed publication tells nobody', async () => {
    const { sent, send } = fakeSender();
    await db.doc('gazettePosts/p1').delete();
    assert.equal(await notifyGazetteComment(db, send, 'post:p1', 'c1', comment(), NOON), 0);
    assert.deepEqual(sent, []);
  });

  test('a publication tells who it names, never a child without the app', async () => {
    const { sent, send } = fakeSender();
    await notifyGazettePostMentions(db, send, 'p2', undefined, { ...byCamille, text: 'Coucou', mentionIds: ['jules', 'nina'] }, NOON);
    assert.deepEqual(sent.map(([token]) => token), ['phone-jules']);
    assert.equal(sent[0][1].tag, gazetteTag('post:p2'));
  });
});
