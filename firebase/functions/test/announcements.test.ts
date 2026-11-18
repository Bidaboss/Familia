// Annonces de l'admin (récit 69): the text of the notice and who gets it (pure logic), then the
// day, the night, « Urgent » and the mode présentation against the Firestore emulator. Fictional
// data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { AnnouncementData, announcementNotice, announcementRecipients, deleteAnnouncementsOf, onAnnouncementCreated } from '../src/announcements';
import { sendHeldNotices } from '../src/prefs';
import { Sender } from '../src/store';

function announcement(overrides: Partial<AnnouncementData> = {}): AnnouncementData {
  return {
    title: 'Repas de dimanche', text: 'On se retrouve à midi chez Inès.', urgent: false,
    recipientIds: ['ines', 'noe', 'lou'], authorId: 'camille', authorName: 'Camille', ...overrides,
  };
}

// 13 h and 23 h in Paris (UTC+1 in November).
const DAY = new Date('2026-11-18T12:00:00Z');
const NIGHT = new Date('2026-11-18T22:00:00Z');

describe('text and recipients', () => {
  test('the title, then the message on one line, opening the announcement', () => {
    const n = announcementNotice('a1', announcement({ text: 'On se retrouve\nà midi.' }));
    assert.equal(n.channel, 'admin');
    assert.equal(n.title, 'Repas de dimanche');
    assert.equal(n.body, 'On se retrouve à midi.');
    assert.equal(n.announcementId, 'a1');
    assert.equal(n.direct, true);
  });

  test('a long message is cut on the phone, whole in the app', () => {
    const n = announcementNotice('a1', announcement({ text: 'a'.repeat(300) }));
    assert.equal(n.body.length, 180);
    assert.ok(n.body.endsWith('…'));
  });

  test('only those ticked who are in the family, never the author', () => {
    assert.deepEqual(announcementRecipients(announcement({ recipientIds: ['ines', 'camille', 'parti'] }), ['camille', 'ines', 'noe']), ['ines']);
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'announcements-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['announcements', 'members', 'quietQueue', 'notifyLog', 'burstQueue', 'presentationQueue']) await db.recursiveDelete(db.collection(c));
    await db.doc('config/presentation').delete();
    for (const id of ['camille', 'ines', 'noe']) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult' });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
    await db.doc('members/lou').set({ firstName: 'Lou', profile: 'childWithoutApp' });
    await db.doc('config/app').set({ features: { adminAnnouncements: true } });
    await db.doc('config/family').set({ ownerMemberId: 'camille' });
  });

  test('by day: every member ticked with the app gets it at once', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await onAnnouncementCreated(db, send, 'a1', announcement(), DAY), 'sent');
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-noe']);
    assert.equal((await db.doc('announcements/a1').get()).get('delivery'), 'sent');
  });

  test('nobody can turn it off: it comes even with every switch off', async () => {
    await db.doc('members/ines/settings/notifications').set({ planning: false, reminders: false, gifts: false, goodNews: false, comments: false, helpRequests: false, trips: false, weekly: false });
    const { sent, send } = fakeSender();
    await onAnnouncementCreated(db, send, 'a1', announcement(), DAY);
    assert.ok(sent.some((s) => s.token === 'token-ines'));
  });

  test('at night it waits for 8 h, for everyone', async () => {
    await db.doc('members/noe/settings/notifications').set({ quietHours: false });
    const { sent, send } = fakeSender();
    assert.equal(await onAnnouncementCreated(db, send, 'a1', announcement(), NIGHT), 'morning');
    assert.equal(sent.length, 0);
    for (const id of ['ines', 'noe']) {
      const items = (await db.doc(`quietQueue/${id}`).get()).get('items') as Notice[];
      assert.equal(items[0].announcementId, 'a1');
    }
  });

  test('at 8 h it comes alone and whole, never inside « Cette nuit dans Familia »', async () => {
    const { sent, send } = fakeSender();
    await onAnnouncementCreated(db, send, 'a1', announcement(), NIGHT);
    await db.doc('quietQueue/ines').update({
      items: FieldValue.arrayUnion(
        { channel: 'events', title: 'Nouvel événement', body: 'Goûter', heldAt: NIGHT.toISOString() },
        { channel: 'goodNews', title: 'Bonne nouvelle', body: 'Lou a marché', heldAt: NIGHT.toISOString() },
      ),
    });
    await sendHeldNotices(db, send, new Date('2026-11-19T07:30:00Z'));
    const toInes = sent.filter((s) => s.token === 'token-ines').map((s) => s.notice);
    const mine = toInes.find((n) => n.announcementId === 'a1');
    assert.ok(mine);
    assert.equal(mine.title, 'Repas de dimanche');
    assert.equal(toInes.length, 2);
  });

  test('« Urgent » rings at once, even at night', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await onAnnouncementCreated(db, send, 'a1', announcement({ urgent: true }), NIGHT), 'sent');
    assert.equal(sent.length, 2);
  });

  test('during the mode présentation nothing goes', async () => {
    await db.doc('config/presentation').set({ on: true, until: null });
    const { sent, send } = fakeSender();
    assert.equal(await onAnnouncementCreated(db, send, 'a1', announcement(), DAY), 'refused');
    assert.equal(sent.length, 0);
  });

  test('module off, or written by someone else than the admin principal: nothing goes', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await onAnnouncementCreated(db, send, 'a1', announcement({ authorId: 'ines' }), DAY), 'refused');
    await db.doc('config/app').set({ features: {} });
    assert.equal(await onAnnouncementCreated(db, send, 'a2', announcement(), DAY), 'refused');
    assert.equal(sent.length, 0);
  });

  test('a member removed takes what they sent', async () => {
    await db.doc('announcements/a1').set({ ...announcement() });
    await db.doc('announcements/a2').set({ ...announcement({ authorId: 'ines' }) });
    await deleteAnnouncementsOf(db, 'camille');
    assert.equal((await db.doc('announcements/a1').get()).exists, false);
    assert.equal((await db.doc('announcements/a2').get()).exists, true);
  });
});
