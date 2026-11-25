// Carnet de recettes, récit 114 (décisions de l'admin du 25/11, lot A1).
//
// - Each recipe has one of 8 categories. Those written before have none: they go to « divers »,
//   once, a little after the rules are published (migrations/recipeCategories marks it).
// - A recipe belongs to its author only. The « personne d'origine » (originMemberId) goes: its
//   first name is copied into the free text « D'où vient la recette ? » (origin), and the id is
//   set to null. Done for the old recipes in the same run, then for each recipe written with an
//   originMemberId (an older app still offers to pick someone), by onRecipeWritten.

import type { DocumentReference, Firestore } from 'firebase-admin/firestore';

export const RECIPE_CATEGORIES = ['aperitif', 'entree', 'plat', 'accompagnement', 'dessert', 'boisson', 'sauce', 'divers'] as const;
export const DEFAULT_CATEGORY = 'divers';
export const RECIPE_MIGRATION = 'migrations/recipeCategories';

/** The rules bound origin to 80 characters. */
const MAX_ORIGIN = 80;

export interface RecipeChange {
  category?: string;
  origin?: string;
  originMemberId?: null;
}

/**
 * What an old recipe needs: a category when it has none (or an unknown one), and its origin
 * member turned into text. [names] gives the first name of each member still in the family; a
 * member who left keeps the origin text as it was. Null when nothing changes.
 */
export function recipeChange(data: Record<string, unknown>, names: Map<string, string>): RecipeChange | null {
  const change: RecipeChange = {};
  const category = data.category;
  if (typeof category !== 'string' || !(RECIPE_CATEGORIES as readonly string[]).includes(category)) {
    change.category = DEFAULT_CATEGORY;
  }
  const originId = data.originMemberId;
  if (typeof originId === 'string' && originId !== '') {
    change.originMemberId = null;
    const text = typeof data.origin === 'string' ? data.origin.trim() : '';
    const name = names.get(originId);
    if (text === '' && name) change.origin = name.slice(0, MAX_ORIGIN);
  }
  return Object.keys(change).length > 0 ? change : null;
}

async function memberNames(db: Firestore): Promise<Map<string, string>> {
  const members = await db.collection('members').get();
  const names = new Map<string, string>();
  for (const m of members.docs) {
    const name = m.get('firstName');
    if (typeof name === 'string' && name.trim() !== '') names.set(m.id, name.trim());
  }
  return names;
}

/**
 * Gives every old recipe a category and its origin as text, once. Returns how many recipes
 * changed, or null when already done.
 */
export async function migrateRecipesOnce(db: Firestore, now: Date): Promise<number | null> {
  const marker = db.doc(RECIPE_MIGRATION);
  if ((await marker.get()).exists) return null;
  const names = await memberNames(db);
  let count = 0;
  let writer = db.batch();
  let pending = 0;
  for (const recipe of (await db.collection('recipes').get()).docs) {
    const change = recipeChange(recipe.data(), names);
    if (!change) continue;
    writer.update(recipe.ref, { ...change });
    count++;
    pending++;
    if (pending === 400) {
      await writer.commit();
      writer = db.batch();
      pending = 0;
    }
  }
  if (pending > 0) await writer.commit();
  await marker.set({ count, at: now });
  console.log(`recipeCategories: ${count} recipes changed`);
  return count;
}

/**
 * A recipe just written with a person of origin (an older app): that person becomes the text of
 * its origin. Its category is left to the app (the migration and the reading give « divers »).
 * Returns true when the recipe changed.
 */
export async function dropOriginMember(db: Firestore, ref: DocumentReference, data: Record<string, unknown> | undefined): Promise<boolean> {
  const originId = data?.originMemberId;
  if (!data || typeof originId !== 'string' || originId === '') return false;
  const member = await db.collection('members').doc(originId).get();
  const name = member.exists ? String(member.get('firstName') ?? '').trim() : '';
  const names = name ? new Map([[originId, name]]) : new Map<string, string>();
  // Read again in the transaction: only if the recipe still names that person (a later edit wins).
  return db.runTransaction(async (tx) => {
    const now = await tx.get(ref);
    if (!now.exists || now.get('originMemberId') !== originId) return false;
    const change = recipeChange({ ...now.data(), category: DEFAULT_CATEGORY }, names);
    if (!change) return false;
    tx.update(ref, { ...change });
    return true;
  });
}
