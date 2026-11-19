// Bonnes nouvelles: who is told what (pure logic), then the sending and the photo cleanup against
// the Firestore emulator (npm run test:functions in firebase/). Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  deleteReactionDigest, DIGEST_DELAY_MS, GoodNewsData, GoodNewsMember, goodNewsMessages, GoodNewsNotice, GoodNewsSender,
  newReactorIds, onGoodNewsEdited, onGoodNewsPublished, onGoodNewsReacted, onGoodNewsRemoved, photoPaths, preview,
  reactionsNotice, sendReactionDigests,
} from '../src/goodNews';

const members: GoodNewsMember[] = [
  { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [] },
  { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [] },
  { id: 'jules', firstName: 'Jules', profile: 'adult', parentIds: [] },
  { id: 'noe', firstName: 'Noé', profile: 'childWithApp', parentIds: ['camille', 'jules'] },
  { id: 'lou', firstName: 'Lou', profile: 'childWithoutApp', parentIds: ['ines'] },
];

function news(overrides: Partial<GoodNewsData> = {}): GoodNewsData {
  return {
    text: 'Premier tour de vélo sans roulettes', photoIds: ['p1', 'p2'], authorId: 'camille', authorName: 'Camille',
    forMemberId: 'camille', mentionIds: [], ...overrides,
  };
}

const none = new Set<string>();

describe('who is told what', () => {
  test('the whole family with the app is told, never the author', () => {
    const messages = goodNewsMessages('n1', news(), members, none);
    assert.equal(messages.length, 1);
    assert.deepEqual(messages[0].recipientIds, ['ines', 'jules', 'noe']);
    assert.equal(messages[0].notice.title, 'Actualité de Camille');
    assert.equal(messages[0].notice.body, 'Premier tour de vélo sans roulettes');
    assert.equal(messages[0].notice.goodNewsId, 'n1');
  });

  test('a good news for a child names the child', () => {
    const [message] = goodNewsMessages('n1', news({ forMemberId: 'lou', authorId: 'ines', authorName: 'Inès' }), members, none);
    assert.equal(message.notice.title, 'Actualité pour Lou');
    assert.equal(message.notice.body, 'Inès : Premier tour de vélo sans roulettes');
  });

  test('those who turned the good news off are not told', () => {
    const [message] = goodNewsMessages('n1', news(), members, new Set(['jules']));
    assert.deepEqual(message.recipientIds, ['ines', 'noe']);
  });

  test('a child who publishes: the responsible adults get their own notice, even when muted (AVIS-12)', () => {
    const messages = goodNewsMessages('n1', news({ authorId: 'noe', authorName: 'Noé', forMemberId: 'noe' }), members, new Set(['camille', 'ines']));
    // Inès turned them off, so only the parents' own notice is left.
    assert.equal(messages.length, 1);
    assert.deepEqual(messages[0].recipientIds, ['camille', 'jules']);
    assert.equal(messages[0].notice.title, 'Noé a publié une actualité');
    const unmuted = goodNewsMessages('n1', news({ authorId: 'noe', authorName: 'Noé', forMemberId: 'noe' }), members, none);
    assert.deepEqual(unmuted.map((m) => m.recipientIds), [['ines'], ['camille', 'jules']]);
  });

  test('nobody to tell gives no message', () => {
    const alone = [members[0]];
    assert.deepEqual(goodNewsMessages('n1', news(), alone, none), []);
  });

  test('a long text is cut on one line', () => {
    assert.equal(preview('Une\nphrase   courte'), 'Une phrase courte');
    const long = preview('a'.repeat(150));
    assert.equal(long.length, 100);
    assert.ok(long.endsWith('…'));
  });

  test('each photo has its file and its thumbnail', () => {
    assert.deepEqual(photoPaths('n1', 'camille', ['p1']), ['goodNews/camille/n1/p1.jpg', 'goodNews/camille/n1/p1-thumb.jpg']);
  });
});

describe('grouped reactions (PRD rév. 174)', () => {
  test('only the members who just reacted count, never the author', () => {
    const before = news({ reactions: { ines: 'heart' } });
    const after = news({ reactions: { ines: 'party', jules: 'clap', camille: 'heart' } });
    assert.deepEqual(newReactorIds(before, after), ['jules']);
    assert.deepEqual(newReactorIds(after, news({ reactions: { jules: 'clap' } })), []);
  });

  test('one, two, then several names', () => {
    assert.equal(reactionsNotice('n1', news(), ['Inès'])?.title, 'Inès a réagi à ton actualité');
    assert.equal(reactionsNotice('n1', news(), ['Inès', 'Jules'])?.title, 'Inès et Jules ont réagi à ton actualité');
    const many = reactionsNotice('n1', news(), ['Inès', 'Jules', 'Noé', 'Lou']);
    assert.equal(many?.title, 'Inès et 3 autres ont réagi à ton actualité');
    assert.equal(many?.channel, 'comments');
    assert.equal(many?.goodNewsId, 'n1');
    assert.equal(reactionsNotice('n1', news(), []), null);
  });
});

describe('photos of a removed or edited good news', () => {
  test('removing deletes every photo, editing only those taken out', async () => {
    const removed: string[] = [];
    const remove = async (paths: string[]) => { removed.push(...paths); };
    await onGoodNewsEdited(remove, 'n1', news(), news({ photoIds: ['p2'] }));
    assert.deepEqual(removed, ['goodNews/camille/n1/p1.jpg', 'goodNews/camille/n1/p1-thumb.jpg']);
    removed.length = 0;
    await onGoodNewsEdited(remove, 'n1', news(), news({ text: 'Autre' }));
    assert.deepEqual(removed, []);
    await onGoodNewsRemoved(remove, 'n1', news());
    assert.equal(removed.length, 4);
  });
});

describe('sending, against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'goodNews');
  const db = getFirestore(app);
  after(async () => { await deleteApp(app); });

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    for (const m of members) {
      await db.collection('members').doc(m.id).set({ firstName: m.firstName, profile: m.profile, parentIds: m.parentIds });
      if (m.profile !== 'childWithoutApp') await db.collection('members').doc(m.id).collection('devices').doc(`phone-${m.id}`).set({});
    }
    await db.collection('members').doc('ines').collection('settings').doc('notifications').set({ goodNews: false });
  });

  test('every phone of those told, the muted setting read from Firestore, and an uninstalled app forgotten', async () => {
    const sent: [string, GoodNewsNotice][] = [];
    const send: GoodNewsSender = async (token, notice) => {
      sent.push([token, notice]);
      return token === 'phone-noe' ? 'unregistered' : 'ok';
    };
    const count = await onGoodNewsPublished(db, send, 'n1', news());
    assert.deepEqual(sent.map(([t]) => t), ['phone-jules', 'phone-noe']);
    assert.equal(count, 1);
    assert.equal((await db.collection('members').doc('noe').collection('devices').get()).size, 0);
  });

  test('a grouped notice an hour after the first reaction, once per reactor, and gone with the post', async () => {
    await db.recursiveDelete(db.collection('goodNews'));
    await db.recursiveDelete(db.collection('goodNewsDigests'));
    const t0 = new Date('2026-11-21T15:00:00Z');
    const post = news({ reactions: { ines: 'heart', jules: 'clap' } });
    await db.collection('goodNews').doc('n1').set(post);
    await onGoodNewsReacted(db, 'n1', news({ reactions: {} }), news({ reactions: { ines: 'heart' } }), t0);
    // A second reaction ten minutes later does not push the time back.
    await onGoodNewsReacted(db, 'n1', news({ reactions: { ines: 'heart' } }), post, new Date(t0.getTime() + 600_000));

    const sent: [string, GoodNewsNotice][] = [];
    const send: GoodNewsSender = async (token, notice) => { sent.push([token, notice]); return 'ok'; };
    assert.equal(await sendReactionDigests(db, send, new Date(t0.getTime() + DIGEST_DELAY_MS - 1)), 0);
    assert.equal(await sendReactionDigests(db, send, new Date(t0.getTime() + DIGEST_DELAY_MS)), 1);
    assert.deepEqual(sent.map(([t, n]) => [t, n.title]), [['phone-camille', 'Inès et Jules ont réagi à ton actualité']]);
    assert.equal(await sendReactionDigests(db, send, new Date(t0.getTime() + 2 * DIGEST_DELAY_MS)), 0);

    // Noé reacts the next day: only Noé is named.
    const later = news({ reactions: { ines: 'heart', jules: 'clap', noe: 'wow' } });
    await db.collection('goodNews').doc('n1').set(later);
    const t1 = new Date(t0.getTime() + 86_400_000);
    await onGoodNewsReacted(db, 'n1', post, later, t1);
    await sendReactionDigests(db, send, new Date(t1.getTime() + DIGEST_DELAY_MS));
    assert.equal(sent[1][1].title, 'Noé a réagi à ton actualité');

    await deleteReactionDigest(db, 'n1');
    assert.equal((await db.collection('goodNewsDigests').doc('n1').get()).exists, false);
  });
});
