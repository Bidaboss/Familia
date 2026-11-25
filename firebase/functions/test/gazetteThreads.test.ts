// La gazette (récits 115, 116): the count and the 2 latest comments kept on the thread of a card,
// against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { deleteGazetteThread, lastText, refreshGazetteThread } from '../src/gazetteThreads';

const app = initializeApp({ projectId: 'demo-familia' }, 'gazette-threads');
const db = getFirestore(app);
after(async () => { await deleteApp(app); });

const thread = (id = 'post:p1') => db.collection('gazetteThreads').doc(id);

async function add(id: string, by: string, text: string, minute: number, threadId = 'post:p1') {
  await thread(threadId).collection('comments').doc(id).set({
    text, authorId: by, authorName: by, createdAt: Timestamp.fromMillis(minute * 60_000),
  });
}

beforeEach(async () => {
  await db.recursiveDelete(db.collection('gazetteThreads'));
});

describe('the text of the card', () => {
  test('on one line, cut at 200 characters', () => {
    assert.equal(lastText('  Trop\n\nbien  '), 'Trop bien');
    assert.equal(lastText('a'.repeat(250)).length, 200);
    assert.ok(lastText('a'.repeat(250)).endsWith('…'));
  });
});

describe('count and 2 latest', () => {
  test('a new comment: the count goes up, the 2 latest are kept oldest first, the J\'aime stay', async () => {
    await thread().set({ reactions: { camille: '💛' } });
    await add('c1', 'camille', 'Un', 1);
    await add('c2', 'ines', 'Deux', 2);
    await add('c3', 'jules', 'Trois', 3);
    await refreshGazetteThread(db, 'post:p1');
    const data = (await thread().get()).data()!;
    assert.equal(data.commentCount, 3);
    assert.deepEqual(data.last.map((c: { id: string }) => c.id), ['c2', 'c3']);
    assert.equal(data.last[1].authorName, 'jules');
    assert.equal(data.last[1].text, 'Trois');
    assert.deepEqual(data.reactions, { camille: '💛' });
  });

  test('a removed comment: counted again; a thread with no J\'aime yet is made by the first comment', async () => {
    await add('c1', 'camille', 'Un', 1, 'help:r1');
    await refreshGazetteThread(db, 'help:r1');
    assert.equal((await thread('help:r1').get()).get('commentCount'), 1);
    await thread('help:r1').collection('comments').doc('c1').delete();
    await refreshGazetteThread(db, 'help:r1');
    const data = (await thread('help:r1').get()).data()!;
    assert.equal(data.commentCount, 0);
    assert.deepEqual(data.last, []);
  });

  test('run twice, the same; nothing written for a thread that never had anything', async () => {
    await add('c1', 'camille', 'Un', 1);
    await refreshGazetteThread(db, 'post:p1');
    await refreshGazetteThread(db, 'post:p1');
    assert.equal((await thread().get()).get('commentCount'), 1);
    await refreshGazetteThread(db, 'post:none');
    assert.equal((await thread('post:none').get()).exists, false);
  });

  test('a removed card takes its J\'aime and its comments', async () => {
    await thread().set({ reactions: { camille: '💛' } });
    await add('c1', 'camille', 'Un', 1);
    await deleteGazetteThread(db, 'post:p1');
    assert.equal((await thread().get()).exists, false);
    assert.equal((await thread().collection('comments').get()).size, 0);
  });
});
