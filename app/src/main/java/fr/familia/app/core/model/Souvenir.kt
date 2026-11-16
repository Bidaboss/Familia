package fr.familia.app.core.model

import java.time.Instant

/**
 * A souvenir given by the server (PRD, récit 34): members/{memberId}/souvenirs/{key}. Private
 * until its member [shown]s it on their page (carte EXPERT-3). The keys are those of
 * functions/src/souvenirs.ts and never change.
 */
data class Souvenir(val key: String, val earnedAt: Instant?, val shown: Boolean)

/** The counted gestures of souvenirCounters/{memberId}, read by the member only. */
data class SouvenirCounters(val reactions: Int = 0, val helpGiven: Int = 0, val weeklyAnswers: Int = 0)
