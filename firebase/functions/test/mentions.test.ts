// « @Prénom » dans une description (Kévin, 19/11): who is newly named and the coup de main
// messages. Fictional data only.
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { HelpMember, helpMessages, HelpRequestData, withHelpMentions } from '../src/helpRequests';
import { namedIn, newlyNamed, notePreview, noteMentionTitle } from '../src/mentions';

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
