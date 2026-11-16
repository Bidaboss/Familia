package fr.familia.app.feature.joinme

import fr.familia.app.core.data.JoinMeRepository
import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeJoinMe(private val now: Instant = Instant.parse("2026-11-21T16:00:00Z"), private val failWith: Exception? = null) : JoinMeRepository {
    val shares = MutableStateFlow<List<JoinMe>>(emptyList())
    private var next = 0
    override fun observeReceived(memberId: String): Flow<List<JoinMe>> = shares.map { list -> list.filter { memberId in it.recipientIds } }
    override fun observeSent(memberId: String): Flow<List<JoinMe>> = shares.map { list -> list.filter { it.authorId == memberId } }
    override suspend fun send(authorId: String, authorName: String, recipientIds: List<String>, place: Place, note: String?) {
        failWith?.let { throw it }
        shares.value = shares.value + JoinMe("j${next++}", authorId, authorName, recipientIds, place, note, now, now.plus(JoinMe.LIFETIME))
    }
    override suspend fun delete(shareId: String) {
        failWith?.let { throw it }
        shares.value = shares.value.filter { it.id != shareId }
    }
}

class FakeLocator(
    var position: Pair<Double, Double>? = 46.4915 to -1.7931,
    private val addresses: Map<Pair<Double, Double>, String> = mapOf((46.4915 to -1.7931) to "1 promenade de la Mer, 85100 Les Sables"),
    private val places: Map<String, Place> = mapOf("12 rue des Lilas" to Place(47.21, -1.55, address = "12 rue des Lilas, 44000 Nantes")),
    private val shortLinks: Map<String, String> = emptyMap(),
) : PlaceLocator {
    override suspend fun currentPosition(): Pair<Double, Double>? = position
    override suspend fun addressOf(lat: Double, lng: Double): String? = addresses[lat to lng]
    override suspend fun find(query: String): Place? = places[query]
    override suspend fun expand(shortLink: String): String? = shortLinks[shortLink]
}
