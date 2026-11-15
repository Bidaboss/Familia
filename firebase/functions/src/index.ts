// Familia Cloud Functions (lot 8), in Europe (europe-west1). The logic is in logic.ts and
// store.ts; this file only wires it to Firebase.

import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { setGlobalOptions } from 'firebase-functions/v2';
import {
  onDocumentCreatedWithAuthContext, onDocumentDeleted, onDocumentDeletedWithAuthContext, onDocumentUpdatedWithAuthContext,
  onDocumentWritten, onDocumentWrittenWithAuthContext,
} from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import {
  deleteClaimShares, deleteGiftListContent, deleteMemberGiftLists, GiftItemData, onGiftItemChanged, revealGiftLists,
  sendBirthdayGiftReminders,
} from './gifts';
import {
  HelpNotice, HelpRequestData, HelpSender, onHelpRequestWritten, reopenRequestsOf, sendStillOpenReminders,
} from './helpRequests';
import { EventData, hasVisibleChange, Notice } from './logic';
import { comingMembers, deleteEventAnswers, loadMembers, deleteMemberData, notifyChange, Sender, sendDayBeforeReminders } from './store';

initializeApp();

// A family of 13: one instance is plenty, and caps the cost if something loops.
setGlobalOptions({ region: 'europe-west1', maxInstances: 1 });

const UNREGISTERED = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']);

const send: Sender = async (token: string, notice: Notice) => {
  try {
    await getMessaging().send({
      token,
      notification: { title: notice.title, body: notice.body },
      data: {
        ...(notice.eventId ? { eventId: notice.eventId } : {}),
        ...(notice.listId ? { listId: notice.listId } : {}),
      },
      android: { notification: { channelId: notice.channel } },
    });
    return 'ok';
  } catch (e) {
    const code = (e as { code?: string }).code ?? '';
    return UNREGISTERED.has(code) ? 'unregistered' : 'error';
  }
};

/** The UID of the member who made the change, when it came from the app. */
function actorUid(event: { authType: string; authId?: string }): string | undefined {
  return event.authType === 'app_user' ? event.authId : undefined;
}

export const onEventCreated = onDocumentCreatedWithAuthContext('events/{eventId}', async (event) => {
  const data = event.data?.data() as EventData | undefined;
  if (!data) return;
  await notifyChange(getFirestore(), send, 'added', event.params.eventId, data, actorUid(event), new Date());
});

export const onEventUpdated = onDocumentUpdatedWithAuthContext('events/{eventId}', async (event) => {
  const before = event.data?.before.data() as EventData | undefined;
  const after = event.data?.after.data() as EventData | undefined;
  if (!before || !after || !hasVisibleChange(before, after)) return;
  const db = getFirestore();
  const members = await loadMembers(db);
  const answered = await comingMembers(db, event.params.eventId, members);
  await notifyChange(db, send, 'edited', event.params.eventId, after, actorUid(event), new Date(), answered, members);
});

export const onEventDeleted = onDocumentDeletedWithAuthContext('events/{eventId}', async (event) => {
  const db = getFirestore();
  const data = event.data?.data() as EventData | undefined;
  // The answers are read first: those who were coming are told, then the answers go.
  try {
    const members = await loadMembers(db);
    const answered = await comingMembers(db, event.params.eventId, members);
    if (data) await notifyChange(db, send, 'deleted', event.params.eventId, data, actorUid(event), new Date(), answered, members);
  } finally {
    await deleteEventAnswers(db, event.params.eventId);
  }
});

export const onMemberDeleted = onDocumentDeleted('members/{memberId}', async (event) => {
  await deleteMemberData(getFirestore(), event.params.memberId);
});

export const dayBeforeReminder = onSchedule({ schedule: '0 18 * * *', timeZone: 'Europe/Paris' }, async () => {
  await sendDayBeforeReminders(getFirestore(), send, new Date());
});

// --- Coups de main (PRD, récits 13 à 18) -----------------------------------------------------------

// A tap on the notification opens the request (helpRequestId), not an event.
const sendHelp: HelpSender = async (token: string, notice: HelpNotice) => {
  try {
    await getMessaging().send({
      token,
      notification: { title: notice.title, body: notice.body },
      data: { helpRequestId: notice.helpRequestId },
      android: { notification: { channelId: notice.channel } },
    });
    return 'ok';
  } catch (e) {
    const code = (e as { code?: string }).code ?? '';
    return UNREGISTERED.has(code) ? 'unregistered' : 'error';
  }
};

export const onHelpRequestWrite = onDocumentWrittenWithAuthContext('helpRequests/{requestId}', async (event) => {
  const before = event.data?.before.data() as HelpRequestData | undefined;
  const after = event.data?.after.data() as HelpRequestData | undefined;
  await onHelpRequestWritten(getFirestore(), sendHelp, event.params.requestId, before, after, actorUid(event), new Date());
});

// Récit 18: at 18 h, the requests of tomorrow still without a volunteer.
export const helpRequestStillOpen = onSchedule({ schedule: '0 18 * * *', timeZone: 'Europe/Paris' }, async () => {
  await sendStillOpenReminders(getFirestore(), sendHelp, new Date());
});

// Spec, cas limites: a volunteer removed from the family frees their upcoming requests.
export const onMemberDeletedHelp = onDocumentDeleted('members/{memberId}', async (event) => {
  await reopenRequestsOf(getFirestore(), event.params.memberId, new Date());
});

// --- Listes et cadeaux (V2) --------------------------------------------------------------------

export const onGiftListDeleted = onDocumentDeleted('giftLists/{listId}', async (event) => {
  await deleteGiftListContent(getFirestore(), event.params.listId);
});

export const onGiftClaimDeleted = onDocumentDeleted('giftLists/{listId}/claims/{itemId}', async (event) => {
  await deleteClaimShares(getFirestore(), event.params.listId, event.params.itemId);
});

export const onGiftItemWritten = onDocumentWritten('giftLists/{listId}/items/{itemId}', async (event) => {
  const before = event.data?.before.data() as GiftItemData | undefined;
  const after = event.data?.after.data() as GiftItemData | undefined;
  await onGiftItemChanged(getFirestore(), send, event.params.listId, event.params.itemId, before, after);
});

export const onMemberDeletedGifts = onDocumentDeleted('members/{memberId}', async (event) => {
  await deleteMemberGiftLists(getFirestore(), event.params.memberId);
});

// The morning after an occasion, its lists are revealed (PRD, récit 10 bis).
export const revealGifts = onSchedule({ schedule: '0 7 * * *', timeZone: 'Europe/Paris' }, async () => {
  await revealGiftLists(getFirestore(), send, new Date());
});

// D-21 and D-14 before a birthday (PRD, récit 10).
export const birthdayGiftReminders = onSchedule({ schedule: '0 10 * * *', timeZone: 'Europe/Paris' }, async () => {
  await sendBirthdayGiftReminders(getFirestore(), send, new Date());
});
