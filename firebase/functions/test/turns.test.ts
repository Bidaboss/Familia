// À qui le tour ? (récit 58): the texts, then who hears what against the Firestore emulator.
// Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import { onTurnWritten, swapAskNotice, swapRefusedNotice, turnHousehold, yourTurnNotice, type TurnData } from '../src/turns';

function turn(overrides: Partial<TurnData> = {}): TurnData {
  return { title: 'Qui reçoit Noël', householdIds: ['h1', 'h2', 'h3'], current: 0, swap: null, createdBy: 'camille', ...overrides };
}

const ASK = { fromId: 'h2', toId: 'h1', byId: 'ines', byName: 'Inès' };

describe('texts of a rotation', () => {
  test('the turn of a household goes directly, and opens the rotation', () => {
    const n = yourTurnNotice('t1', turn());
    assert.equal(n.title, 'C\'est votre tour');
    assert.equal(n.body, '« Qui reçoit Noël » : c\'est à votre foyer. Touchez pour créer l\'événement.');
    assert.equal(n.direct, true);
    assert.equal(n.turnId, 't1');
    assert.equal(n.channel, 'helpRequests');
  });

  test('an exchange, and its refusal', () => {
    assert.equal(swapAskNotice('t1', turn(), ASK).body, 'Inès propose d\'échanger votre place pour « Qui reçoit Noël ».');
    assert.equal(swapRefusedNotice('t1', turn()).title, 'Pas d\'échange cette fois');
  });

  test('whose turn it is', () => {
    assert.equal(turnHousehold(turn({ current: 2 })), 'h3');
    assert.equal(turnHousehold(undefined), null);
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'turns-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-12-20T17:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    const family: [string, string, string][] = [
      ['camille', 'h1', 'adult'], ['noe', 'h1', 'adult'], ['lou', 'h1', 'childWithApp'], ['ines', 'h2', 'adult'], ['jules', 'h3', 'adult'],
    ];
    for (const [id, householdId, profile] of family) {
      await db.doc(`members/${id}`).set({ firstName: id, householdId, profile, uid: `uid-${id}` });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
  });

  const tokens = (sent: { token: string }[]) => sent.map((s) => s.token).sort();

  test('at the creation, the first household hears it, except the author; never a child', async () => {
    const { sent, send } = fakeSender();
    await onTurnWritten(db, send, 't1', undefined, turn(), 'uid-camille', now);
    assert.deepEqual(tokens(sent), ['token-noe']);
  });

  test('« C\'est fait »: the next household hears it is theirs', async () => {
    const { sent, send } = fakeSender();
    await onTurnWritten(db, send, 't1', turn(), turn({ current: 1 }), 'uid-noe', now);
    assert.deepEqual(tokens(sent), ['token-ines']);
    assert.equal(sent[0].notice.title, 'C\'est votre tour');
  });

  test('a pass: the household that is now first hears it', async () => {
    const { sent, send } = fakeSender();
    await onTurnWritten(db, send, 't1', turn(), turn({ householdIds: ['h2', 'h1', 'h3'] }), 'uid-camille', now);
    assert.deepEqual(tokens(sent), ['token-ines']);
  });

  test('an exchange proposed goes to the other household; a refusal back to the one who proposed', async () => {
    const asked = fakeSender();
    await onTurnWritten(db, asked.send, 't1', turn(), turn({ swap: ASK }), 'uid-ines', now);
    assert.deepEqual(tokens(asked.sent), ['token-camille', 'token-noe']);

    const refused = fakeSender();
    await onTurnWritten(db, refused.send, 't1', turn({ swap: ASK }), turn(), 'uid-camille', now);
    assert.deepEqual(tokens(refused.sent), ['token-ines']);
    assert.equal(refused.sent[0].notice.title, 'Pas d\'échange cette fois');
  });

  test('a withdrawn proposal tells nobody; an accepted one tells the household whose turn it now is', async () => {
    const withdrawn = fakeSender();
    await onTurnWritten(db, withdrawn.send, 't1', turn({ swap: ASK }), turn(), 'uid-ines', now);
    assert.equal(withdrawn.sent.length, 0);

    const accepted = fakeSender();
    await onTurnWritten(db, accepted.send, 't1', turn({ swap: ASK }), turn({ householdIds: ['h2', 'h1', 'h3'] }), 'uid-camille', now);
    assert.deepEqual(tokens(accepted.sent), ['token-ines']);
    assert.equal(accepted.sent[0].notice.title, 'C\'est votre tour');
  });

  test('a removed rotation, or a title changed, tells nobody', async () => {
    const { sent, send } = fakeSender();
    await onTurnWritten(db, send, 't1', turn(), undefined, 'uid-camille', now);
    await onTurnWritten(db, send, 't1', turn(), turn({ title: 'Repas du dimanche' }), 'uid-camille', now);
    assert.equal(sent.length, 0);
  });
});
