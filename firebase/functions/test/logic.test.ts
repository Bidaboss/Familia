// Fictional family only: no real names or dates.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  addDays, changeNotice, changeRecipients, EventData, formatWhen, hasVisibleChange, Member, parisDay, reminders, RsvpStatus,
} from '../src/logic';

const NBSP = ' ';

const members: Member[] = [
  { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [], uid: 'uid-camille' },
  { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [], uid: 'uid-ines' },
  { id: 'sam', firstName: 'Sam', profile: 'adult', parentIds: [], uid: null },
  { id: 'noe', firstName: 'Noé', profile: 'childWithoutApp', parentIds: ['camille', 'sam'], uid: null },
  { id: 'lina', firstName: 'Lina', profile: 'childWithoutApp', parentIds: ['camille'], uid: null },
  { id: 'jade', firstName: 'Jade', profile: 'childWithApp', parentIds: ['ines'], uid: 'uid-jade' },
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
