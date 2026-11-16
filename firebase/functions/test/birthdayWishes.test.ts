import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  birthdaysOn, cardsNotice, deleteBirthdayCardsOf, invitationNotice, onBirthdayCardCreated, startBirthdays,
} from '../src/birthdayWishes';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';

describe('the birthday wishes, without the emulator', () => {
  test('the birthdays of the day, 29 February on the 28th, never a date to come', () => {
    const members = [
      { id: 'camille', birthDate: '1990-11-21' },
      { id: 'leap', birthDate: '2000-02-29' },
      { id: 'mistake', birthDate: '2030-11-21' },
      { id: 'none', birthDate: null },
    ];
    assert.deepEqual(birthdaysOn(members, '2026-11-21'), ['camille']);
    assert.deepEqual(birthdaysOn(members, '2027-02-28'), ['leap']);
  });

  test('the notices name the people, never a number', () => {
    const invite = invitationNotice('Camille', 'camille');
    assert.equal(invite.title, 'C\'est l\'anniversaire de Camille');
    assert.equal(invite.body, 'Envoie-lui tes vœux.');
    assert.equal(invite.channel, 'birthdays');
    assert.equal(invite.birthdayOf, 'camille');
    assert.equal(cardsNotice('camille', 2026, ['Inès']).body, 'Inès t\'a envoyé une carte de vœux.');
    const many = cardsNotice('camille', 2026, ['Inès', 'Lou', 'Noé']);
    assert.equal(many.body, 'Inès, Lou et Noé t\'ont envoyé une carte de vœux.');
    assert.equal(many.tag, 'birthdayCards-2026');
  });
});

describe('the birthday wishes against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'birthday-wishes-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  // 10 h in Paris, Saturday 21/11/2026.
  const morning = new Date('2026-11-21T09:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'goodNews', 'birthdayCards']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { birthdayWishes: true } });
    for (const [id, profile, birthDate] of [
      ['camille', 'adult', '1990-11-21'], ['ines', 'adult', '1985-06-01'], ['noe', 'adult', '1970-01-01'], ['lou', 'childWithoutApp', '2020-12-05'],
    ]) {
      batch.set(db.doc(`members/${id}`), { firstName: id === 'ines' ? 'Inès' : id, profile, parentIds: [], birthDate, householdId: 'h1' });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: morning });
    }
    await batch.commit();
  });

  test('on the day: one Bonne nouvelle of Familia, and the family is invited once, not the person', async () => {
    await db.doc('members/noe/settings/notifications').set({ birthdayWishes: false });
    const { sent, send } = fakeSender();
    assert.deepEqual(await startBirthdays(db, send, morning), ['camille']);
    const news = await db.doc('goodNews/birthday-2026-camille').get();
    assert.equal(news.get('birthdayOf'), 'camille');
    assert.equal(news.get('authorId'), 'familia');
    assert.equal(news.get('householdId'), 'h1');
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
    assert.equal(sent[0].notice.title, 'C\'est l\'anniversaire de camille');
    assert.deepEqual(await startBirthdays(db, send, new Date('2026-11-21T15:00:00Z')), []);
    assert.equal(sent.length, 1);
  });

  test('module off: no Bonne nouvelle, no notice', async () => {
    await db.doc('config/app').set({ features: { birthdayWishes: false } });
    const { sent, send } = fakeSender();
    assert.deepEqual(await startBirthdays(db, send, morning), []);
    assert.equal((await db.doc('goodNews/birthday-2026-camille').get()).exists, false);
    assert.equal(sent.length, 0);
  });

  test('each card tells the person, with the names of all who wrote that day', async () => {
    const { sent, send } = fakeSender();
    const first = { forMemberId: 'camille', authorId: 'ines', authorName: 'Inès', text: 'Bon anniversaire !', year: 2026 };
    await db.doc('birthdayCards/2026-camille-ines').set(first);
    assert.equal(await onBirthdayCardCreated(db, send, first), true);
    const second = { ...first, authorId: 'noe', authorName: 'noe' };
    await db.doc('birthdayCards/2026-camille-noe').set(second);
    await onBirthdayCardCreated(db, send, second);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille', 'token-camille']);
    assert.equal(sent[1].notice.body, 'Inès et noe t\'ont envoyé une carte de vœux.');
    await db.doc('members/camille/settings/notifications').set({ birthdayWishes: false });
    assert.equal(await onBirthdayCardCreated(db, send, second), false);
  });

  test('a member who leaves takes the cards they wrote and received', async () => {
    await db.doc('birthdayCards/2026-camille-ines').set({ forMemberId: 'camille', authorId: 'ines', year: 2026 });
    await db.doc('birthdayCards/2026-ines-camille').set({ forMemberId: 'ines', authorId: 'camille', year: 2026 });
    await db.doc('birthdayCards/2026-noe-ines').set({ forMemberId: 'noe', authorId: 'ines', year: 2026 });
    await deleteBirthdayCardsOf(db, 'camille');
    const left = await db.collection('birthdayCards').get();
    assert.deepEqual(left.docs.map((d) => d.id), ['2026-noe-ines']);
  });
});
