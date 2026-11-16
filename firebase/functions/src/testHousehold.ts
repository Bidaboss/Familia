// Le foyer de test : households/{householdId}.isTest == true, written by the admin principal only.
// A member is « de test » when their foyer is. One source of truth: the flag is never copied on
// members/{id}. Other modules use these helpers to keep test profiles away from the real family.

import type { Firestore } from 'firebase-admin/firestore';

/** What is known of a member's foyer: test, real, or none (member or foyer missing). */
export type HouseholdKind = 'test' | 'real' | 'none';

export async function householdKindOf(db: Firestore, memberId: string): Promise<HouseholdKind> {
  const member = await db.doc(`members/${memberId}`).get();
  const householdId = member.exists ? (member.get('householdId') as string | null | undefined) : null;
  if (!householdId) return 'none';
  const household = await db.doc(`households/${householdId}`).get();
  if (!household.exists) return 'none';
  return household.get('isTest') === true ? 'test' : 'real';
}

/** True only for a member of a foyer marked isTest; a missing member or foyer counts as real. */
export async function isTestMember(db: Firestore, memberId: string): Promise<boolean> {
  return (await householdKindOf(db, memberId)) === 'test';
}
