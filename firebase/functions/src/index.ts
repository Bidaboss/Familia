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
import { onRequest } from 'firebase-functions/v2/https';
import { deleteCalendarFeedOf, feedFor } from './calendarFeed';
import { RECURRING_EVENTS, ruleLabel, syncSeries, writtenBySeries, type SeriesData } from './series';
import { CARPOOL, clearRidesOf, clearRidesOfMember, onRideChanged, type RideData } from './carpool';
import {
  deleteClaimShares, deleteItemContributions, deleteGiftListContent, deleteMemberGiftLists, GiftItemData, onGiftItemChanged, revealGiftLists,
  sendBirthdayGiftReminders, updateSharesTotal,
} from './gifts';
import { countLeftovers, deleteLeftovers, openDueCapsules } from './capsules';
import { CommentData, deleteCommentMutesOf, deleteEventComments, onCommentCreated } from './comments';
import { removeKinFromTree, removeMemberFromTree } from './genealogy';
import { notifyEventMentions } from './mentions';
import { checkConsumptionIfDue } from './consumption';
import { CrashData, onCrashReported } from './crashes';
import { FeedbackData, onFeedbackChanged, onFeedbackCreated, screenshotToDelete } from './feedback';
import { deleteEndedGoodDeals, sendGoodDealsSummary } from './goodDeals';
import { deleteLoanOf, onLendLoanWritten, releaseLendItemsOf, sendLendReminders, type LendLoanData } from './lendItems';
import { deleteJoinMeOf, JoinMeData, onJoinMeCreated } from './joinMe';
import { AnnouncementData, deleteAnnouncementsOf, onAnnouncementCreated } from './announcements';
import { closeDuePolls, closeEventPolls, deletePollVotes, deleteVotesOf, onPollClosed, onPollCreated, PollData } from './polls';
import { cloudMonitoring } from './monitoring';
import { deleteMemberNotebook, syncNotebookEntry, syncNotebookItem } from './notebook';
import {
  deleteReactionDigest, FileRemover, GoodNewsData, GoodNewsNotice, GoodNewsSender, onGoodNewsEdited, onGoodNewsPublished,
  newReactorIds, onGoodNewsReacted, onGoodNewsRemoved, sendReactionDigests,
} from './goodNews';
import {
  HelpNotice, HelpRequestData, HelpSender, onHelpRequestWritten, reopenRequestsOf, sendStillOpenReminders,
} from './helpRequests';
import { actorUid, addDays, changeNotice, EventData, hasVisibleChange, Member, Notice, parisDay } from './logic';
import { comingMembers, deleteEventAnswers, featureAudience, loadMembers, deleteMemberData, memberForUid, notify, notifyChange, releaseBringOf, Sender, sendDayBeforeReminders } from './store';
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
import { deleteGoodNewsComments, GoodNewsCommentData, onGoodNewsCommentCreated, onGoodNewsCommentDeleted } from './goodNewsComments';
import { orphanReportDue, reportOrphanGoodNewsPhotos, type StoredFile } from './orphanPhotos';
import { capBursts, deleteQuietQueueOf, PRIVATE_CHANNELS, sendBurstHeld, sendEveningHeld, sendHeldNotices } from './prefs';
import { deleteBirthdayCardsOf, onBirthdayCardCreated, startBirthdays, type BirthdayCard } from './birthdayWishes';
import { deleteWelcomeOf, isFirstSignIn, sendQueuedWelcomes, welcomeMember } from './welcome';
import { deleteCheersOf, deleteMomentsOf } from './thinkOfMe';
import { eraseTurns } from './turns';
import { keepReceivedPhoto, revealWishes, thankGivers } from './wishesReveal';
import { cleanupOldLists, migrateToSingleList } from './singleList';
import { deleteEventReminders, deleteSeriesReminders, sendMyReminders } from './reminders';
import { dropBags, leftTheTrip, sendPackingReminders } from './packing';
import { deleteItemPhoto } from './itemPhoto';
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

// Avalanche du 21: no phone rings more than 3 times an hour, the rest goes out grouped (prefs.ts).
capBursts();

const UNREGISTERED = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']);


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
        ...(notice.goodDeals ? { goodDeals: notice.goodDeals } : {}),
        ...(notice.lendItemId ? { lendItemId: notice.lendItemId } : {}),
        ...(notice.announcementId ? { announcementId: notice.announcementId } : {}),
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

export const onEventCreated = onDocumentCreatedWithAuthContext('events/{eventId}', async (event) => {
  const data = event.data?.data() as EventData | undefined;
  // An occurrence of a series (récit 54) is written by the server: the series sends one notice.
  if (!data || writtenBySeries(undefined, event.data?.data())) return;
  const db = getFirestore();
  const now = new Date();
  // Who is named « @Prénom » in the note (Kévin, 19/11) hears it once, as « t'a nommé ».
  const named = await notifyEventMentions(db, send, event.params.eventId, undefined, data, actorUid(event), await loadMembers(db), now);
  await notifyChange(db, send, 'added', event.params.eventId, data, actorUid(event), now, new Set(), undefined, '', (ids) => ids.filter((id) => !named.includes(id)));
});

export const onEventUpdated = onDocumentUpdatedWithAuthContext('events/{eventId}', async (event) => {
  const before = event.data?.before.data() as EventData | undefined;
  const after = event.data?.after.data() as EventData | undefined;
  if (!before || !after) return;
  const renamed = JSON.stringify(before.mentionIds ?? []) !== JSON.stringify(after.mentionIds ?? []);
  if (!hasVisibleChange(before, after) && !renamed) return;
  if (writtenBySeries(event.data?.before.data(), event.data?.after.data())) return;
  const db = getFirestore();
  const now = new Date();
  const members = await loadMembers(db);
  const named = await notifyEventMentions(db, send, event.params.eventId, before, after, actorUid(event), members, now);
  if (!hasVisibleChange(before, after)) return;
  const answered = await comingMembers(db, event.params.eventId, members);
  await notifyChange(db, send, 'edited', event.params.eventId, after, actorUid(event), now, answered, members, '', (ids) => ids.filter((id) => !named.includes(id)));
});

export const onEventDeleted = onDocumentDeletedWithAuthContext('events/{eventId}', async (event) => {
  const db = getFirestore();
  const data = event.data?.data() as EventData | undefined;
  // The answers are read first: those who were coming are told, then the answers go.
  try {
    const members = await loadMembers(db);
    const told = ((data as { seriesTold?: string[] } | undefined)?.seriesTold) ?? [];
    const answered = await comingMembers(db, event.params.eventId, members, told);
    if (data && (data as { cancelledBySeries?: boolean }).cancelledBySeries) {
      // An occurrence taken out of its series (récit 54): only those who were coming hear of it.
      const only = await featureAudience(db, RECURRING_EVENTS);
      const to = only([...answered]);
      if (to.length > 0) await notify(db, send, to, changeNotice('deleted', event.params.eventId, data, null));
    } else if (data) {
      await notifyChange(db, send, 'deleted', event.params.eventId, data, actorUid(event), new Date(), answered, members);
    }
  } finally {
    // Each one is tried even if another fails: answers, the album (récit 41), records and files, comments (récit 28),
    // and its polls closed (récit 2 quater).
    const results = await Promise.allSettled([
      deleteEventAnswers(db, event.params.eventId),
      db.recursiveDelete(db.collection('events').doc(event.params.eventId).collection('photos')),
      deleteEventComments(db, event.params.eventId),
      getStorage().bucket().deleteFiles({ prefix: `events/${event.params.eventId}/` }),
      closeEventPolls(db, event.params.eventId),
      deleteEventReminders(db, event.params.eventId),
    ]);
    const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
    if (failed) throw failed.reason;
  }
});

// « Événements qui reviennent » (récit 54): the occurrences follow their series, with one notice for it.
export const onSeriesWritten = onDocumentWrittenWithAuthContext('eventSeries/{seriesId}', async (event) => {
  const db = getFirestore();
  const now = new Date();
  const result = await syncSeries(db, event.params.seriesId, parisDay(now));
  const before = event.data?.before.data() as SeriesData | undefined;
  const after = event.data?.after.data() as SeriesData | undefined;
  if (!after) {
    await deleteSeriesReminders(db, event.params.seriesId);
    return;
  }
  const repeat = ` Puis ${ruleLabel(after.rule)}.`;
  // recurringEvents off: the admin principal tries it alone, nobody else is told.
  const only = await featureAudience(db, RECURRING_EVENTS);
  // Who is named « @Prénom » in the note of the series hears it once, on its first day.
  const told = async (first: string, data: EventData, members: Member[]) => {
    const named = await notifyEventMentions(db, send, first, before as EventData | undefined, { ...data, mentionIds: after.mentionIds ?? [] }, actorUid(event), members, now, only);
    return (ids: string[]) => only(ids).filter((id) => !named.includes(id));
  };
  if (!before && result.created.length > 0) {
    const first = [...result.created].sort()[0];
    const data = (await db.collection('events').doc(first).get()).data() as EventData;
    const members = await loadMembers(db);
    await notifyChange(db, send, 'added', first, data, actorUid(event), now, new Set(), members, repeat, await told(first, data, members));
  } else if (before && result.updated.length > 0) {
    const first = [...result.updated].sort()[0];
    const data = (await db.collection('events').doc(first).get()).data() as EventData;
    const members = await loadMembers(db);
    const answered = await comingMembers(db, first, members);
    await notifyChange(db, send, 'edited', first, data, actorUid(event), now, answered, members, ` Et les suivantes.`, await told(first, data, members));
  }
});

// L'arbre (récit 35): « Je viens » adds a leaf for the member who comes, once per event.
export const onRsvpWritten = onDocumentWritten('events/{eventId}/rsvps/{memberId}', async (event) => {
  const { eventId, memberId } = event.params;
  const before = event.data?.before.get('status');
  const after = event.data?.after.get('status');
  // « On y va ensemble » (récit 52): « Je ne viens plus » takes the member out of the cars.
  if (before !== undefined && before !== 'no' && (after === 'no' || after === undefined)) {
    await clearRidesOf(getFirestore(), eventId, memberId);
  }
  if (after !== 'yes' || before === 'yes') return;
  await grow('rsvp', () => addLeaf(getFirestore(), memberId, treeMark.rsvp(eventId, memberId), new Date()));
});

// « On y va ensemble » (récit 52): the driver hears who gets in or out, the passengers that the car goes.
export const onRideWritten = onDocumentWrittenWithAuthContext('events/{eventId}/rides/{driverId}', async (event) => {
  const db = getFirestore();
  const members = await loadMembers(db);
  const before = (event.data?.before.data() as RideData | undefined) ?? null;
  const after = (event.data?.after.data() as RideData | undefined) ?? null;
  const actorId = memberForUid(members, actorUid(event))?.id ?? null;
  // carpool off: the admin principal tries it alone, nobody else is told.
  const only = await featureAudience(db, CARPOOL);
  await onRideChanged(db, (ids, notice) => (only(ids).length > 0 ? notify(db, send, only(ids), notice) : Promise.resolve(0)), event.params.eventId, event.params.driverId, before, after, actorId, members);
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
  await deleteWelcomeOf(getFirestore(), event.params.memberId);
  await deleteMomentsOf(getFirestore(), event.params.memberId);
  // « Voir dans mon agenda » (récit 49): their link stops working.
  await deleteCalendarFeedOf(getFirestore(), event.params.memberId);
  // « On y va ensemble » (récit 52): out of the cars to come.
  await clearRidesOfMember(getFirestore(), event.params.memberId, parisDay(new Date()));
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

// « Plantages » (récit 6 sexies, rév. 220): the admin principal is told of a new kind of crash, once a day at most.
export const onCrashSent = onDocumentCreated('crashReports/{reportId}', async (event) => {
  const data = event.data?.data() as CrashData | undefined;
  if (data) await onCrashReported(getFirestore(), send, event.params.reportId, data);
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
  // Deleted: its image goes too (Kévin 16/11, a QR code), even if the notices below fail.
  if (before && !after) await getStorage().bucket().deleteFiles({ prefix: `helpRequests/${event.params.requestId}/` });
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
  // Bons plans (récit 45): the summary of the day, then the bons plans ended 30 days ago.
  try {
    await sendGoodDealsSummary(getFirestore(), send, now);
    await deleteEndedGoodDeals(getFirestore(), now);
  } catch (e) {
    console.error('good deals', e);
  }
  // On se prête (récit 51): the one reminder, 3 days after the return date.
  try {
    await sendLendReminders(getFirestore(), send, now);
  } catch (e) {
    console.error('lend reminders', e);
  }
  // À qui le tour ? is removed: its data goes once, if Administration did not do it first.
  try {
    await eraseTurns(getFirestore(), now);
  } catch (e) {
    console.error('turns erase', e);
  }
});

// A bon plan removed (by its author, the admin principal or the cleanup) takes its photo with it.
export const onGoodDealDeleted = onDocumentDeleted('goodDeals/{dealId}', async (event) => {
  await getStorage().bucket().deleteFiles({ prefix: `goodDeals/${event.params.dealId}/` });
});

// On se prête (récit 51): an ask to the owner, the answer to the one who asked. The loan lives
// apart from the thing (lendLoans), read by the owner and the borrower only (EXPERT-9).
export const onLendLoanChanged = onDocumentWrittenWithAuthContext('lendLoans/{itemId}', async (event) => {
  const before = event.data?.before.data() as LendLoanData | undefined;
  const after = event.data?.after.data() as LendLoanData | undefined;
  await onLendLoanWritten(getFirestore(), send, event.params.itemId, before, after, actorUid(event), new Date());
});

// Kept, empty, under its old name: the deploy runs --non-interactive and would stop rather than
// delete a function that left the code (same trigger kept too). The notices of a loan moved to onLendLoanChanged.
export const onLendItemChanged = onDocumentUpdatedWithAuthContext('lendItems/{itemId}', async () => {});

// --- À qui le tour ? (récit 58), supprimé le 20/11 ------------------------------------------

// Nothing is sent any more. Kept, empty: the deploy runs --non-interactive and would stop rather
// than delete a function that left the code.
export const onTurnChanged = onDocumentWrittenWithAuthContext('turns/{turnId}', async () => {});

// The turns are erased once (Kévin, 20/11), at the first change in Administration > Modules after
// the deploy (any switch), or else by the task of 18 h. migrations/turnsErased keeps it to once.
export const onTurnsErase = onDocumentWritten({ document: 'config/app', timeoutSeconds: 300 }, async () => {
  await eraseTurns(getFirestore(), new Date());
});

// A thing removed takes its loan and its photo with it.
export const onLendItemDeleted = onDocumentDeleted('lendItems/{itemId}', async (event) => {
  await deleteLoanOf(getFirestore(), event.params.itemId);
  await getStorage().bucket().deleteFiles({ prefix: `lendItems/${event.params.itemId}/` });
});

// Spec, cas limites: a volunteer removed from the family frees their upcoming requests.
export const onMemberDeletedHelp = onDocumentDeleted('members/{memberId}', async (event) => {
  await reopenRequestsOf(getFirestore(), event.params.memberId, new Date());
  // On se prête (récit 51): their things go, and what they asked for or had is free again.
  await releaseLendItemsOf(getFirestore(), event.params.memberId);
});

// --- Listes et cadeaux (V2) --------------------------------------------------------------------

export const onGiftListDeleted = onDocumentDeleted('giftLists/{listId}', async (event) => {
  await Promise.all([
    deleteGiftListContent(getFirestore(), event.params.listId),
    // The photos of its wishes and ideas (récit 44).
    getStorage().bucket().deleteFiles({ prefix: `giftLists/${event.params.listId}/` }),
  ]);
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
  // A photo the item no longer names (récit 44), whoever removed the item: « name.jpg » and « name-thumb.jpg ».
  const photo = typeof before?.photo === 'string' ? before.photo : null;
  // « Reçue » (rév. 232): the photo is copied to « On m'a offert » first, with « Plus de photos ».
  if (photo && !after) await keepReceivedPhoto(getFirestore(), event.params.listId, event.params.itemId, photo);
  if (photo && photo.startsWith(`giftLists/${event.params.listId}/`) && photo !== after?.photo) {
    await deleteItemPhoto(photo);
  }
  if (before && after) await syncNotebookItem(getFirestore(), event.params.listId, event.params.itemId);
  // Récit 68: the participations of a removed wish go with it.
  if (before && !after) await deleteItemContributions(getFirestore(), event.params.listId, event.params.itemId);
});

// « Liste unique » (récit 9 ter): when its switch is turned on in Administration, the open lists of an occasion
// are copied into « Mes envies » and the ideas of the family, then the old lists copied whole are deleted
// (PRODUIT-58). Turned on again, it copies only what was not copied yet, then deletes what can go.
export const onSingleListSwitched = onDocumentWritten({ document: 'config/app', timeoutSeconds: 540 }, async (event) => {
  const before = event.data?.before.get('features.singleList') === true;
  const after = event.data?.after.get('features.singleList') === true;
  if (before || !after) return;
  const copied = await migrateToSingleList(getFirestore());
  console.log(`singleList: ${copied} items copied`);
  const { deleted, kept } = await cleanupOldLists(getFirestore());
  console.log(`oldListsCleanup: ${deleted} lists deleted, ${kept} kept`);
});

// Its switch is gone: the old lists go with « Liste unique » above. Still exported, because the
// deployment (non interactive) refuses to delete a function; it does nothing.
export const onOldListsCleanupSwitched = onDocumentWritten('config/app', async () => {});

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

// A moment removed takes its encouragements with it (récit 43).
export const onMomentDeleted = onDocumentDeleted('moments/{momentId}', async (event) => {
  await deleteCheersOf(getFirestore(), event.params.momentId);
});

// « Pensez à moi » is retired (PRODUIT-136): this one does nothing any more. It stays exported, as
// « Publier Firebase » deploys without --force and would stop on a function to remove.
export const onMomentCheerWritten = onDocumentCreated('momentCheers/{cheerId}', async () => {});

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

// The default bucket of the project (familia-93286.firebasestorage.app, US-WEST1): no region to set.
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
  await deleteGoodNewsComments(getFirestore(), event.params.postId);
});

// Commentaires des bonnes nouvelles (récit 67): the count of the news, and who is told.
export const onGoodNewsCommentWritten = onDocumentCreated('goodNews/{postId}/comments/{commentId}', async (event) => {
  const data = event.data?.data() as GoodNewsCommentData | undefined;
  if (!data) return;
  await onGoodNewsCommentCreated(getFirestore(), sendGoodNews, event.params.postId, event.params.commentId, data);
});

export const onGoodNewsCommentRemoved = onDocumentDeleted('goodNews/{postId}/comments/{commentId}', async (event) => {
  await onGoodNewsCommentDeleted(getFirestore(), event.params.postId);
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

// « Ma valise » (récit 62): a participant taken off the trip leaves their bag behind. It goes,
// so it never comes back to them nor stays in the database for nothing.
export const onTripParticipantsChanged = onDocumentUpdated('trips/{tripId}', async (event) => {
  const gone = leftTheTrip(event.data?.before.get('participantIds'), event.data?.after.get('participantIds'));
  await dropBags(getFirestore(), event.params.tripId, gone);
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
  await removeKinFromTree(getFirestore(), event.params.kinId);
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

/** « Qui est-ce ? » (récit 43): a removed photo takes its answer, its guesses and its files with it. */
export const onWhoIsItDeleted = onDocumentDeleted('whoIsIt/{postId}', async (event) => {
  const post = getFirestore().collection('whoIsIt').doc(event.params.postId);
  const authorId = String(event.data?.get('authorId') ?? '');
  const results = await Promise.allSettled([
    getFirestore().recursiveDelete(post.collection('answer')),
    getFirestore().recursiveDelete(post.collection('guesses')),
    ...(authorId ? [getStorage().bucket().deleteFiles({ prefix: `whoIsIt/${authorId}/${event.params.postId}/` })] : []),
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

/**
 * The Mode présentation (récit 48) was removed on 18/11 (PRODUIT-132). Its trigger stays, doing
 * nothing: « Publier Firebase » runs without --force and would stop on a function to delete. To
 * remove after the 21st, with the function deleted by hand in the console.
 */
export const onPresentationWritten = onDocumentWritten('config/presentation', async () => {
  // Nothing: config/presentation is ignored.
});

export const arrivalReminder = onSchedule({ schedule: 'every 30 minutes', timeZone: 'Europe/Paris' }, async () => {
  // First what was held during the quiet hours (récit 6 quinquies), from 8 h. A failure waits
  // for the next run and never stops the rest.
  try {
    await sendHeldNotices(getFirestore(), sendHeld, new Date());
  } catch (e) {
    console.error('quietQueue', e);
  }
  // Then the bienvenues that waited, and what went past 3 notices an hour (avalanche du 21). A
  // failure waits for the next run and never stops the rest.
  try {
    await sendQueuedWelcomes(getFirestore(), send, new Date());
  } catch (e) {
    console.error('welcomeQueue', e);
  }
  try {
    await sendBurstHeld(getFirestore(), sendHeld, new Date());
  } catch (e) {
    console.error('burstQueue', e);
  }
  await sendArrivalReminders(getFirestore(), send, new Date());
  // And the reminders each one asked for on an event (récit 56). A failure waits for the next run.
  try {
    await sendMyReminders(getFirestore(), send, new Date());
  } catch (e) {
    console.error('myReminders', e);
  }
  // And « Ma valise » the day before a trip, from 18 h (récit 62). A failure waits for the next run.
  try {
    await sendPackingReminders(getFirestore(), send, new Date());
  } catch (e) {
    console.error('packing', e);
  }
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
  // And the résumé of the day, once from 18 h (AVIS-14), from 19 h with the résumé du soir.
  await sendDigests(getFirestore(), send, new Date());
  // And the résumé du soir, from 19 h (récit 70). A failure waits for the next run.
  try {
    await sendEveningHeld(getFirestore(), sendHeld, new Date());
  } catch (e) {
    console.error('eveningQueue', e);
  }
  // And the polls whose end date has passed (récits 2 bis et 2 ter).
  await closeDuePolls(getFirestore(), new Date());
  // And the day's notice of the accounts of each trip, once from 20 h (récit 16 bis).
  await sendExpenseNotices(getFirestore(), send, new Date());
  // And, on Sunday at 4 h, the list of the good news photos linked to nothing (note C de #263).
  // Read only: nothing is deleted before the 21/11. A failure never stops the rest.
  if (orphanReportDue(new Date())) {
    try {
      await reportOrphanGoodNewsPhotos(getFirestore(), listStoredFiles, new Date());
    } catch (e) {
      console.error('orphanPhotos', e);
    }
  }
});

/** The files under a prefix of the default bucket, with their date and size. */
const listStoredFiles = async (prefix: string): Promise<StoredFile[]> => {
  const [files] = await getStorage().bucket().getFiles({ prefix });
  return files.map((f) => ({
    name: f.name,
    updated: new Date(f.metadata.updated ?? f.metadata.timeCreated ?? 0),
    size: Number(f.metadata.size ?? 0),
  }));
};

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
  await deleteAnnouncementsOf(getFirestore(), event.params.memberId);
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

// --- Annonces de l'admin (récit 69) ---------------------------------------------------------

export const onAnnouncementCreatedNotify = onDocumentCreated('announcements/{announcementId}', async (event) => {
  const data = event.data?.data() as AnnouncementData | undefined;
  if (data) await onAnnouncementCreated(getFirestore(), send, event.params.announcementId, data, new Date());
});

/**
 * « Voir le planning dans mon agenda » (récit 49): https://…/calendarFeed?t=<token>, the secret link
 * of one member, read by their calendar every few hours. A wrong or replaced link gets a 404.
 */
export const calendarFeed = onRequest({ cors: false }, async (req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.status(405).send('');
    return;
  }
  try {
    const feed = await feedFor(getFirestore(), req.query.t, new Date());
    if (feed === null) {
      res.status(404).send('');
      return;
    }
    res.set('Content-Type', 'text/calendar; charset=utf-8');
    res.set('Cache-Control', 'private, max-age=900');
    res.set('X-Robots-Tag', 'noindex');
    res.status(200).send(feed);
  } catch (e) {
    console.error('calendarFeed', e);
    res.status(500).send('');
  }
});

