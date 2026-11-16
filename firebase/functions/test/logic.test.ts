// Fictional family only: no real names or dates.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  addDays, changeNotice, changeRecipients, concerns, EventData, formatWhen, hasVisibleChange, Member, parisDay, reminders, RsvpStatus,
} from '../src/logic';

const NBSP = ' ';

const members: Member[] = [
  { id: 'camille', firstName: 'Camille', profile: 'adult', householdId: 'h1', parentIds: [], uid: 'uid-camille' },
  { id: 'ines', firstName: 'Inès', profile: 'adult', householdId: 'h2', parentIds: [], uid: 'uid-ines' },
  { id: 'sam', firstName: 'Sam', profile: 'adult', householdId: 'h1', parentIds: [], uid: null },
  { id: 'noe', firstName: 'Noé', profile: 'childWithoutApp', householdId: 'h1', parentIds: ['camille', 'sam'], uid: null },
  { id: 'lina', firstName: 'Lina', profile: 'childWithoutApp', householdId: 'h1', parentIds: ['camille'], uid: null },
  { id: 'jade', firstName: 'Jade', profile: 'childWithApp', householdId: 'h2', parentIds: ['ines'], uid: 'uid-jade' },
];

const event: EventData = {
  title: 'Piscine', date: '2026-12-19', time: '10:00', place: 'Centre aquatique', note: null,
  targetType: 'family', targetMemberId: null, householdId: null,
};

test('dates and times are written the French way', () => {
  assert.equal(formatWhen(event), `samedi 19 décembre à 10${NBSP}h${NBSP}00`);
  assert.equal(formatWhen({ date: '2027-01-04', time: null }), 'lundi 4 janvier');
  assert.equal(formatWhen({ date: '2027-01-04', time: '09:05' }), `lundi 4 janvier à 9${NBSP}h${NBSP}05`);
});

test('the day is the day in Paris, also around midnight UTC', () => {
  assert.equal(parisDay(new Date('2026-12-18T23:30:00Z')), '2026-12-19'); // 0 h 30 in Paris
  assert.equal(parisDay(new Date('2026-07-18T22:30:00Z')), '2026-07-19'); // summer time
  assert.equal(addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(addDays('2028-02-28', 1), '2028-02-29');
});

test('everyone with the app is told of a change, except its author', () => {
  assert.deepEqual(changeRecipients(members, 'camille', event, '2026-12-01'), ['ines', 'sam', 'jade']);
  // Unknown author (a change from the console): everyone with the app.
  assert.deepEqual(changeRecipients(members, null, event, '2026-12-01'), ['camille', 'ines', 'sam', 'jade']);
});

test('an event of a household reaches that household only (AVIS-7)', () => {
  const h1: EventData = { ...event, targetType: 'household', householdId: 'h1' };
  assert.deepEqual(changeRecipients(members, 'ines', h1, '2026-12-01'), ['camille', 'sam']);
  const h2: EventData = { ...event, targetType: 'household', householdId: 'h2' };
  assert.deepEqual(changeRecipients(members, 'camille', h2, '2026-12-01'), ['ines', 'jade']);
});

test("a child's event reaches their household and the adults responsible for them", () => {
  const forJade: EventData = { ...event, targetType: 'child', targetMemberId: 'jade' };
  assert.deepEqual(changeRecipients(members, null, forJade, '2026-12-01'), ['ines', 'jade']);
  // A step-parent in another household is responsible for Jade too.
  const blended = members.map((m) => (m.id === 'jade' ? { ...m, parentIds: ['ines', 'camille'] } : m));
  assert.deepEqual(changeRecipients(blended, null, forJade, '2026-12-01'), ['camille', 'ines', 'jade']);
});

test("a member's own event reaches their household", () => {
  const forSam: EventData = { ...event, targetType: 'self', targetMemberId: 'sam' };
  assert.deepEqual(changeRecipients(members, 'sam', forSam, '2026-12-01'), ['camille']);
});

test('an edit or a deletion also reaches whoever said they were coming', () => {
  const h1: EventData = { ...event, targetType: 'household', householdId: 'h1' };
  assert.deepEqual(changeRecipients(members, 'camille', h1, '2026-12-01', new Set(['ines'])), ['ines', 'sam']);
  // Never the author, even when they were coming.
  assert.deepEqual(changeRecipients(members, 'camille', h1, '2026-12-01', new Set(['camille'])), ['sam']);
});

test('the scope never includes a member who left, nor an unknown target', () => {
  const me = members[0];
  assert.equal(concerns({ targetType: 'self', targetMemberId: 'ghost', householdId: null }, me, members), false);
  assert.equal(concerns({ targetType: 'other', targetMemberId: null, householdId: null }, me, members), false);
  assert.equal(concerns({ targetType: 'household', targetMemberId: null, householdId: 'h1' }, { ...me, householdId: null }, members), false);
});

test('a past event notifies nobody', () => {
  assert.deepEqual(changeRecipients(members, 'camille', event, '2026-12-20'), []);
  assert.equal(changeRecipients(members, 'camille', event, '2026-12-19').length, 3); // today still counts
});

test('only visible changes notify', () => {
  assert.equal(hasVisibleChange(event, { ...event }), false);
  assert.equal(hasVisibleChange(event, { ...event, time: '11:00' }), true);
  assert.equal(hasVisibleChange(event, { ...event, place: null }), true);
});

test('change texts name the author and the event', () => {
  assert.equal(changeNotice('added', 'e1', event, 'Camille').body, `Camille a ajouté « Piscine », samedi 19 décembre à 10${NBSP}h${NBSP}00.`);
  assert.equal(changeNotice('edited', 'e1', event, null).title, 'Événement modifié');
  assert.equal(changeNotice('deleted', 'e1', event, 'Inès').body, 'Inès a supprimé « Piscine » du samedi 19 décembre.');
  assert.equal(changeNotice('added', 'e1', event, null).body.startsWith('Un membre de la famille a ajouté'), true);
  assert.equal(changeNotice('added', 'e1', event, 'Camille').channel, 'events');
});

test('with « Ajouter à mon agenda » on, a change or a deletion reminds to update the copy (récit 30)', () => {
  assert.match(changeNotice('edited', 'e1', event, 'Camille', true).body, /mets-le à jour depuis Familia\.$/);
  assert.match(changeNotice('deleted', 'e1', event, 'Camille', true).body, /pense à l'en retirer\.$/);
  assert.doesNotMatch(changeNotice('added', 'e1', event, 'Camille', true).body, /agenda/);
  assert.doesNotMatch(changeNotice('edited', 'e1', event, 'Camille').body, /agenda/);
});

test('the reminder goes to those coming or maybe coming, children without the app through their adults', () => {
  const answers = new Map<string, RsvpStatus>([
    ['ines', 'maybe'], ['jade', 'yes'], ['noe', 'yes'], ['lina', 'yes'], ['sam', 'no'], ['gone', 'yes'],
  ]);
  const result = reminders(members, 'e1', event, answers, new Set());
  assert.deepEqual(
    result.map((r) => [r.recipientId, r.notice.title]),
    [
      ['camille', 'Demain : Piscine (pour Lina et Noé)'], // one reminder for both children
      ['ines', 'Demain : Piscine'],
      ['jade', 'Demain : Piscine'],
      ['sam', 'Demain : Piscine (pour Noé)'], // Sam is not coming, Noé is
    ],
  );
  assert.equal(result[0].notice.body, `10${NBSP}h${NBSP}00 · Centre aquatique`);
  assert.equal(result[0].notice.channel, 'reminders');
});

test('an adult coming with their child gets one reminder that says so', () => {
  const answers = new Map<string, RsvpStatus>([['camille', 'yes'], ['lina', 'yes']]);
  const result = reminders(members, 'e1', { ...event, time: null, place: null }, answers, new Set());
  assert.deepEqual(result.map((r) => r.notice.title), ['Demain : Piscine (avec Lina)']);
  assert.equal(result[0].notice.body, 'Journée'); // the same word as the planning
});

test('a member who turned the reminder off gets none', () => {
  const answers = new Map<string, RsvpStatus>([['ines', 'yes'], ['camille', 'yes']]);
  assert.deepEqual(reminders(members, 'e1', event, answers, new Set(['ines'])).map((r) => r.recipientId), ['camille']);
});

test('« Qui apporte quoi » (récit 4 bis): the reminder says what each one brings, even without « Je viens »', () => {
  const answers = new Map<string, RsvpStatus>([['camille', 'yes'], ['sam', 'no']]);
  const bringing = new Map([['camille', ['Le pain', 'Le fromage']], ['jade', ['Les boissons']], ['sam', ['Le dessert']], ['gone', ['X']]]);
  const result = reminders(members, 'e1', event, answers, new Set(), bringing);
  assert.deepEqual(result.map((r) => r.recipientId), ['camille', 'jade']); // Sam said no; a member who left gets nothing
  assert.equal(result[0].notice.body, `10${NBSP}h${NBSP}00 · Centre aquatique\nTu apportes : Le pain, Le fromage`);
  assert.equal(result[1].notice.body, `10${NBSP}h${NBSP}00 · Centre aquatique\nTu apportes : Les boissons`);
});
