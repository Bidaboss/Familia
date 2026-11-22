package fr.familia.app.feature.trips.story

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.trips.detail.TripDays
import fr.familia.app.feature.trips.detail.flightKm
import java.time.LocalDate
import kotlin.math.roundToInt

/** The word of a step with the photos it chose that are still in the album, in the order chosen. */
data class StoryWord(val step: TripStep, val word: TripWord, val photos: List<AlbumPhoto>)

/**
 * One day of « Le voyage jour par jour » (récit 90): the words of its steps, in the order of the
 * trip, and the steps under way that day for the route of the end screen. [number] is « Jour 2 ».
 */
data class StoryDay(val date: LocalDate, val number: Int, val words: List<StoryWord>, val steps: List<TripStep>) {
    /** The route of the day: the places of its steps, in order, and its length when two are placed. */
    val routeLabel: String? get() = steps.map { it.place.trim() }.filter { it.isNotEmpty() }.distinct().takeIf { it.isNotEmpty() }?.joinToString(" → ")

    /** True when a step of the day has no position: its route is not drawn nor counted. */
    val hasUnplaced: Boolean get() = steps.any { !it.hasPosition }
    private val placed: List<TripStep> get() = steps.filter { it.hasPosition }

    /** As the crow flies between the placed steps of the day, in order; null before two are placed. */
    val distanceKm: Int? get() = placed.takeIf { it.size >= 2 }?.zipWithNext { a, b -> flightKm(a.lat!!, a.lng!!, b.lat!!, b.lng!!) }?.sum()?.roundToInt()
}

/**
 * The days of [trip] that have at least one word, first to last (a day counts if a step under way
 * that day has a word). A step outside the dates of the trip goes to its first or last day, so that
 * no word is lost.
 */
fun storyDays(trip: Trip, steps: List<TripStep>, words: Map<String, TripWord>, album: List<AlbumPhoto>): List<StoryDay> {
    val days = TripDays.of(trip)
    return days.mapIndexedNotNull { i, date ->
        val ofDay = TripDays.stepsOf(steps, date, days)
        // A word is told on the first day of its step, never twice.
        val told = ofDay.filter { step -> step.departDate.coerceIn(days.first(), days.last()) == date }
            .mapNotNull { step -> words[step.id]?.let { StoryWord(step, it, it.photosIn(album)) } }
        if (told.isEmpty()) null else StoryDay(date, i + 1, told, ofDay)
    }
}

/** One screen of the story. */
sealed interface StoryFrame {
    /** A photo of a word, full frame, with the word on it. */
    data class Photo(val story: StoryWord, val photo: AlbumPhoto) : StoryFrame

    /** A word with no photo, on a card over the landscape. */
    data class Text(val story: StoryWord) : StoryFrame

    /** The route of the day, then « Jour suivant » and « Revoir ce jour ». */
    data object End : StoryFrame
}

/** The screens of [day] in order: each word with its photos (or alone), then the route. */
fun storyFrames(day: StoryDay): List<StoryFrame> =
    day.words.flatMap { story ->
        if (story.photos.isEmpty()) listOf(StoryFrame.Text(story)) else story.photos.map { StoryFrame.Photo(story, it) }
    } + StoryFrame.End

/** The word a frame shows, null on the end screen. */
fun StoryFrame.story(): StoryWord? = when (this) {
    is StoryFrame.Photo -> story
    is StoryFrame.Text -> story
    StoryFrame.End -> null
}

/**
 * The day opened first: [wanted] when it has content, else today during the trip, else the last day
 * that has content (index in [days], or null with no day).
 */
fun firstStoryDay(days: List<StoryDay>, wanted: LocalDate?, today: LocalDate): Int? {
    if (days.isEmpty()) return null
    wanted?.let { w -> days.indexOfFirst { it.date == w }.takeIf { it >= 0 }?.let { return it } }
    days.indexOfFirst { it.date == today }.takeIf { it >= 0 }?.let { return it }
    return days.lastIndex
}
