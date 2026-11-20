// « @Prénom » dans une description (Kévin, 19/11): who is newly named, the event notice against the
// Firestore emulator, and the coup de main messages. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { HelpMember, helpMessages, HelpRequestData, withHelpMentions } from '../src/helpRequests';
import { EventData, Member, Notice } from '../src/logic';
import { namedIn, newlyNamed, notePreview, noteMentionTitle, notifyEventMentions } from '../src/mentions';
import { differs, occurrenceContent, SeriesData } from '../src/series';
import { Sender } from '../src/store';

const everyone = new Set(['camille', 'ines', 'jules', 'noe']);

describe('who is named', () => {
  test('a comment names each member once, never its author nor someone without the app', () => {
    assert.deepEqual(namedIn({ authorId: 'ines', mentionIds: ['noe', 'ines', 'noe', 'nina', 3] }, everyone), ['noe']);
    assert.deepEqual(namedIn({ authorId: 'ines' }, everyone), []);
  });

  test('a note tells only who it names for the first time', () => {
    assert.deepEqual(newlyNamed(undefined, { mentionIds: ['jules', 'ines'] }, 'camille', everyone), ['ines', 'jules']);
    assert.deepEqual(newlyNamed({ mentionIds: ['ines'] }, { mentionIds: ['ines', 'jules'] }, 'camille', everyone), ['jules']);
    assert.deepEqual(newlyNamed({ mentionIds: ['ines'] }, { mentionIds: ['ines'] }, 'camille', everyone), []);
    assert.deepEqual(newlyNamed(undefined, { mentionIds: ['camille', 'nina'] }, 'camille', everyone), []);
  });

  test('the title and a short note on one line', () => {
    assert.equal(noteMentionTitle('Camille', 'Pique-nique'), 'Camille t\'a nommé dans « Pique-nique »');
    assert.equal(notePreview('Apporte\n\nle pain'), 'Apporte le pain');
    assert.equal(notePreview('a'.repeat(300)).length, 120);
    assert.equal(notePreview(null), '');
  });
});

describe('a series', () => {
  const series: SeriesData = {
    title: 'Piscine', time: null, place: null, note: '@Inès on y va', targetType: 'family', targetMemberId: null, householdId: null,
    mentionIds: ['ines'], authorId: 'camille', authorName: 'Camille',
    rule: { freq: 'weekly', interval: 1, monthlyBy: null, start: '2026-12-01', until: null, count: null }, exdates: [], applyFrom: null,
  };

  test('each day keeps who the note names, and a change of them is a change', () => {
    const day = occurrenceContent(series, '2026-12-08');
    assert.deepEqual(day.mentionIds, ['ines']);
    assert.equal(differs(series, { ...day }), false);
    assert.equal(differs(series, { ...day, mentionIds: [] }), true);
    assert.equal(differs({ ...series, mentionIds: undefined }, { ...day, mentionIds: [] }), false);
  });
});

describe('a coup de main', () => {
  const members: HelpMember[] = [
    { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [] },
    { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [] },
    { id: 'jules', firstName: 'Jules', profile: 'adult', parentIds: [] },
  ];
  function request(overrides: Partial<HelpRequestData> = {}): HelpRequestData {
    return {
      kind: 'other', title: 'Porter un meuble', date: '2026-12-02', startTime: null, endTime: null, place: null,
      authorId: 'camille', authorName: 'Camille', forMemberId: 'camille', notifyIds: ['ines', 'jules'],
      status: 'open', volunteerId: null, volunteerName: null, declinedBy: null, thankedAt: null,
      note: '@Inès tu as la camionnette ?', mentionIds: ['ines'], ...overrides,
    };
  }
  const run = (before: HelpRequestData | undefined, now: HelpRequestData) =>
    withHelpMentions('r1', before, now, 'camille', members, helpMessages('r1', before, now, 'camille', members, '2026-11-30'));

  test('who is named hears « t\'a nommé » first, and not the usual notice too', () => {
    const messages = run(undefined, request());
    assert.deepEqual(messages[0].recipientIds, ['ines']);
    assert.equal(messages[0].notice.title, 'Camille t\'a nommé dans « Porter un meuble »');
    assert.equal(messages[0].notice.direct, true);
    assert.equal(messages[0].notice.helpRequestId, 'r1');
    assert.deepEqual(messages.slice(1).flatMap((m) => m.recipientIds), ['jules']);
  });

  test('nobody new named, nothing more; a cancelled request names nobody', () => {
    const same = run(request(), request({ title: 'Porter une armoire' }));
    assert.ok(same.every((m) => !m.notice.title.includes('t\'a nommé')));
    const cancelled = run(request({ mentionIds: [] }), request({ status: 'cancelled' }));
    assert.ok(cancelled.every((m) => !m.notice.title.includes('t\'a nommé')));
  });

  test('only someone warned can be named', () => {
    const messages = run(undefined, request({ notifyIds: ['jules'], mentionIds: ['ines'] }));
    assert.ok(messages.every((m) => !m.recipientIds.includes('ines')));
  });
});

describe('an event, against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'mentions-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  const members: Member[] = ['camille', 'ines', 'jules'].map((id) => ({ id, firstName: id[0].toUpperCase() + id.slice(1), profile: 'adult', householdId: 'h1', parentIds: [], uid: `uid-${id}` }));
  const event = (mentionIds: string[]): EventData => ({
    title: 'Pique-nique', date: '2026-12-05', time: null, place: null, note: '@Inès @Jules le pain', targetType: 'family',
    targetMemberId: null, householdId: null, mentionIds,
  });

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    for (const m of members) {
      await db.doc(`members/${m.id}`).set({ firstName: m.firstName, profile: 'adult', householdId: 'h1', parentIds: [], uid: m.uid });
      await db.doc(`members/${m.id}/devices/token-${m.id}`).set({ updatedAt: new Date() });
    }
  });

  test('each member newly named is told once, directly, and returned to be left out of the rest', async () => {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => { sent.push({ token, notice }); return 'ok'; };
    const now = new Date('2026-11-30T11:00:00Z');
    const named = await notifyEventMentions(db, send, 'e1', event(['ines']), event(['ines', 'jules', 'camille']), 'uid-camille', members, now);
    assert.deepEqual(named, ['jules']);
    assert.deepEqual(sent.map((s) => s.token), ['token-jules']);
    assert.equal(sent[0].notice.title, 'Camille t\'a nommé dans « Pique-nique »');
    assert.equal(sent[0].notice.eventId, 'e1');
    assert.equal(sent[0].notice.direct, true);
  });
});
