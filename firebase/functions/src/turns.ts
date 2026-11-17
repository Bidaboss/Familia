// À qui le tour ? (PRD, récit 58): the notices of a rotation between households. When the turn
// comes to a household, its adults hear it straight away; a proposed exchange goes to the adults
// of the other household, and a refusal back to the one who proposed. « On passe notre tour »
// tells the next household it is now theirs, and nobody else. No counter, no ranking.

import type { Firestore } from 'firebase-admin/firestore';
import type { Notice } from './logic';
import { deliver } from './prefs';
import type { Sender } from './store';

export interface TurnSwap {
  fromId: string;
  toId: string;
  byId: string;
  byName: string;
}

export interface TurnData {
  title: string;
  householdIds: string[];
  current: number;
  swap: TurnSwap | null;
  createdBy: string;
}

/** The household whose turn it is, or null for a rotation that is not readable. */
export function turnHousehold(t: TurnData | undefined): string | null {
  if (!t || !Array.isArray(t.householdIds) || t.householdIds.length === 0) return null;
  return t.householdIds[t.current] ?? null;
}

function notice(turnId: string, title: string, body: string): Notice {
  return { channel: 'helpRequests', title, body, turnId, direct: true, tag: `turn-${turnId}` };
}

/** « C'est ton tour », to the adults of the household whose turn it now is. */
export function yourTurnNotice(turnId: string, t: TurnData): Notice {
  return notice(turnId, 'C\'est ton tour', `« ${t.title} » : c'est à ton foyer. Touche pour créer l'événement.`);
}

/** An exchange proposed, to the adults of the other household. */
export function swapAskNotice(turnId: string, t: TurnData, swap: TurnSwap): Notice {
  return notice(turnId, 'Un échange de tour', `${swap.byName} propose d'échanger ta place pour « ${t.title} ».`);
}

/** The other household said no, to the one who proposed. */
export function swapRefusedNotice(turnId: string, t: TurnData): Notice {
  return notice(turnId, 'Pas d\'échange cette fois', `L'échange pour « ${t.title} » n'a pas été accepté.`);
}

/** The adults of [householdId], without [except]. */
async function adultsOf(db: Firestore, householdId: string, except: string | null): Promise<string[]> {
  const found = await db.collection('members').where('householdId', '==', householdId).get();
  return found.docs.filter((d) => d.get('profile') === 'adult' && d.id !== except).map((d) => d.id);
}

/** The member of [uid], the one who made the change in the app, with their household. */
async function memberOfUid(db: Firestore, uid: string | undefined): Promise<{ id: string; householdId: unknown } | null> {
  if (!uid) return null;
  const found = await db.collection('members').where('uid', '==', uid).limit(1).get();
  return found.empty ? null : { id: found.docs[0].id, householdId: found.docs[0].get('householdId') };
}

/**
 * What a change of a rotation tells. A new household whose turn it is (at the creation, after
 * « C'est fait », a pass, an exchange or an edit): its adults, except the one who made the change.
 * A new proposal: the adults of the other household. A proposal dropped without an exchange by an
 * adult of the other household: the one who proposed. Withdrawn by their own household, or dropped
 * by an edit of the author or the admin principal, it tells nobody: nobody said no. [only] keeps who may hear of the module (featureAudience):
 * switched off, the admin principal alone. Returns the number of notices sent.
 */
export async function onTurnWritten(
  db: Firestore, send: Sender, turnId: string, before: TurnData | undefined, after: TurnData | undefined,
  actorUid: string | undefined, now: Date, only: (ids: string[]) => string[] = (ids) => ids,
): Promise<number> {
  if (!after) return 0;
  const actorMember = await memberOfUid(db, actorUid);
  const actor = actorMember?.id ?? null;
  let sent = 0;
  const nowHousehold = turnHousehold(after);
  if (nowHousehold && nowHousehold !== turnHousehold(before)) {
    const ids = only(await adultsOf(db, nowHousehold, actor));
    if (ids.length > 0) sent += await deliver(db, send, ids, yourTurnNotice(turnId, after), now);
  }
  const asked = after.swap;
  if (asked && !before?.swap) {
    const ids = only(await adultsOf(db, asked.toId, actor));
    if (ids.length > 0) sent += await deliver(db, send, ids, swapAskNotice(turnId, after, asked), now);
  }
  const dropped = before?.swap;
  if (dropped && !after.swap) {
    const exchanged = before.householdIds.indexOf(dropped.fromId) !== after.householdIds.indexOf(dropped.fromId);
    const refused = actorMember !== null && actorMember.householdId === dropped.toId;
    const ids = only([dropped.byId]);
    if (!exchanged && refused && ids.length > 0) {
      sent += await deliver(db, send, ids, swapRefusedNotice(turnId, after), now);
    }
  }
  return sent;
}
