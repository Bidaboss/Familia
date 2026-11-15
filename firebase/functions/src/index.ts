// Familia Cloud Functions (lot 8), in Europe (europe-west1). The logic is in logic.ts and
// store.ts; this file only wires it to Firebase.

import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { setGlobalOptions } from 'firebase-functions/v2';
import {
  onDocumentCreatedWithAuthContext, onDocumentDeleted, onDocumentDeletedWithAuthContext, onDocumentUpdatedWithAuthContext,
} from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { EventData, hasVisibleChange, Notice } from './logic';
import { deleteEventAnswers, deleteMemberData, notifyChange, Sender, sendDayBeforeReminders } from './store';
import { deleteTripContents, isNewArrival, notifyArrival, sendArrivalReminders, StepData } from './trips';

initializeApp();

// A family of 13: one instance is plenty, and caps the cost if something loops.
setGlobalOptions({ region: 'europe-west1', maxInstances: 1 });

const UNREGISTERED = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']);

const send: Sender = async (token: string, notice: Notice) => {
  try {
    await getMessaging().send({
      token,
      notification: { title: notice.title, body: notice.body },
      data: notice.tripId ? { tripId: notice.tripId } : { eventId: notice.eventId },
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
  await notifyChange(getFirestore(), send, 'edited', event.params.eventId, after, actorUid(event), new Date());
});

export const onEventDeleted = onDocumentDeletedWithAuthContext('events/{eventId}', async (event) => {
  const db = getFirestore();
  await deleteEventAnswers(db, event.params.eventId);
  const data = event.data?.data() as EventData | undefined;
  if (data) await notifyChange(db, send, 'deleted', event.params.eventId, data, actorUid(event), new Date());
});

export const onMemberDeleted = onDocumentDeleted('members/{memberId}', async (event) => {
  await deleteMemberData(getFirestore(), event.params.memberId);
});

export const dayBeforeReminder = onSchedule({ schedule: '0 18 * * *', timeZone: 'Europe/Paris' }, async () => {
  await sendDayBeforeReminders(getFirestore(), send, new Date());
});

// --- Trips (V3, récit 14) ---------------------------------------------------------------------

export const onTripStepUpdated = onDocumentUpdatedWithAuthContext('trips/{tripId}/steps/{stepId}', async (event) => {
  const before = event.data?.before.data() as StepData | undefined;
  const after = event.data?.after.data() as StepData | undefined;
  if (!before || !after || !isNewArrival(before, after)) return;
  await notifyArrival(getFirestore(), send, event.params.tripId, after, actorUid(event));
});

export const onTripDeleted = onDocumentDeleted('trips/{tripId}', async (event) => {
  await deleteTripContents(getFirestore(), event.params.tripId);
});

export const arrivalReminder = onSchedule({ schedule: 'every 30 minutes', timeZone: 'Europe/Paris' }, async () => {
  await sendArrivalReminders(getFirestore(), send, new Date());
});
