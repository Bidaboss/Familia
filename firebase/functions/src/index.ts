// Familia Cloud Functions (lot 8), in Europe (europe-west1). The logic is in logic.ts and
// store.ts; this file only wires it to Firebase.

import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { getStorage } from 'firebase-admin/storage';
import { setGlobalOptions } from 'firebase-functions/v2';
import {
  onDocumentCreated, onDocumentCreatedWithAuthContext, onDocumentDeleted, onDocumentDeletedWithAuthContext, onDocumentUpdated,
  onDocumentUpdatedWithAuthContext, onDocumentWritten, onDocumentWrittenWithAuthContext,
} from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import {
  deleteClaimShares, deleteGiftListContent, deleteMemberGiftLists, GiftItemData, onGiftItemChanged, revealGiftLists,
  sendBirthdayGiftReminders, updateSharesTotal,
} from './gifts';
import { countLeftovers, deleteLeftovers, openDueCapsules } from './capsules';
import { CommentData, deleteCommentMutesOf, deleteEventComments, onCommentCreated } from './comments';
import { deleteLinksOf, removeMemberFromTree } from './genealogy';
import { checkConsumptionIfDue } from './consumption';
import { FeedbackData, onFeedbackChanged, onFeedbackCreated, screenshotToDelete } from './feedback';
import { deleteJoinMeOf, JoinMeData, onJoinMeCreated } from './joinMe';
import { closeDuePolls, deletePollVotes, deleteVotesOf, onPollClosed, onPollCreated, PollData } from './polls';
import { cloudMonitoring } from './monitoring';
import { deleteMemberNotebook, syncNotebookEntry, syncNotebookItem } from './notebook';
import {
  deleteReactionDigest, FileRemover, GoodNewsData, GoodNewsNotice, GoodNewsSender, onGoodNewsEdited, onGoodNewsPublished,
  newReactorIds, onGoodNewsReacted, onGoodNewsRemoved, sendReactionDigests,
} from './goodNews';
import {
  HelpNotice, HelpRequestData, HelpSender, onHelpRequestWritten, reopenRequestsOf, sendStillOpenReminders,
} from './helpRequests';
import { addDays, EventData, hasVisibleChange, Notice, parisDay } from './logic';
import { comingMembers, deleteEventAnswers, loadMembers, deleteMemberData, memberForUid, notifyChange, releaseBringOf, Sender, sendDayBeforeReminders } from './store';
import { deleteExpenseBook, ExpenseData, historyEntry, sendExpenseNotices, writeExpenseHistory } from './expenses';
import { deleteTripContents, isNewArrival, notifyArrival, sendArrivalReminders, StepData } from './trips';
import { countAnswers, deleteAnswersOf, publishWeeklyQuestion, removeOldAnswerPhoto } from './weeklyQuestion';
import {
  countGesture, countHelpGiven, deleteSouvenirsOf, giveSouvenir, giveYearSouvenirs, isNewWeeklyAnswer, souvenirMark,
} from './souvenirs';
import { queueAlbumInvitations } from './albumInvites';
import { deleteDigestOf, sendDigests } from './digest';
import { applyHostedChallenge, remindHost } from './hostChallenge';
import { sendWeeklyRecap } from './recap';
import { deleteQuietQueueOf, sendHeldNotices } from './prefs';
import { deleteBirthdayCardsOf, onBirthdayCardCreated, startBirthdays, type BirthdayCard } from './birthdayWishes';
import { isFirstSignIn, welcomeMember } from './welcome';
import { revealWishes, thankGivers } from './wishesReveal';
import { addLeaf, applyChallengePick, celebrateChallenge, countForChallenge, deleteTreeDataOf, ensureChallenge, parisMonth, treeMark } from './tree';

initializeApp();

// L'arbre (récit 35) never stops what a trigger was doing: a failure is logged and the leaf lost.
async function grow(what: string, work: () => Promise<unknown>): Promise<void> {
  try {
    await work();
  } catch (e) {
    console.error(`tree: ${what}`, e);
  }
}

// A family of 13: one instance is plenty, and caps the cost if something loops.
setGlobalOptions({ region: 'europe-west1', maxInstances: 1 });

const UNREGISTERED = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']);

/** Channels whose notifications may hold a surprise (gifts, time capsules). Same list in NotificationChannels.kt. */
const PRIVATE_CHANNELS: ReadonlySet<string> = new Set(['gifts', 'capsules']);

const send: Sender = async (token: string, notice: Notice) => {
  try {
    await getMessaging().send({
      token,
      notification: { title: notice.title, body: notice.body },
      data: {
        ...(notice.eventId ? { eventId: notice.eventId } : {}),
        ...(notice.listId ? { listId: notice.listId } : {}),
        ...(notice.tripId ? { tripId: notice.tripId } : {}),
        ...(notice.weeklyQuestionId ? { weeklyQuestionId: notice.weeklyQuestionId } : {}),
        ...(notice.pollId ? { pollId: notice.pollId } : {}),
        ...(notice.joinMeId ? { joinMeId: notice.joinMeId } : {}),
        ...(notice.capsuleId ? { capsuleId: notice.capsuleId } : {}),
        ...(notice.feedback ? { feedback: notice.feedback } : {}),
        ...(notice.souvenirKey ? { souvenirKey: notice.souvenirKey } : {}),
        ...(notice.recapWeek ? { recapWeek: notice.recapWeek } : {}),
        ...(notice.birthdayOf ? { birthdayOf: notice.birthdayOf } : {}),
        ...(notice.welcomeOf ? { welcomeOf: notice.welcomeOf } : {}),
        ...(notice.tag ? { notificationTag: notice.tag } : {}),
      },
      // A surprise never shows on the lock screen (audit 2.2): only « Familia » and the channel.
      android: { notification: { channelId: notice.channel, ...(notice.tag ? { tag: notice.tag } : {}), ...(PRIVATE_CHANNELS.has(notice.channel) ? { visibility: 'private' as const } : {}) } },
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
    // Each one is tried even if another fails: answers, the album (récit 41), records and files, comments (récit 28).
    const results = await Promise.allSettled([
      deleteEventAnswers(db, event.params.eventId),
      db.recursiveDelete(db.collection('events').doc(event.params.eventId).collection('photos')),
      deleteEventComments(db, event.params.eventId),
      getStorage().bucket().deleteFiles({ prefix: `events/${event.params.eventId}/` }),
    ]);
    const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
    if (failed) throw failed.reason;
  }
});

// L'arbre (récit 35): « Je viens » adds a leaf for the member who comes, once per event.
export const onRsvpWritten = onDocumentWritten('events/{eventId}/rsvps/{memberId}', async (event) => {
  const { eventId, memberId } = event.params;
  if (event.data?.after.get('status') !== 'yes' || event.data?.before.get('status') === 'yes') return;
  await grow('rsvp', () => addLeaf(getFirestore(), memberId, treeMark.rsvp(eventId, memberId), new Date()));
});

// Album d'un événement (récit 41): each photo adds a leaf for its author, once per slot, so taking
// a photo off and putting it back counts once.
export const onEventPhotoCreated = onDocumentCreated('events/{eventId}/photos/{photoId}', async (event) => {
  const authorId = event.data?.get('authorId');
  if (typeof authorId !== 'string' || authorId === '') return;
  await grow('albumPhoto', () => addLeaf(getFirestore(), authorId, treeMark.albumPhoto(event.params.eventId, event.params.photoId), new Date()));
});

export const onMemberDeleted = onDocumentDeleted('members/{memberId}', async (event) => {
  await deleteMemberData(getFirestore(), event.params.memberId);
  // « Qui apporte quoi » (récit 4 bis): what they had taken is free again.
  await releaseBringOf(getFirestore(), event.params.memberId);
  await deleteCommentMutesOf(getFirestore(), event.params.memberId);
  await deleteBirthdayCardsOf(getFirestore(), event.params.memberId);
});

// Commentaires d'un événement (récit 28): those who follow the event are told.
export const onEventCommentCreated = onDocumentCreated('events/{eventId}/comments/{commentId}', async (event) => {
  const data = event.data?.data() as CommentData | undefined;
  if (data) await onCommentCreated(getFirestore(), send, event.params.eventId, event.params.commentId, data);
});

// « Une idée ? Un souci ? » (récit 6 sexies): the admin principal is told of each new one.
export const onFeedbackSent = onDocumentCreated('feedback/{feedbackId}', async (event) => {
  const data = event.data?.data() as FeedbackData | undefined;
  if (data) await onFeedbackCreated(getFirestore(), send, data);
});

// … and its author of each new state given by the admin principal.
export const onFeedbackAnswered = onDocumentUpdated('feedback/{feedbackId}', async (event) => {
  const before = event.data?.before.data() as FeedbackData | undefined;
  const after = event.data?.after.data() as FeedbackData | undefined;
  if (before && after) await onFeedbackChanged(getFirestore(), send, before, after);
});

// A removed request takes its screenshot with it, even when the app could not delete it (offline,
// or the request deleted from the console). Only the one file this request may name.
export const onFeedbackRemoved = onDocumentDeleted('feedback/{feedbackId}', async (event) => {
  const path = screenshotToDelete(event.params.feedbackId, event.data?.data() as FeedbackData | undefined);
  if (path) await getStorage().bucket().file(path).delete({ ignoreNotFound: true });
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
  if (after?.status === 'taken' && before?.status !== 'taken' && after.volunteerId) {
    const volunteerId = after.volunteerId;
    await grow('help', () => addLeaf(getFirestore(), volunteerId, treeMark.help(event.params.requestId), new Date()));
  }
});

// Récit 18: at 18 h, the requests of tomorrow still without a volunteer.
// Also the souvenirs of the day (récit 34): yesterday's coups de main rendus, and a year in Familia.
export const helpRequestStillOpen = onSchedule({ schedule: '0 18 * * *', timeZone: 'Europe/Paris' }, async () => {
  const now = new Date();
  await sendStillOpenReminders(getFirestore(), sendHelp, now);
  await countHelpGiven(getFirestore(), send, addDays(parisDay(now), -1), now);
  await giveYearSouvenirs(getFirestore(), send, now);
  await grow('challenge', () => ensureChallenge(getFirestore(), parisMonth(now), now));
  // And, 7 days before a month, the foyer whose turn it is to launch its challenge (récit 35 bis).
  await grow('hostReminder', () => remindHost(getFirestore(), send, now));
  // On Sunday, « La semaine de la famille » (récit 36).
  try {
    await sendWeeklyRecap(getFirestore(), send, now);
  } catch (e) {
    console.error('weekly recap', e);
  }
});

// Spec, cas limites: a volunteer removed from the family frees their upcoming requests.
export const onMemberDeletedHelp = onDocumentDeleted('members/{memberId}', async (event) => {
  await reopenRequestsOf(getFirestore(), event.params.memberId, new Date());
});

// --- Listes et cadeaux (V2) --------------------------------------------------------------------

export const onGiftListDeleted = onDocumentDeleted('giftLists/{listId}', async (event) => {
  await deleteGiftListContent(getFirestore(), event.params.listId);
});

// A reservation is taken up in the private notebook of who made it (récit 9 bis).
export const onGiftClaimCreated = onDocumentCreated('giftLists/{listId}/claims/{itemId}', async (event) => {
  const holderId = String(event.data?.get('holderId') ?? '');
  await syncNotebookEntry(getFirestore(), event.params.listId, event.params.itemId, holderId);
});

// The holder chose another occasion for a wish (rév. 186): every notebook of the gift follows.
export const onGiftClaimUpdated = onDocumentUpdated('giftLists/{listId}/claims/{itemId}', async (event) => {
  if (event.data?.before.get('occasion') === event.data?.after.get('occasion')) return;
  await syncNotebookItem(getFirestore(), event.params.listId, event.params.itemId);
});

export const onGiftClaimDeleted = onDocumentDeleted('giftLists/{listId}/claims/{itemId}', async (event) => {
  const db = getFirestore();
  await deleteClaimShares(db, event.params.listId, event.params.itemId);
  await syncNotebookEntry(db, event.params.listId, event.params.itemId, String(event.data?.get('holderId') ?? ''));
});

export const onGiftShareWritten = onDocumentWritten('giftLists/{listId}/claims/{itemId}/shares/{memberId}', async (event) => {
  const db = getFirestore();
  await updateSharesTotal(db, event.params.listId, event.params.itemId);
  await syncNotebookEntry(db, event.params.listId, event.params.itemId, event.params.memberId);
});

export const onGiftItemWritten = onDocumentWritten('giftLists/{listId}/items/{itemId}', async (event) => {
  const before = event.data?.before.data() as GiftItemData | undefined;
  const after = event.data?.after.data() as GiftItemData | undefined;
  await onGiftItemChanged(getFirestore(), send, event.params.listId, event.params.itemId, before, after);
  if (before && after) await syncNotebookItem(getFirestore(), event.params.listId, event.params.itemId);
});

export const onMemberDeletedGifts = onDocumentDeleted('members/{memberId}', async (event) => {
  await deleteMemberGiftLists(getFirestore(), event.params.memberId);
  await deleteMemberNotebook(getFirestore(), event.params.memberId);
});

// The morning after an occasion, its lists are revealed, then each taken wish whose occasion
// was yesterday or before (PRD, récit 10 bis, rév. 186).
export const revealGifts = onSchedule({ schedule: '0 7 * * *', timeZone: 'Europe/Paris' }, async () => {
  const now = new Date();
  // Each on its own: a failure of the lists never holds back the wishes, nor the reverse.
  for (const [what, reveal] of [['lists', revealGiftLists], ['wishes', revealWishes]] as const) {
    try {
      await reveal(getFirestore(), send, now);
    } catch (e) {
      console.error(`revealGifts: ${what}`, e);
    }
  }
});

// « Merci » on « On m'a offert »: its givers are told, once (PRD rév. 189).
export const onReceivedGiftUpdated = onDocumentUpdated('giftLists/{listId}/received/{itemId}', async (event) => {
  await thankGivers(getFirestore(), send, event.params.listId, event.data?.before.data(), event.data?.after.data());
});

// D-21 and D-14 before a birthday (PRD, récit 10).
export const birthdayGiftReminders = onSchedule({ schedule: '0 10 * * *', timeZone: 'Europe/Paris' }, async () => {
  const now = new Date();
  // Each on its own: a failure of the reminders never holds back the wishes, nor the reverse.
  try { await sendBirthdayGiftReminders(getFirestore(), send, now); } catch (e) { console.error('birthday gift reminders', e); }
  // On the day, « C'est l'anniversaire de … » and its Bonne nouvelle (récit 36).
  try { await startBirthdays(getFirestore(), send, now); } catch (e) { console.error('birthday wishes', e); }
});

// Each card of vœux tells the person celebrated (récit 36).
export const onBirthdayCardWritten = onDocumentCreated('birthdayCards/{cardId}', async (event) => {
  const card = event.data?.data() as BirthdayCard | undefined;
  if (card) await onBirthdayCardCreated(getFirestore(), send, card);
});

// La bienvenue (récit 43): the first sign-in of a member links their Google account (uid).
export const onMemberSignedIn = onDocumentUpdated('members/{memberId}', async (event) => {
  if (!isFirstSignIn(event.data?.before.data(), event.data?.after.data())) return;
  await welcomeMember(getFirestore(), send, event.params.memberId, new Date());
});

// --- Bonnes nouvelles (PRD, récit 32) ---------------------------------------------------------------

// A tap on the notification opens the good news (goodNewsId).
const sendGoodNews: GoodNewsSender = async (token: string, notice: GoodNewsNotice) => {
  try {
    await getMessaging().send({
      token,
      notification: { title: notice.title, body: notice.body },
      data: { goodNewsId: notice.goodNewsId },
      android: { notification: { channelId: notice.channel } },
    });
    return 'ok';
  } catch (e) {
    const code = (e as { code?: string }).code ?? '';
    return UNREGISTERED.has(code) ? 'unregistered' : 'error';
  }
};

// The default bucket of the project (familia-93286.firebasestorage.app, US-EAST1): no region to set.
const removeFiles: FileRemover = async (paths: string[]) => {
  const bucket = getStorage().bucket();
  await Promise.all(paths.map((path) => bucket.file(path).delete({ ignoreNotFound: true })));
};

export const onGoodNewsCreated = onDocumentCreated('goodNews/{postId}', async (event) => {
  const data = event.data?.data() as (GoodNewsData & { challengeMonth?: string; forChallenge?: string; birthdayOf?: string }) | undefined;
  if (!data || data.challengeMonth || data.birthdayOf) return; // Published by Familia: already told.
  await onGoodNewsPublished(getFirestore(), sendGoodNews, event.params.postId, data);
  await giveSouvenir(getFirestore(), send, data.authorId, 'firstGoodNews', new Date());
  const { postId } = event.params;
  await grow('goodNews', async () => {
    const now = new Date();
    await addLeaf(getFirestore(), data.authorId, treeMark.goodNews(postId), now);
    const achieved = await countForChallenge(getFirestore(), postId, data.photoIds?.length ?? 0, now, data.forChallenge ?? null);
    if (achieved) await celebrateChallenge(getFirestore(), send, achieved, now);
  });
});

/** A foyer published the challenge of its month (récit 35 bis). */
export const onHouseholdChallengeWritten = onDocumentWritten('householdChallenges/{month}', async (event) => {
  if (event.data?.after.get('status') !== 'published' || event.data?.before.get('status') === 'published') return;
  await grow('hostedChallenge', () => applyHostedChallenge(getFirestore(), event.params.month, new Date()));
});

/** The admin principal picked the challenge of a month in Administration (récit 35). */
export const onChallengePicked = onDocumentWritten('challengePicks/{month}', async (event) => {
  if (!event.data?.after.exists) return;
  await grow('challengePick', () => applyChallengePick(getFirestore(), event.params.month, new Date()));
});

export const onGoodNewsUpdated = onDocumentUpdated('goodNews/{postId}', async (event) => {
  const before = event.data?.before.data() as GoodNewsData | undefined;
  const after = event.data?.after.data() as GoodNewsData | undefined;
  if (!before || !after) return;
  await onGoodNewsEdited(removeFiles, event.params.postId, before, after);
  await onGoodNewsReacted(getFirestore(), event.params.postId, before, after, new Date());
  for (const reactor of newReactorIds(before, after)) {
    await countGesture(getFirestore(), send, reactor, 'reactions', souvenirMark.reaction(event.params.postId, reactor), new Date());
    await grow('reaction', () => addLeaf(getFirestore(), reactor, treeMark.reaction(event.params.postId, reactor), new Date()));
  }
});

export const onGoodNewsDeleted = onDocumentDeleted('goodNews/{postId}', async (event) => {
  const data = event.data?.data() as GoodNewsData | undefined;
  if (data) await onGoodNewsRemoved(removeFiles, event.params.postId, data);
  await deleteReactionDigest(getFirestore(), event.params.postId);
});

// Grouped reactions (PRD rév. 174): every 15 minutes, the notices due an hour after a first reaction.
export const goodNewsReactionDigests = onSchedule({ schedule: '*/15 * * * *', timeZone: 'Europe/Paris' }, async () => {
  await sendReactionDigests(getFirestore(), sendGoodNews, new Date());
});

// --- Trips (V3, récit 14) ---------------------------------------------------------------------

export const onTripStepUpdated = onDocumentUpdatedWithAuthContext('trips/{tripId}/steps/{stepId}', async (event) => {
  const before = event.data?.before.data() as StepData | undefined;
  const after = event.data?.after.data() as StepData | undefined;
  if (!before || !after || !isNewArrival(before, after)) return;
  await notifyArrival(getFirestore(), send, event.params.tripId, after, actorUid(event));
  if (after.arrivedBy) {
    const by = after.arrivedBy;
    await giveSouvenir(getFirestore(), send, by, 'firstArrival', new Date());
    await grow('arrival', () => addLeaf(getFirestore(), by, treeMark.arrival(event.params.tripId, event.params.stepId), new Date()));
  }
});

export const onTripDeleted = onDocumentDeleted('trips/{tripId}', async (event) => {
  await deleteTripContents(getFirestore(), event.params.tripId);
  // The photos of the trip and their thumbnails (récit 15), all under trips/{tripId}/.
  await getStorage().bucket().deleteFiles({ prefix: `trips/${event.params.tripId}/` });
});

// Les comptes du voyage (récit 16 bis) live out of the trip: they go with it.
export const onTripDeletedAccounts = onDocumentDeleted('trips/{tripId}', async (event) => {
  await deleteExpenseBook(getFirestore(), 'trip', event.params.tripId);
});

// Les comptes d'un événement (PRODUIT-57) go with it too.
export const onEventDeletedAccounts = onDocumentDeleted('events/{eventId}', async (event) => {
  await deleteExpenseBook(getFirestore(), 'event', event.params.eventId);
});

// Each change of an expense goes in its history, with who made it (récit 16 bis).
export const onExpenseWritten = onDocumentWrittenWithAuthContext('expenseBooks/{bookId}/expenses/{expenseId}', async (event) => {
  const before = event.data?.before.data() as ExpenseData | undefined;
  const after = event.data?.after.data() as ExpenseData | undefined;
  const db = getFirestore();
  const uid = actorUid(event);
  const actor = uid ? memberForUid(await loadMembers(db), uid) : null;
  const entry = historyEntry(before, after, actor);
  if (entry) await writeExpenseHistory(db, event.params.bookId, event.params.expenseId, entry);
});

// Capsule temporelle (récit 39): a removed capsule takes its photos with it.
export const onCapsuleDeleted = onDocumentDeleted('capsules/{capsuleId}', async (event) => {
  const authorId = event.data?.get('authorId');
  if (typeof authorId !== 'string' || authorId === '') return;
  await getStorage().bucket().deleteFiles({ prefix: `capsules/${authorId}/${event.params.capsuleId}/` });
});

// An author who leaves: the admin principal sees how many sealed capsules are left (PRD rév. 184).
export const onMemberDeletedCapsules = onDocumentDeleted('members/{memberId}', async (event) => {
  const firstName = String(event.data?.get('firstName') ?? '');
  await countLeftovers(getFirestore(), event.params.memberId, firstName, new Date());
});

// The admin principal confirmed: those capsules are deleted unread.
export const onCapsuleLeftoversUpdated = onDocumentUpdated('capsuleLeftovers/{memberId}', async (event) => {
  if (event.data?.after.get('deleteAsked') !== true || event.data?.before.get('deleteAsked') === true) return;
  await deleteLeftovers(getFirestore(), event.params.memberId);
});

// La fiche membre (récit 7 bis) leaves with its member.
export const onMemberDeletedCard = onDocumentDeleted('members/{memberId}', async (event) => {
  await getFirestore().collection('memberCards').doc(event.params.memberId).delete();
});

// Arbre généalogique (récit 40): someone who leaves the tree takes their links with them.
export const onMemberDeletedKin = onDocumentDeleted('members/{memberId}', async (event) => {
  await removeMemberFromTree(getFirestore(), event.params.memberId);
});

export const onKinDeleted = onDocumentDeleted('kin/{kinId}', async (event) => {
  await deleteLinksOf(getFirestore(), event.params.kinId);
});

// Carnet de recettes (récit 38): a removed recipe takes its photos and comments with it, records
// and files. Each part is tried even if another fails, then the first failure is raised.
export const onRecipeDeleted = onDocumentDeleted('recipes/{recipeId}', async (event) => {
  const recipe = getFirestore().collection('recipes').doc(event.params.recipeId);
  const results = await Promise.allSettled([
    getFirestore().recursiveDelete(recipe.collection('photos')),
    getFirestore().recursiveDelete(recipe.collection('comments')),
    getStorage().bucket().deleteFiles({ prefix: `recipes/${event.params.recipeId}/` }),
  ]);
  const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
  if (failed) throw failed.reason;
});

/** A notice held during the quiet hours goes out with the sender it was made for. */
const sendHeld = async (token: string, notice: Notice & { goodNewsId?: string; helpRequestId?: string }) => {
  if (notice.goodNewsId && (notice.channel === 'goodNews' || notice.channel === 'comments')) {
    return sendGoodNews(token, { channel: notice.channel, title: notice.title, body: notice.body, goodNewsId: notice.goodNewsId });
  }
  if (notice.helpRequestId) return sendHelp(token, { channel: notice.channel, title: notice.title, body: notice.body, helpRequestId: notice.helpRequestId });
  return send(token, notice);
};

export const arrivalReminder = onSchedule({ schedule: 'every 30 minutes', timeZone: 'Europe/Paris' }, async () => {
  // First what was held during the quiet hours (récit 6 quinquies), from 8 h. A failure waits
  // for the next run and never stops the rest.
  try {
    await sendHeldNotices(getFirestore(), sendHeld, new Date());
  } catch (e) {
    console.error('quietQueue', e);
  }
  await sendArrivalReminders(getFirestore(), send, new Date());
  // Also the question of the week, on Monday from 9 h (récit 37): one task less to pay for.
  await publishWeeklyQuestion(getFirestore(), send, new Date());
  // And the consumption of the day before, once from 8 h (récit 1 sexies).
  await checkConsumptionIfDue(getFirestore(), cloudMonitoring, send, new Date());
  // And the time capsules whose day has come (récit 39).
  await openDueCapsules(getFirestore(), send, new Date());
  // And, before it, the invitations to yesterday's albums (récit 41), once a day. A failure is
  // logged and tried again next time: it never stops the résumé or the polls.
  try {
    await queueAlbumInvitations(getFirestore(), new Date());
  } catch (e) {
    console.error('albumInvites', e);
  }
  // And the résumé of the day, once from 18 h (AVIS-14).
  await sendDigests(getFirestore(), send, new Date());
  // And the polls whose end date has passed (récits 2 bis et 2 ter).
  await closeDuePolls(getFirestore(), new Date());
  // And the day's notice of the accounts of each trip, once from 20 h (récit 16 bis).
  await sendExpenseNotices(getFirestore(), send, new Date());
});

// --- Question de la semaine (récit 37) --------------------------------------------------------

export const onWeeklyAnswerWritten = onDocumentWritten('weeklyQuestions/{weekId}/answers/{memberId}', async (event) => {
  const { weekId, memberId } = event.params;
  await countAnswers(getFirestore(), weekId);
  await removeOldAnswerPhoto(removeFiles, weekId, memberId, event.data?.before.data(), event.data?.after.data());
  if (isNewWeeklyAnswer(event.data?.before.data(), event.data?.after.data())) {
    await countGesture(getFirestore(), send, memberId, 'weeklyAnswers', souvenirMark.weeklyAnswer(weekId, memberId), new Date());
  }
});

export const onMemberDeletedAnswers = onDocumentDeleted('members/{memberId}', async (event) => {
  await deleteAnswersOf(getFirestore(), event.params.memberId);
  await deleteVotesOf(getFirestore(), event.params.memberId);
  await deleteQuietQueueOf(getFirestore(), event.params.memberId);
  await deleteSouvenirsOf(getFirestore(), event.params.memberId);
  await deleteTreeDataOf(getFirestore(), event.params.memberId);
  await deleteJoinMeOf(getFirestore(), event.params.memberId);
  await deleteDigestOf(getFirestore(), event.params.memberId);
});

// --- Sondages (récits 2 bis et 2 ter) ---------------------------------------------------------

export const onPollCreatedNotify = onDocumentCreated('polls/{pollId}', async (event) => {
  const data = event.data?.data() as PollData | undefined;
  if (data) await onPollCreated(getFirestore(), send, event.params.pollId, data);
});

export const onPollUpdated = onDocumentUpdatedWithAuthContext('polls/{pollId}', async (event) => {
  const before = event.data?.before.data() as PollData | undefined;
  const after = event.data?.after.data() as PollData | undefined;
  if (!before || !after) return;
  const db = getFirestore();
  const uid = actorUid(event);
  const actor = uid ? memberForUid(await loadMembers(db), uid) : null;
  await onPollClosed(db, send, event.params.pollId, before, after, actor?.id ?? null);
});

export const onPollDeleted = onDocumentDeleted('polls/{pollId}', async (event) => {
  await deletePollVotes(getFirestore(), event.params.pollId);
});

// --- « Rejoignez-moi » (récit 33) -----------------------------------------------------------------

export const onJoinMeCreatedNotify = onDocumentCreated('joinMe/{shareId}', async (event) => {
  const data = event.data?.data() as JoinMeData | undefined;
  if (data) await onJoinMeCreated(getFirestore(), send, event.params.shareId, data);
});
