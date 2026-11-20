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
 * The files to publish today: a file named by a date later than [today] (YYYY-MM-DD, Paris) waits
 * for a later publication, so each version gets only its own cards and its own number. A file
 * whose name is not a date is always published.
 */
export function dueFiles(files, today) {
  return files.filter(({ id }) => !/^\d{4}-\d{2}-\d{2}$/.test(id) || id <= today);
}

/** Today's date in Paris, YYYY-MM-DD. */
export function parisToday(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/**
 * The new config/app.whatsNew. A file already published keeps its version number, with its texts
 * brought up to date; a new file takes the version being published; a removed file leaves the
 * history. The corrections of the earlier versions stay (the last [CORRECTIONS_KEPT]), and
 * [corrections] (correctionsRelease) joins them. Oldest first.
 */
export function mergeWhatsNew(history, files, versionCode, versionName, corrections = null) {
  const past = Array.isArray(history) ? history : [];
  const known = new Map(past.map((entry) => [entry.id, entry]));
  const fromFiles = files.map(({ id, cards }) => {
    const before = known.get(id);
    return before
      ? { id, versionCode: before.versionCode, versionName: before.versionName ?? '', cards }
      : { id, versionCode, versionName, cards };
  });
  const keptFixes = past
    .filter((entry) => entry?.fixes === true && entry.id !== corrections?.id)
    .sort((a, b) => a.versionCode - b.versionCode)
    .slice(corrections ? 1 - CORRECTIONS_KEPT : -CORRECTIONS_KEPT);
  return [...fromFiles, ...keptFixes, ...(corrections ? [corrections] : [])]
    .sort((a, b) => a.versionCode - b.versionCode || a.id.localeCompare(b.id));
}

// --- Corrections (Kévin le 20/11) ---------------------------------------------------------------
// Each version also tells what was merged since the one before, from the titles of the pull
// requests (squash commits « Titre (#123) »), on a card of its own that the app draws apart.

export const CORRECTIONS_KEPT = 30;
export const CORRECTIONS_LINES = 6;
export const CORRECTIONS_TITLE = 'Ce qui a été corrigé ou retouché';

/**
 * Words that keep a title out of the card: read by the whole family, children included, it must
 * never give a surprise away (relecture de la #433).
 */
export const CORRECTIONS_HIDDEN = /surprise|cadeau|envie|r[ée]serv|anniversaire|no[ëe]l/i;

/**
 * The titles of the pull requests among [subjects] (git log), without their number nor references,
 * nor those that touch gifts ([CORRECTIONS_HIDDEN]).
 */
export function correctionTitles(subjects) {
  return subjects
    .map((subject) => subject.trim())
    .filter((subject) => /\(#\d+\)$/.test(subject))
    .map((subject) => subject
      .replace(/\s*\(#\d+\)$/, '')
      .replace(/\s*\([^()]*\b(?:PRODUIT|DESIGN|BUG|CADEAUX|AIDE|VOYAGE|ORGA|COORD|DEV|PRD|récit)\b[^()]*\)\s*$/i, '')
      .trim())
    .filter((title) => title && !CORRECTIONS_HIDDEN.test(title));
}

/** The version's card of corrections, or null when nothing was merged. */
export function correctionsRelease(titles, versionCode, versionName) {
  if (!titles.length) return null;
  const lines = titles.slice(0, CORRECTIONS_LINES).map((title) => `• ${title}`);
  const more = titles.length - CORRECTIONS_LINES;
  if (more === 1) lines.push('• et une autre retouche');
  if (more > 1) lines.push(`• et ${more} autres retouches`);
  const id = `corrections-${versionCode}`;
  return {
    id,
    versionCode,
    versionName,
    fixes: true,
    cards: [{ id, title: CORRECTIONS_TITLE, body: lines.join('\n'), kind: 'fixes' }],
  };
}
