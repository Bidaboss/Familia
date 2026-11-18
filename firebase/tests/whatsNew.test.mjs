// « Quoi de neuf ? » (PRD, récit 6 septies): the texts of docs/whats-new and how the "APK de
// publication" workflow numbers them. No emulator needed; it runs with the other tests.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { checkFile, dueFiles, mergeWhatsNew, parisToday, readWhatsNew } from '../functions/scripts/whats-new.mjs';

const card = (id, more = {}) => ({ id, title: `Titre ${id}`, body: `Une phrase pour ${id}.`, ...more });

test('the files of the repository are all good', () => {
  // Throws with the list of problems: the pull request fails before the workflow does.
  readWhatsNew(new URL('../../docs/whats-new/', import.meta.url));
});

test('a file holds 1 to 3 cards, each with an id, a title and a sentence', () => {
  assert.deepEqual(checkFile('a.json', { cards: [card('un')] }), []);
  assert.equal(checkFile('a.json', { cards: [] }).length, 1);
  assert.equal(checkFile('a.json', { cards: [card('a'), card('b'), card('c'), card('d')] }).length, 1);
  assert.ok(checkFile('a.json', { cards: [{ id: 'sans-texte' }] }).length >= 2);
  assert.ok(checkFile('a.json', { cards: [card('Majuscule')] }).length >= 1);
  assert.ok(checkFile('a.json', { cards: [card('long', { body: 'x'.repeat(141) })] }).length >= 1);
  assert.ok(checkFile('a.json', { cards: [card('faute', { adultOnly: true })] }).length >= 1);
  assert.ok(checkFile('a.json', { cards: [card('enfant', { adultsOnly: 'oui' })] }).length >= 1);
  assert.deepEqual(checkFile('a.json', { cards: [card('ok', { childBody: 'Pour toi.', feature: 'recipes', open: 'recipes', adultsOnly: false })] }), []);
});

test('two cards with the same id are refused, even in two files', () => {
  const dir = mkdtempSync(join(tmpdir(), 'whats-new-'));
  writeFileSync(join(dir, '2026-11-28.json'), JSON.stringify({ cards: [card('recettes')] }));
  writeFileSync(join(dir, '2026-12-05.json'), JSON.stringify({ cards: [card('recettes')] }));
  writeFileSync(join(dir, 'README.md'), 'pas lu');
  assert.throws(() => readWhatsNew(dir), /déjà pris/);
});

test('a new file takes the version published, an old one keeps its number, a removed one leaves', () => {
  const history = [
    { id: '2026-11-28', versionCode: 210, versionName: '1.210', cards: [card('a')] },
    { id: 'retire', versionCode: 205, versionName: '1.205', cards: [card('z')] },
  ];
  const files = [
    { id: '2026-11-28', cards: [card('a', { body: 'Texte corrigé.' })] },
    { id: '2026-12-05', cards: [card('b')] },
  ];
  const merged = mergeWhatsNew(history, files, 230, '1.230');
  assert.deepEqual(merged.map((r) => [r.id, r.versionCode]), [['2026-11-28', 210], ['2026-12-05', 230]]);
  assert.equal(merged[0].cards[0].body, 'Texte corrigé.');
  assert.deepEqual(mergeWhatsNew(undefined, files, 230, '1.230').map((r) => r.versionCode), [230, 230]);
});

test('a file dated later waits for its own publication, and leaves a history it entered too early', () => {
  const files = [
    { id: '2026-11-20', cards: [card('a')] },
    { id: '2026-11-23', cards: [card('b')] },
    { id: 'sans-date', cards: [card('c')] },
  ];
  assert.deepEqual(dueFiles(files, '2026-11-20').map((f) => f.id), ['2026-11-20', 'sans-date']);
  assert.deepEqual(dueFiles(files, '2026-11-23').map((f) => f.id), ['2026-11-20', '2026-11-23', 'sans-date']);
  // Published too early with the same number: the next publication takes it out, a later one gives it its own.
  const tooEarly = [{ id: '2026-11-23', versionCode: 200, versionName: '1.200', cards: [card('b')] }];
  assert.deepEqual(mergeWhatsNew(tooEarly, dueFiles(files, '2026-11-20'), 210, '1.210').map((r) => r.id), ['2026-11-20', 'sans-date']);
});

test("today is read in Paris", () => {
  assert.equal(parisToday(new Date('2026-11-20T23:30:00Z')), '2026-11-21');
  assert.equal(parisToday(new Date('2026-11-20T12:00:00Z')), '2026-11-20');
});
