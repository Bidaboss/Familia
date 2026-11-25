package fr.familia.app.core.viewas

import fr.familia.app.core.model.WhoIsItPost
import fr.familia.app.core.model.WhoIsItGuess
import fr.familia.app.core.model.WhoIsItAnswer
import fr.familia.app.core.data.WhoIsItRepository
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.data.AppConfigRepository
import fr.familia.app.core.data.BringRepository
import fr.familia.app.core.data.CarpoolRepository
import fr.familia.app.core.data.EventSeriesRepository
import fr.familia.app.core.model.EventSeries
import fr.familia.app.core.model.Ride
import fr.familia.app.core.model.RideNeed
import java.time.LocalTime
import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.data.CommentPage
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.ExpenseRepository
import fr.familia.app.core.data.FamilyData
import fr.familia.app.core.data.FeedbackRepository
import fr.familia.app.core.data.ExportRepository
import fr.familia.app.core.data.GenealogyRepository
import fr.familia.app.core.data.KinPhotoChange
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.BirthdayCardRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.data.LendItemRepository
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.JoinMeRepository
import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.data.MemberPhoto
import fr.familia.app.core.data.MemberPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.data.NotificationRepository
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.data.SouvenirRepository
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.TripPlaceRepository
import fr.familia.app.core.data.TripWordRepository
import fr.familia.app.core.data.UsageRepository
import fr.familia.app.core.data.WeeklyQuestionRepository
import fr.familia.app.core.model.BringItem
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.model.CapsuleLeftover
import fr.familia.app.core.model.ChallengeRotation
import fr.familia.app.core.model.DayUsage
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseBook
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseHistoryEntry
import fr.familia.app.core.model.ExpenseSettlement
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.EveningKind
import fr.familia.app.core.model.NotificationFamily
import fr.familia.app.core.model.NotificationPrefs
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.model.NotebookBudget
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.ReceivedGift
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeComment
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters
import fr.familia.app.core.model.Transfer
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripItem
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripWord
import fr.familia.app.core.model.UsageDay
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.place.Place
import fr.familia.app.feature.auth.AuthRepository
import fr.familia.app.feature.auth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/*
 * « Voir comme » (PRD, rôles): the repositories of the app, read only. Every read goes to the real
 * repository; every write calls [ViewAsGuard.refuse], which says « Lecture seule » and throws, so
 * nothing reaches Firestore or Storage and no notification can start.
 *
 * Each wrapper implements its whole interface by hand, without delegation (`by`), so a method added
 * to an interface stops the build until it is sorted here as a read or a write. A method with a
 * default body is also written out: forgetting one would only show an empty list, never write.
 */

class ReadOnlyAuthRepository(private val inner: AuthRepository, private val guard: ViewAsGuard) : AuthRepository {
    override fun currentUser(): AuthUser? = inner.currentUser()
    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser = guard.refuse()
    override fun signOut() {
        guard.tell()
    }
}

class ReadOnlyMemberRepository(private val inner: MemberRepository, private val guard: ViewAsGuard) : MemberRepository {
    override suspend fun findByEmail(email: String): Member? = inner.findByEmail(email)
    override suspend fun findCachedByEmail(email: String): Member? = inner.findCachedByEmail(email)
    override suspend fun linkAccount(memberId: String, uid: String) = guard.refuse()
    override fun observeFamily(): Flow<Family> = inner.observeFamily()
    override fun newMemberId(): String = inner.newMemberId()
    override suspend fun saveMember(member: Member, previousEmail: String?) = guard.refuse()
    override suspend fun removeMember(member: Member, updatedChildren: List<Member>) = guard.refuse()
    override suspend fun savePhone(memberId: String, phone: String?) = guard.refuse()
    override suspend fun saveNickname(memberId: String, nickname: String?) = guard.refuse()
    override fun newHouseholdId(): String = inner.newHouseholdId()
    override suspend fun saveHousehold(household: Household) = guard.refuse()
    override suspend fun deleteHousehold(householdId: String) = guard.refuse()
}

class ReadOnlyMemberPhotoRepository(private val inner: MemberPhotoRepository, private val guard: ViewAsGuard) : MemberPhotoRepository {
    override fun observePhotos(): Flow<Map<String, MemberPhoto>> = inner.observePhotos()
    override suspend fun savePhoto(memberId: String, jpeg: ByteArray) = guard.refuse()
    override suspend fun removePhoto(memberId: String) = guard.refuse()
}

class ReadOnlyMemberCardRepository(private val inner: MemberCardRepository, private val guard: ViewAsGuard) : MemberCardRepository {
    override fun observeCard(memberId: String): Flow<MemberCard?> = inner.observeCard(memberId)
    override suspend fun saveCard(card: MemberCard, byId: String) = guard.refuse()
}

class ReadOnlyEventRepository(private val inner: EventRepository, private val guard: ViewAsGuard) : EventRepository {
    override fun observeUpcoming(from: LocalDate): Flow<List<Event>> = inner.observeUpcoming(from)
    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Event>> = inner.observeBetween(from, to)
    override suspend fun getEvent(id: String): Event? = inner.getEvent(id)
    override fun observeEvent(id: String): Flow<Event?> = inner.observeEvent(id)
    override fun observeOnDays(days: List<LocalDate>): Flow<List<Event>> = inner.observeOnDays(days)
    override fun observeCreatedSince(since: java.time.Instant, limit: Int): Flow<List<Event>> = inner.observeCreatedSince(since, limit)
    override fun newEventId(): String = inner.newEventId()
    override suspend fun saveEvent(event: Event, isNew: Boolean) = guard.refuse()
    override suspend fun deleteEvent(id: String) = guard.refuse()
}

class ReadOnlyRsvpRepository(private val inner: RsvpRepository, private val guard: ViewAsGuard) : RsvpRepository {
    override fun observeRsvps(eventId: String): Flow<List<Rsvp>> = inner.observeRsvps(eventId)
    override fun observeAllRsvps(): Flow<Map<String, List<Rsvp>>> = inner.observeAllRsvps()
    override fun observeRsvpsOf(eventIds: List<String>): Flow<Map<String, List<Rsvp>>> = inner.observeRsvpsOf(eventIds)
    override fun observeAnswersOf(memberId: String, eventIds: List<String>): Flow<Map<String, List<Rsvp>>> =
        inner.observeAnswersOf(memberId, eventIds)
    override suspend fun setRsvp(eventId: String, memberId: String, status: RsvpStatus, answeredBy: String) = guard.refuse()
}

class ReadOnlyBringRepository(private val inner: BringRepository, private val guard: ViewAsGuard) : BringRepository {
    override fun observe(eventId: String): Flow<List<BringItem>> = inner.observe(eventId)
    override suspend fun add(eventId: String, label: String, authorId: String, authorName: String, take: Boolean, recipeId: String?) =
        guard.refuse()
    override suspend fun take(eventId: String, itemId: String, memberId: String, firstName: String) = guard.refuse()
    override suspend fun release(eventId: String, itemId: String) = guard.refuse()
    override suspend fun delete(eventId: String, itemId: String) = guard.refuse()
}

class ReadOnlyEventCommentRepository(private val inner: EventCommentRepository, private val guard: ViewAsGuard) : EventCommentRepository {
    override fun observe(eventId: String, limit: Int): Flow<CommentPage> = inner.observe(eventId, limit)
    override suspend fun add(eventId: String, text: String, me: Member, mentionIds: List<String>) = guard.refuse()
    override suspend fun edit(eventId: String, commentId: String, text: String, mentionIds: List<String>) = guard.refuse()
    override suspend fun delete(eventId: String, commentId: String) = guard.refuse()
    override fun observeMuted(eventId: String, memberId: String): Flow<Boolean> = inner.observeMuted(eventId, memberId)
    override suspend fun setMuted(eventId: String, memberId: String, muted: Boolean) = guard.refuse()
}

class ReadOnlyHelpRequestRepository(private val inner: HelpRequestRepository, private val guard: ViewAsGuard) : HelpRequestRepository {
    override fun observeMine(memberId: String): Flow<List<HelpRequest>> = inner.observeMine(memberId)
    override fun observeRequest(id: String): Flow<HelpRequest?> = inner.observeRequest(id)
    override fun newRequestId(): String = inner.newRequestId()
    override suspend fun saveRequest(request: HelpRequest, isNew: Boolean) = guard.refuse()
    override suspend fun take(id: String, volunteerId: String, volunteerName: String, note: String?) = guard.refuse()
    override suspend fun setVolunteerNote(id: String, note: String?) = guard.refuse()
    override suspend fun withdraw(id: String) = guard.refuse()
    override suspend fun decline(id: String, memberId: String) = guard.refuse()
    override suspend fun markUnavailable(id: String, memberId: String) = guard.refuse()
    override suspend fun thank(id: String) = guard.refuse()
    override suspend fun cancel(id: String) = guard.refuse()
}

class ReadOnlyTripWordRepository(private val inner: TripWordRepository, private val guard: ViewAsGuard) : TripWordRepository {
    override fun observeWords(tripId: String): Flow<Map<String, TripWord>> = inner.observeWords(tripId)
    override fun observeWord(tripId: String, stepId: String): Flow<TripWord?> = inner.observeWord(tripId, stepId)
    override suspend fun saveWord(tripId: String, stepId: String, text: String, photoIds: List<String>, me: Member, isNew: Boolean) = guard.refuse()
    override suspend fun deleteWord(tripId: String, stepId: String) = guard.refuse()
    override suspend fun setHeart(tripId: String, stepId: String, memberId: String, hearted: Boolean) = guard.refuse()
    override fun comments(tripId: String, stepId: String): EventCommentRepository =
        ReadOnlyEventCommentRepository(inner.comments(tripId, stepId), guard)
}

class ReadOnlyTripPlaceRepository(private val inner: TripPlaceRepository, private val guard: ViewAsGuard) : TripPlaceRepository {
    override fun observePlaces(tripId: String): Flow<List<TripPlace>> = inner.observePlaces(tripId)
    override suspend fun getPlace(tripId: String, placeId: String): TripPlace? = inner.getPlace(tripId, placeId)
    override fun newPlaceId(tripId: String): String = inner.newPlaceId(tripId)
    override suspend fun savePlace(tripId: String, place: TripPlace, isNew: Boolean) = guard.refuse()
    override suspend fun deletePlace(tripId: String, placeId: String) = guard.refuse()
}

class ReadOnlyTripRepository(private val inner: TripRepository, private val guard: ViewAsGuard) : TripRepository {
    override fun observeFollowedTrips(memberId: String): Flow<List<Trip>> = inner.observeFollowedTrips(memberId)
    override fun observeTrip(id: String): Flow<Trip?> = inner.observeTrip(id)
    override fun observeSteps(tripId: String): Flow<List<TripStep>> = inner.observeSteps(tripId)
    override suspend fun getTrip(id: String): Trip? = inner.getTrip(id)
    override suspend fun getStep(tripId: String, stepId: String): TripStep? = inner.getStep(tripId, stepId)
    override fun newTripId(): String = inner.newTripId()
    override fun newStepId(tripId: String): String = inner.newStepId(tripId)
    override suspend fun saveTrip(trip: Trip, isNew: Boolean) = guard.refuse()
    override suspend fun deleteTrip(id: String) = guard.refuse()
    override suspend fun saveStep(tripId: String, step: TripStep, isNew: Boolean, remindAt: Instant?) = guard.refuse()
    override suspend fun deleteStep(tripId: String, stepId: String) = guard.refuse()
    override suspend fun markArrived(tripId: String, stepId: String, me: Member) = guard.refuse()
    override suspend fun alertFamily(tripId: String, me: Member) = guard.refuse()
    override suspend fun setCurrency(tripId: String, code: String?) = guard.refuse()
    override suspend fun clearArrival(tripId: String, stepId: String) = guard.refuse()
    override fun observeItems(tripId: String): Flow<List<TripItem>> = inner.observeItems(tripId)
    override fun newItemId(tripId: String): String = inner.newItemId(tripId)
    override suspend fun saveItem(tripId: String, item: TripItem, isNew: Boolean) = guard.refuse()
    override suspend fun setItemDone(tripId: String, itemId: String, done: Boolean) = guard.refuse()
    override suspend fun setItemAssignee(tripId: String, itemId: String, expectedId: String?, assignee: Member?): Boolean = guard.refuse()
    override suspend fun deleteItem(tripId: String, itemId: String) = guard.refuse()
}

class ReadOnlyCarpoolRepository(private val inner: CarpoolRepository, private val guard: ViewAsGuard) : CarpoolRepository {
    override fun observeRides(eventId: String): Flow<List<Ride>> = inner.observeRides(eventId)
    override fun observeNeeds(eventId: String): Flow<List<RideNeed>> = inner.observeNeeds(eventId)
    override suspend fun offer(eventId: String, driverId: String, seats: Int, from: String?, leaveAt: LocalTime?, isNew: Boolean) = guard.refuse()
    override suspend fun withdraw(eventId: String, driverId: String) = guard.refuse()
    override suspend fun getIn(eventId: String, driverId: String, memberId: String, leaving: String?) = guard.refuse()
    override suspend fun getOut(eventId: String, driverId: String, memberId: String) = guard.refuse()
    override suspend fun askSeat(eventId: String, memberId: String, askedBy: String) = guard.refuse()
    override suspend fun cancelAsk(eventId: String, memberId: String) = guard.refuse()
}

class ReadOnlyEventSeriesRepository(private val inner: EventSeriesRepository, private val guard: ViewAsGuard) : EventSeriesRepository {
    override suspend fun getSeries(id: String): EventSeries? = inner.getSeries(id)
    override fun newSeriesId(): String = inner.newSeriesId()
    override suspend fun saveSeries(series: EventSeries, isNew: Boolean) = guard.refuse()
    override suspend fun deleteSeries(id: String) = guard.refuse()
}

class ReadOnlyGoodDealRepository(private val inner: GoodDealRepository, private val guard: ViewAsGuard) : GoodDealRepository {
    override fun observeAll(): Flow<List<GoodDeal>> = inner.observeAll()
    override fun observe(id: String): Flow<GoodDeal?> = inner.observe(id)
    override suspend fun get(id: String): GoodDeal? = inner.get(id)
    override fun newId(): String = inner.newId()
    override suspend fun save(deal: GoodDeal, isNew: Boolean) = guard.refuse()
    override suspend fun delete(id: String) = guard.refuse()
    override suspend fun setInterested(id: String, memberId: String, interested: Boolean) = guard.refuse()
    override suspend fun setRecommended(id: String, memberId: String, recommended: Boolean) = guard.refuse()
}

class ReadOnlyLendItemRepository(private val inner: LendItemRepository, private val guard: ViewAsGuard) : LendItemRepository {
    override fun observeAll(readerId: String): Flow<List<LendItem>> = inner.observeAll(readerId)
    override fun observe(id: String, readerId: String): Flow<LendItem?> = inner.observe(id, readerId)
    override suspend fun get(id: String): LendItem? = inner.get(id)
    override fun newId(): String = inner.newId()
    override suspend fun save(item: LendItem, isNew: Boolean) = guard.refuse()
    override suspend fun delete(id: String) = guard.refuse()
    override suspend fun ask(id: String, ownerId: String, me: Member, from: Instant?, until: Instant?) = guard.refuse()
    override suspend fun accept(id: String) = guard.refuse()
    override suspend fun release(id: String) = guard.refuse()
}

class ReadOnlyRecipeRepository(private val inner: RecipeRepository, private val guard: ViewAsGuard) : RecipeRepository {
    override fun observeRecipes(): Flow<List<Recipe>> = inner.observeRecipes()
    override fun observeRecipe(id: String): Flow<Recipe?> = inner.observeRecipe(id)
    override suspend fun getRecipe(id: String): Recipe? = inner.getRecipe(id)
    override fun newRecipeId(): String = inner.newRecipeId()
    override suspend fun saveRecipe(recipe: Recipe, isNew: Boolean) = guard.refuse()
    override suspend fun deleteRecipe(id: String) = guard.refuse()
    override fun observeComments(recipeId: String): Flow<List<RecipeComment>> = inner.observeComments(recipeId)
    override suspend fun addComment(recipeId: String, text: String, me: Member, mentionIds: List<String>) = guard.refuse()
    override suspend fun deleteComment(recipeId: String, commentId: String) = guard.refuse()
}

class ReadOnlyCapsuleRepository(private val inner: CapsuleRepository, private val guard: ViewAsGuard) : CapsuleRepository {
    override fun observeWrittenBy(memberId: String): Flow<List<Capsule>> = inner.observeWrittenBy(memberId)
    override fun observeOpenedFor(memberId: String): Flow<List<Capsule>> = inner.observeOpenedFor(memberId)
    override fun observeCapsule(id: String): Flow<Capsule?> = inner.observeCapsule(id)
    override suspend fun deleteCapsule(id: String) = guard.refuse()
    override fun newCapsuleId(): String = inner.newCapsuleId()
    override suspend fun createCapsule(capsule: Capsule) = guard.refuse()
    override fun observeLeftovers(): Flow<List<CapsuleLeftover>> = inner.observeLeftovers()
    override suspend fun askDeleteLeftovers(memberId: String) = guard.refuse()
}

class ReadOnlyWhoIsItRepository(private val inner: WhoIsItRepository, private val guard: ViewAsGuard) : WhoIsItRepository {
    override fun observeLatest(count: Int): Flow<List<WhoIsItPost>> = inner.observeLatest(count)
    override fun observePost(id: String): Flow<WhoIsItPost?> = inner.observePost(id)
    override fun observeAnswer(id: String): Flow<WhoIsItAnswer?> = inner.observeAnswer(id)
    override fun observeGuesses(id: String): Flow<List<WhoIsItGuess>> = inner.observeGuesses(id)
    override fun newPostId(): String = inner.newPostId()
    override suspend fun publish(post: WhoIsItPost, answer: WhoIsItAnswer) = guard.refuse()
    override suspend fun setAnswer(id: String, answer: WhoIsItAnswer) = guard.refuse()
    override suspend fun guess(id: String, guess: WhoIsItGuess) = guard.refuse()
    override suspend fun delete(id: String) = guard.refuse()
}

class ReadOnlyBirthdayCardRepository(private val inner: BirthdayCardRepository, private val guard: ViewAsGuard) : BirthdayCardRepository {
    override fun observeFor(memberId: String, year: Int): Flow<List<BirthdayCard>> = inner.observeFor(memberId, year)
    override suspend fun save(card: BirthdayCard, isNew: Boolean) = guard.refuse()
    override suspend fun remove(card: BirthdayCard) = guard.refuse()
}

class ReadOnlyGoodNewsRepository(private val inner: GoodNewsRepository, private val guard: ViewAsGuard) : GoodNewsRepository {
    override fun observeLatest(count: Int): Flow<List<GoodNews>> = inner.observeLatest(count)
    override fun observePost(id: String): Flow<GoodNews?> = inner.observePost(id)
    override fun observeBetween(from: java.time.Instant, to: java.time.Instant): Flow<List<GoodNews>> = inner.observeBetween(from, to)
    override fun newId(): String = inner.newId()
    override suspend fun publish(news: GoodNews) = guard.refuse()
    override suspend fun edit(id: String, text: String, mentionIds: List<String>) = guard.refuse()
    override suspend fun react(id: String, memberId: String, reaction: Reaction?) = guard.refuse()
    override suspend fun setHidden(id: String, memberId: String, hidden: Boolean) = guard.refuse()
    override suspend fun remove(id: String) = guard.refuse()
}

class ReadOnlyWeeklyQuestionRepository(private val inner: WeeklyQuestionRepository, private val guard: ViewAsGuard) : WeeklyQuestionRepository {
    override fun observeLatest(count: Int): Flow<List<WeeklyQuestion>> = inner.observeLatest(count)
    override fun observeQuestion(id: String): Flow<WeeklyQuestion?> = inner.observeQuestion(id)
    override fun observeMyAnswer(questionId: String, memberId: String): Flow<WeeklyAnswer?> = inner.observeMyAnswer(questionId, memberId)
    override fun observeAnswers(questionId: String): Flow<List<WeeklyAnswer>> = inner.observeAnswers(questionId)
    override suspend fun answer(questionId: String, memberId: String, memberName: String, text: String?, photoId: String?) = guard.refuse()
    override fun newPhotoId(): String = inner.newPhotoId()
    override suspend fun removeAnswer(questionId: String, memberId: String) = guard.refuse()
}

class ReadOnlySouvenirRepository(private val inner: SouvenirRepository, private val guard: ViewAsGuard) : SouvenirRepository {
    override fun observeMine(memberId: String): Flow<List<Souvenir>> = inner.observeMine(memberId)
    override fun observeShown(memberId: String): Flow<List<Souvenir>> = inner.observeShown(memberId)
    override fun observeCounters(memberId: String): Flow<SouvenirCounters> = inner.observeCounters(memberId)
    override suspend fun setShown(memberId: String, key: String, shown: Boolean) = guard.refuse()
}

class ReadOnlyTreeRepository(private val inner: TreeRepository, private val guard: ViewAsGuard) : TreeRepository {
    override fun observeTree(): Flow<FamilyTree> = inner.observeTree()
    override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = inner.observeChallenge(month)
    override fun observePick(month: YearMonth): Flow<String?> = inner.observePick(month)
    override suspend fun pick(month: YearMonth, choiceId: String) = guard.refuse()
    override fun observeRotation(): Flow<ChallengeRotation?> = inner.observeRotation()
    override suspend fun setRotation(rotation: ChallengeRotation) = guard.refuse()
    override fun observeHosted(month: YearMonth): Flow<HostedChallenge?> = inner.observeHosted(month)
    override suspend fun saveHosted(challenge: HostedChallenge, memberId: String) = guard.refuse()
}

class ReadOnlyGenealogyRepository(private val inner: GenealogyRepository, private val guard: ViewAsGuard) : GenealogyRepository {
    override fun observeKin(): Flow<List<Kin>> = inner.observeKin()
    override fun observeLinks(): Flow<List<KinLink>> = inner.observeLinks()
    override fun observeWords(memberId: String): Flow<Map<String, String>> = inner.observeWords(memberId)
    override fun newKinId(): String = inner.newKinId()
    override suspend fun saveKin(kin: Kin, byMemberId: String, firstLink: KinLink?, photo: KinPhotoChange?) = guard.refuse()
    override suspend fun deleteKin(id: String) = guard.refuse()
    override suspend fun addLink(link: KinLink, byMemberId: String) = guard.refuse()
    override suspend fun removeLink(link: KinLink) = guard.refuse()
    override suspend fun saveWords(memberId: String, words: Map<String, String>) = guard.refuse()
}

class ReadOnlyPhotoStorage(private val inner: PhotoStorage, private val guard: ViewAsGuard) : PhotoStorage {
    override suspend fun upload(path: String, jpeg: ByteArray) = guard.refuse()
    override suspend fun downloadUrl(path: String): String = inner.downloadUrl(path)
    override suspend fun delete(path: String) = guard.refuse()
    override suspend fun bytes(path: String, maxBytes: Long): ByteArray? = inner.bytes(path, maxBytes)
}

class ReadOnlyPhotoUploader(private val guard: ViewAsGuard) : PhotoUploader {
    override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) = guard.refuse()
}

class ReadOnlyAlbumPhotoRepository(private val inner: AlbumPhotoRepository, private val guard: ViewAsGuard) : AlbumPhotoRepository {
    override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = inner.observePhotos(folder)
    override suspend fun addPhoto(folder: String, slot: Int, me: Member) = guard.refuse()
    override suspend fun deletePhoto(photo: AlbumPhoto) = guard.refuse()
}

class ReadOnlyAppConfigRepository(private val inner: AppConfigRepository, private val guard: ViewAsGuard) : AppConfigRepository {
    override fun observeAppConfig(): Flow<AppConfig> = inner.observeAppConfig()
    override suspend fun fetchAppConfig(): AppConfig = inner.fetchAppConfig()
    override suspend fun setFeature(key: String, enabled: Boolean) = guard.refuse()
    override suspend fun setDiscoverOrder(keys: List<String>) = guard.refuse()
    override suspend fun hideMemory(key: String) = guard.refuse()
}

/**
 * [adminId] reads, so a query for the lists of the member looked at would be refused as soon as one
 * of them is kept from the admin (their own surprise): the admin's lists are read instead, and only
 * those the member also sees are kept. The others stay hidden (« Masqué : c'est ta surprise »).
 */
class ReadOnlyGiftRepository(
    private val inner: GiftRepository,
    private val guard: ViewAsGuard,
    private val adminId: String,
    /** The member looked at: a list opened by its id (a link, a notification) follows their rule too. */
    private val viewedId: String,
) : GiftRepository {
    override fun observeVisibleLists(memberId: String): Flow<List<GiftList>> =
        inner.observeVisibleLists(adminId).map { lists -> listsSeenBy(lists, memberId) }
    override fun observeList(listId: String): Flow<GiftList?> = inner.observeList(listId).map { listSeenBy(it, viewedId) }
    override fun observeItems(listId: String, hiddenFromMe: Boolean): Flow<List<GiftItem>> = inner.observeItems(listId, hiddenFromMe)
    override fun observeClaims(listId: String): Flow<List<GiftClaim>> = inner.observeClaims(listId)
    override fun observeClaimsOf(listId: String, itemIds: List<String>): Flow<List<GiftClaim>> = inner.observeClaimsOf(listId, itemIds)
    override fun observeWishesSharedWith(memberId: String, listIds: List<String>): Flow<List<fr.familia.app.core.model.SharedWish>> =
        inner.observeWishesSharedWith(memberId, listIds)
    override fun observeShares(listId: String, itemIds: List<String>, memberId: String, allOf: Set<String>): Flow<Map<String, List<GiftShare>>> =
        inner.observeShares(listId, itemIds, memberId, allOf)
    override fun newListId(): String = inner.newListId()
    override fun newItemId(listId: String): String = inner.newItemId(listId)
    override suspend fun saveList(list: GiftList, isNew: Boolean) = guard.refuse()
    override suspend fun deleteList(listId: String) = guard.refuse()
    override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) = guard.refuse()
    override suspend fun deleteItem(listId: String, itemId: String) = guard.refuse()
    override suspend fun markWishReceived(listId: String, item: GiftItem) = guard.refuse()
    override suspend fun claim(listId: String, claim: GiftClaim) = guard.refuse()
    override suspend fun release(listId: String, itemId: String) = guard.refuse()
    override suspend fun setShare(listId: String, itemId: String, share: GiftShare, isNew: Boolean) = guard.refuse()
    override suspend fun markShareReceived(listId: String, itemId: String, memberId: String, received: Boolean) = guard.refuse()
    override suspend fun leaveShare(listId: String, itemId: String, memberId: String) = guard.refuse()
    override suspend fun setClaimOccasion(listId: String, itemId: String, occasion: GiftOccasion) = guard.refuse()
    override fun observeContributions(listId: String, itemIds: List<String>, memberId: String, all: Boolean) =
        inner.observeContributions(listId, itemIds, memberId, all)
    override suspend fun setContribution(listId: String, itemId: String, contribution: fr.familia.app.core.model.GiftContribution, previous: Int) =
        guard.refuse()
    override suspend fun removeContribution(listId: String, itemId: String, memberId: String, previous: Int) = guard.refuse()
    override fun observeReceived(listId: String): Flow<List<ReceivedGift>> = inner.observeReceived(listId)
    override suspend fun thank(listId: String, itemId: String) = guard.refuse()
}

class ReadOnlyNotebookRepository(private val inner: NotebookRepository, private val guard: ViewAsGuard) : NotebookRepository {
    override fun observeEntries(memberId: String): Flow<List<NotebookEntry>> = inner.observeEntries(memberId)
    override fun newEntryId(memberId: String): String = inner.newEntryId(memberId)
    override suspend fun saveIdea(memberId: String, entry: NotebookEntry, isNew: Boolean) = guard.refuse()
    override suspend fun follow(memberId: String, entryId: String, status: GiftStatus, occasion: NotebookOccasion, note: String?) = guard.refuse()
    override suspend fun deleteIdea(memberId: String, entryId: String) = guard.refuse()
    override fun observeBudgets(memberId: String): Flow<List<NotebookBudget>> = inner.observeBudgets(memberId)
    override suspend fun setBudget(memberId: String, budget: NotebookBudget, amount: Double?) = guard.refuse()
}

/** Nothing is counted while the admin looks through someone else's eyes (PRD, récit 1 nonies). */
class ReadOnlyUsageRepository(private val inner: UsageRepository, private val guard: ViewAsGuard) : UsageRepository {
    override suspend fun saveDay(memberId: String, day: LocalDate, usage: DayUsage) = guard.refuse()
    override suspend fun fetchDays(memberId: String, since: LocalDate): List<UsageDay> = inner.fetchDays(memberId, since)
    override suspend fun fetchLastDay(memberId: String): LocalDate? = inner.fetchLastDay(memberId)
}

class ReadOnlyFeedbackRepository(private val inner: FeedbackRepository, private val guard: ViewAsGuard) : FeedbackRepository {
    override suspend fun send(kind: Feedback.Kind, text: String, me: Member, context: FeedbackContext, screenshot: ByteArray?) = guard.refuse()
    override fun observeAll(): Flow<List<Feedback>> = inner.observeAll()
    override fun observeMine(memberId: String): Flow<List<Feedback>> = inner.observeMine(memberId)
    override suspend fun setStatus(id: String, status: Feedback.Status, note: String?) = guard.refuse()
    override suspend fun delete(id: String, screenshot: String?) = guard.refuse()
}

class ReadOnlyPollRepository(private val inner: PollRepository, private val guard: ViewAsGuard) : PollRepository {
    override fun observeMine(memberId: String): Flow<List<Poll>> = inner.observeMine(memberId)
    override fun observePoll(id: String): Flow<Poll?> = inner.observePoll(id)
    override fun observeVotes(pollId: String): Flow<List<PollVote>> = inner.observeVotes(pollId)
    override suspend fun create(
        kind: Poll.Kind,
        question: String,
        options: List<String>,
        multiple: Boolean,
        askedIds: List<String>,
        viewerIds: List<String>,
        closesAt: Instant?,
        authorId: String,
        authorName: String,
        eventId: String?,
    ): String = guard.refuse()
    override suspend fun vote(pollId: String, vote: PollVote) = guard.refuse()
    override suspend fun close(pollId: String) = guard.refuse()
    override suspend fun chooseDate(pollId: String, index: Int, eventId: String) = guard.refuse()
    override suspend fun delete(pollId: String) = guard.refuse()
}

class ReadOnlyJoinMeRepository(private val inner: JoinMeRepository, private val guard: ViewAsGuard) : JoinMeRepository {
    override fun observeReceived(memberId: String): Flow<List<JoinMe>> = inner.observeReceived(memberId)
    override fun observeSent(memberId: String): Flow<List<JoinMe>> = inner.observeSent(memberId)
    override suspend fun send(authorId: String, authorName: String, recipientIds: List<String>, place: Place, note: String?) = guard.refuse()
    override suspend fun delete(shareId: String) = guard.refuse()
}

/** The export is the admin's own tool: not from someone else's eyes. */
class ReadOnlyExportRepository(private val guard: ViewAsGuard) : ExportRepository {
    override suspend fun loadFamilyData(memberId: String): FamilyData = guard.refuse()
}

/**
 * The phone stays registered for the admin's own notifications: it is never registered for the
 * member being looked at, and their settings are not changed.
 */
class ReadOnlyNotificationRepository(private val inner: NotificationRepository, private val guard: ViewAsGuard) : NotificationRepository {
    override suspend fun registerThisDevice(memberId: String) = guard.refuse()
    override fun observeDayBeforeReminder(memberId: String): Flow<Boolean> = inner.observeDayBeforeReminder(memberId)
    override suspend fun setDayBeforeReminder(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observeGoodNews(memberId: String): Flow<Boolean> = inner.observeGoodNews(memberId)
    override suspend fun setGoodNews(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observeWeekly(memberId: String): Flow<Boolean> = inner.observeWeekly(memberId)
    override suspend fun setWeekly(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observeSouvenirs(memberId: String): Flow<Boolean> = inner.observeSouvenirs(memberId)
    override suspend fun setSouvenirs(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observeTree(memberId: String): Flow<Boolean> = inner.observeTree(memberId)
    override suspend fun setTree(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observeRecap(memberId: String): Flow<Boolean> = inner.observeRecap(memberId)
    override suspend fun setRecap(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observeBirthdayWishes(memberId: String): Flow<Boolean> = inner.observeBirthdayWishes(memberId)
    override suspend fun setBirthdayWishes(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observePackingReminder(memberId: String): Flow<Boolean> = inner.observePackingReminder(memberId)
    override suspend fun setPackingReminder(memberId: String, enabled: Boolean) = guard.refuse()
    override fun observePrefs(memberId: String): Flow<NotificationPrefs> = inner.observePrefs(memberId)
    override suspend fun setFamilies(memberId: String, families: Map<NotificationFamily, Boolean>) = guard.refuse()
    override suspend fun setQuietHours(memberId: String, enabled: Boolean) = guard.refuse()
    override suspend fun setEvening(memberId: String, kind: EveningKind, inSummary: Boolean) = guard.refuse()
}

class ReadOnlyExpenseRepository(private val inner: ExpenseRepository, private val guard: ViewAsGuard) : ExpenseRepository {
    override fun observeBook(container: ExpenseContainer): Flow<ExpenseBook?> = inner.observeBook(container)
    override suspend fun openBook(container: ExpenseContainer, creatorId: String, shares: Map<String, Double>, memberShares: Map<String, Double>) = guard.refuse()
    override suspend fun setShares(bookId: String, shares: Map<String, Double>, memberShares: Map<String, Double>) = guard.refuse()
    override fun observeExpenses(bookId: String): Flow<List<Expense>> = inner.observeExpenses(bookId)
    override fun observeSettlements(bookId: String): Flow<List<ExpenseSettlement>> = inner.observeSettlements(bookId)
    override fun newExpenseId(bookId: String): String = inner.newExpenseId(bookId)
    override fun observeHistory(bookId: String, expenseId: String): Flow<List<ExpenseHistoryEntry>> = inner.observeHistory(bookId, expenseId)
    override suspend fun saveExpense(bookId: String, expense: Expense, me: Member, isNew: Boolean) = guard.refuse()
    override suspend fun deleteExpense(bookId: String, expenseId: String) = guard.refuse()
    override suspend fun setClosed(bookId: String, closed: Boolean) = guard.refuse()
    override suspend fun settle(bookId: String, transfer: Transfer, me: Member) = guard.refuse()
    override suspend fun unsettle(bookId: String, settlementId: String) = guard.refuse()
}

/**
 * [list] when [memberId] may read it, else null, as the rules answer « introuvable »: on a projector
 * in « Voir comme », a surprise never shows to the one it is for (récit 1 octies, surprise de la démo).
 */
fun listSeenBy(list: GiftList?, memberId: String): GiftList? = list?.takeIf { listsSeenBy(listOf(it), memberId).isNotEmpty() }

/** The lists of [lists] that [memberId] sees, by the rule of canSeeList (firebase/firestore.rules). */
fun listsSeenBy(lists: List<GiftList>, memberId: String): List<GiftList> = lists.filter { list ->
    list.kind == GiftListKind.Wishes
        // The ideas of the family stay hidden from their person, « Voir comme » included (récit 9 ter).
        || (list.kind == GiftListKind.Ideas && list.beneficiaryId != memberId)
        || memberId in list.viewerIds || (list.beneficiaryId == memberId && list.revealed)
}
