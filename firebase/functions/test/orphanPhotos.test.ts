// Ménage des photos orphelines (note C de #263): which files would go, and that nothing is deleted.
// Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { orphanGoodNewsPhotos, orphanReportDue, reportOrphanGoodNewsPhotos, type StoredFile } from '../src/orphanPhotos';

const NOW = new Date('2026-11-22T03:00:00Z');
const OLD = new Date('2026-11-20T10:00:00Z');
const file = (name: string, updated: Date = OLD, size = 1000): StoredFile => ({ name, updated, size });

describe('orphanGoodNewsPhotos', () => {
  const posts = new Map([['n1', { authorId: 'camille', photoIds: ['p1'] }]]);

  test('a photo and its thumbnail shown by their good news stay', () => {
    const files = [file('goodNews/camille/n1/p1.jpg'), file('goodNews/camille/n1/p1-thumb.jpg')];
    assert.deepEqual(orphanGoodNewsPhotos(files, posts, NOW), []);
  });

  test('a photo the good news does not list, or of a good news gone, is an orphan', () => {
    const files = [
      file('goodNews/camille/n1/p2.jpg'),
      file('goodNews/camille/n1/p2-thumb.jpg'),
      file('goodNews/ines/n9/p1.jpg'),
      file('goodNews/ines/n1/p1.jpg'),
    ];
    assert.deepEqual(orphanGoodNewsPhotos(files, posts, NOW).map((f) => f.name), files.map((f) => f.name));
  });

  test('a file sent less than a day ago, or of another shape, is left alone', () => {
    const files = [
      file('goodNews/camille/n2/p1.jpg', new Date('2026-11-21T20:00:00Z')),
      file('goodNews/camille/n1/notes.txt'),
      file('trips/t1/camille/p1.jpg'),
    ];
    assert.deepEqual(orphanGoodNewsPhotos(files, posts, NOW), []);
  });
});

describe('orphanReportDue', () => {
  test('only the run of Sunday between 4 h 00 and 4 h 29 in Paris makes the report', () => {
    // 22/11/2026 is a Sunday; Paris is at UTC+1 in November.
    assert.equal(orphanReportDue(new Date('2026-11-22T03:00:00Z')), true);
    assert.equal(orphanReportDue(new Date('2026-11-22T03:29:00Z')), true);
    assert.equal(orphanReportDue(new Date('2026-11-22T03:30:00Z')), false);
    assert.equal(orphanReportDue(new Date('2026-11-22T02:59:00Z')), false);
    assert.equal(orphanReportDue(new Date('2026-11-21T03:00:00Z')), false);
    // In summer Paris is at UTC+2: Sunday 28/06/2026 at 4 h is 2 h UTC.
    assert.equal(orphanReportDue(new Date('2026-06-28T02:00:00Z')), true);
  });
});

describe('reportOrphanGoodNewsPhotos', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'orphan-photos');
  const db = getFirestore(app);

  beforeEach(async () => {
    const docs = await db.collection('goodNews').listDocuments();
    await Promise.all(docs.map((d) => d.delete()));
  });

  after(async () => {
    await deleteApp(app);
  });

  test('logs what would be deleted, and lists only the good news photos', async () => {
    await db.collection('goodNews').doc('n1').set({ authorId: 'camille', photoIds: ['p1'], text: 'Le repas' });
    const prefixes: string[] = [];
    const logs: string[] = [];
    const orphans = await reportOrphanGoodNewsPhotos(db, async (prefix) => {
      prefixes.push(prefix);
      return [file('goodNews/camille/n1/p1.jpg'), file('goodNews/camille/n1/p2.jpg', OLD, 2048)];
    }, NOW, (m) => logs.push(m));
    assert.deepEqual(prefixes, ['goodNews/']);
    assert.deepEqual(orphans.map((f) => f.name), ['goodNews/camille/n1/p2.jpg']);
    assert.match(logs[0], /1 photo\(s\) orpheline\(s\) sur 2, 2 Ko, rien n'est supprimé/);
    assert.equal(logs[1], 'orphanPhotos : serait supprimée goodNews/camille/n1/p2.jpg');
  });
});
