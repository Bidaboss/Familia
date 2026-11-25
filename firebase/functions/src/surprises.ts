// Cadeaux et Surprise (brief design/cadeaux/brief-surprise-final.md, plan « Cadeaux et Surprise »):
// what the server keeps for a surprise. The beneficiary never reads any of it, admin included.
//
// surprises/{sid}/proposals/{pid}/ratings/{memberId}  { stars: 1 to 5 }, read only by its author.
//   → ratingSum and ratingCount on the proposal, so the average is shown without telling who gave what.
// surprises/{sid}/takes/{pid}/shares/{memberId}       { amount: whole euros }
//   → sharesTotal and sharesCount on the take (a joint gift).

import type { Firestore } from 'firebase-admin/firestore';

/** A star rating counts only from 1 to 5, whole stars: anything else is left out. */
export function validStars(value: unknown): number | null {
  const n = Number(value);
  return Number.isInteger(n) && n >= 1 && n <= 5 ? n : null;
}

/** A part counts only in whole euros above 0 (brief: « parts en euros entiers »). */
export function validAmount(value: unknown): number | null {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
}

/**
 * The sum and the number of the ratings of a proposal. Only the server reads every rating, so the
 * members see the average and never who rated what (the app shows it from 3 ratings).
 */
export async function updateRatingAverage(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  const proposal = db.collection('surprises').doc(surpriseId).collection('proposals').doc(proposalId);
  // In a transaction: two ratings in the same second must not write an older count over a newer one.
  await db.runTransaction(async (tx) => {
    const [snapshot, ratings] = await Promise.all([tx.get(proposal), tx.get(proposal.collection('ratings'))]);
    if (!snapshot.exists) return;
    const stars = ratings.docs.map((r) => validStars(r.get('stars'))).filter((n): n is number => n !== null);
    const sum = stars.reduce((total, n) => total + n, 0);
    if (snapshot.get('ratingSum') === sum && snapshot.get('ratingCount') === stars.length) return;
    tx.update(proposal, { ratingSum: sum, ratingCount: stars.length });
  });
}

/** The total and the number of the parts of a joint gift. Over the goal is allowed: the app only warns. */
export async function updateTakeTotal(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  const take = db.collection('surprises').doc(surpriseId).collection('takes').doc(proposalId);
  await db.runTransaction(async (tx) => {
    const [snapshot, shares] = await Promise.all([tx.get(take), tx.get(take.collection('shares'))]);
    if (!snapshot.exists) return;
    const amounts = shares.docs.map((s) => validAmount(s.get('amount'))).filter((n): n is number => n !== null);
    const total = amounts.reduce((sum, n) => sum + n, 0);
    if (snapshot.get('sharesTotal') === total && snapshot.get('sharesCount') === amounts.length) return;
    tx.update(take, { sharesTotal: total, sharesCount: amounts.length });
  });
}

/** A removed proposal takes its ratings with it: nobody could read them any more. */
export async function deleteProposalRatings(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  await db.recursiveDelete(db.collection('surprises').doc(surpriseId).collection('proposals').doc(proposalId).collection('ratings'));
}

/** A released gift (« Je le prends » undone, or the joint gift dropped) takes its parts with it. */
export async function deleteTakeShares(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  await db.recursiveDelete(db.collection('surprises').doc(surpriseId).collection('takes').doc(proposalId).collection('shares'));
}

/** A surprise removed by its creator leaves nothing behind (members, proposals, ratings, takes, parts). */
export async function deleteSurpriseContent(db: Firestore, surpriseId: string): Promise<void> {
  const surprise = db.collection('surprises').doc(surpriseId);
  const children = await surprise.listCollections();
  await Promise.all(children.map((c) => db.recursiveDelete(c)));
}
