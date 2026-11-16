// « Quoi de neuf ? » (PRD, récit 6 septies): reads the texts of docs/whats-new/*.json and builds
// config/app.whatsNew, the history of every version, for the "APK de publication" workflow
// (publish-release.mjs). The format is in docs/whats-new/README.md. No dependency.
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const MAX_CARDS_PER_FILE = 3;
export const TITLE_MAX = 40;
export const BODY_MAX = 140;
const TEXT_FIELDS = ['title', 'body', 'childTitle', 'childBody'];
const KNOWN_FIELDS = new Set(['id', ...TEXT_FIELDS, 'feature', 'open', 'adultsOnly']);

/** The problems of one file, in simple French; empty when the file is good. */
export function checkFile(name, content) {
  const problems = [];
  const where = (index) => `${name}, carte ${index + 1}`;
  if (!content || !Array.isArray(content.cards)) return [`${name} : il faut une liste « cards ».`];
  if (content.cards.length < 1 || content.cards.length > MAX_CARDS_PER_FILE) {
    problems.push(`${name} : de 1 à ${MAX_CARDS_PER_FILE} cartes par version, pas ${content.cards.length}.`);
  }
  content.cards.forEach((card, index) => {
    if (typeof card !== 'object' || card === null) {
      problems.push(`${where(index)} : ce n'est pas une carte.`);
      return;
    }
    for (const key of Object.keys(card)) {
      if (!KNOWN_FIELDS.has(key)) problems.push(`${where(index)} : le champ « ${key} » est inconnu.`);
    }
    if (typeof card.id !== 'string' || !/^[a-z0-9-]{1,40}$/.test(card.id)) {
      problems.push(`${where(index)} : « id » en minuscules, chiffres et tirets.`);
    }
    for (const key of ['title', 'body']) {
      if (typeof card[key] !== 'string' || !card[key].trim()) problems.push(`${where(index)} : « ${key} » manque.`);
    }
    for (const key of TEXT_FIELDS) {
      if (card[key] === undefined) continue;
      const max = key.toLowerCase().includes('title') ? TITLE_MAX : BODY_MAX;
      if (typeof card[key] !== 'string') problems.push(`${where(index)} : « ${key} » doit être un texte.`);
      else if (card[key].length > max) problems.push(`${where(index)} : « ${key} » dépasse ${max} caractères.`);
    }
    for (const key of ['feature', 'open']) {
      if (card[key] !== undefined && (typeof card[key] !== 'string' || !card[key])) {
        problems.push(`${where(index)} : « ${key} » doit être un texte.`);
      }
    }
    if (card.adultsOnly !== undefined && typeof card.adultsOnly !== 'boolean') {
      problems.push(`${where(index)} : « adultsOnly » vaut true ou false.`);
    }
  });
  return problems;
}

/**
 * Every file of [dir], sorted by name: [{ id, cards }]. Throws with every problem at once, also
 * when two cards share an id.
 */
export function readWhatsNew(where) {
  const dir = where instanceof URL ? fileURLToPath(where) : where;
  const files = readdirSync(dir).filter((name) => name.endsWith('.json')).sort();
  const problems = [];
  const releases = [];
  const ids = new Map();
  for (const name of files) {
    let content;
    try {
      content = JSON.parse(readFileSync(join(dir, name), 'utf8'));
    } catch (error) {
      problems.push(`${name} : JSON illisible (${error.message}).`);
      continue;
    }
    const fileProblems = checkFile(name, content);
    problems.push(...fileProblems);
    if (fileProblems.length) continue;
    for (const card of content.cards) {
      if (ids.has(card.id)) problems.push(`${name} : l'id « ${card.id} » est déjà pris dans ${ids.get(card.id)}.`);
      ids.set(card.id, name);
    }
    releases.push({ id: name.slice(0, -'.json'.length), cards: content.cards });
  }
  if (problems.length) throw new Error(problems.join('\n'));
  return releases;
}

/**
 * The new config/app.whatsNew. A file already published keeps its version number, with its texts
 * brought up to date; a new file takes the version being published; a removed file leaves the
 * history. Oldest first.
 */
export function mergeWhatsNew(history, files, versionCode, versionName) {
  const known = new Map((Array.isArray(history) ? history : []).map((entry) => [entry.id, entry]));
  return files
    .map(({ id, cards }) => {
      const before = known.get(id);
      return before
        ? { id, versionCode: before.versionCode, versionName: before.versionName ?? '', cards }
        : { id, versionCode, versionName, cards };
    })
    .sort((a, b) => a.versionCode - b.versionCode || a.id.localeCompare(b.id));
}
