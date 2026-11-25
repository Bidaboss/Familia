package fr.familia.app.feature.trips.documents

import fr.familia.app.core.data.TripDocument
import fr.familia.app.core.data.TripDocumentKind
import fr.familia.app.core.data.TripDocumentRepository
import fr.familia.app.core.data.TripDocumentType
import fr.familia.app.core.data.TripDocuments
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class TripDocumentsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val me = Member("m1", "Alice", null, null, "h1", MemberProfile.Adult, false, emptyList(), "u1")

    private fun document(id: String, ownerId: String = "m1", kind: TripDocumentKind = TripDocumentKind.Ticket) =
        TripDocument(id, ownerId, "Alice", "Billet $id", kind, TripDocumentType.Pdf, 10, null)

    private class FakeDocuments : TripDocumentRepository {
        val list = MutableStateFlow<List<TripDocument>>(emptyList())
        var failAdd = false
        var failDelete = false
        override fun observe(tripId: String): Flow<List<TripDocument>> = list
        override suspend fun add(tripId: String, me: Member, title: String, kind: TripDocumentKind, type: TripDocumentType, bytes: ByteArray) {
            if (failAdd) throw IOException("offline")
            list.value = listOf(TripDocument("d${list.value.size + 1}", me.id, me.firstName, title, kind, type, bytes.size.toLong(), null)) + list.value
        }
        override suspend fun bytes(tripId: String, document: TripDocument): ByteArray? = if (document.id == "gone") null else byteArrayOf(1, 2)
        override suspend fun delete(tripId: String, document: TripDocument) {
            if (failDelete) throw IOException("offline")
            list.value = list.value.filterNot { it.id == document.id }
        }
    }

    @Test
    fun `a PDF, a JPEG or a PNG of 5 MB at most, titled after its name`() {
        val ok = checkPicked("application/pdf", "billet-aller.pdf", ByteArray(100)) as PickedFile.Ok
        assertEquals(TripDocumentType.Pdf, ok.type)
        assertEquals("billet-aller", ok.title)
        assertTrue(checkPicked("image/png", null, ByteArray(TripDocuments.MAX_BYTES.toInt())) is PickedFile.Ok)
        assertEquals(PickedFile.Refused(DocumentProblem.TooBig), checkPicked("image/jpeg", "scan.jpg", ByteArray(TripDocuments.MAX_BYTES.toInt() + 1)))
        assertEquals(PickedFile.Refused(DocumentProblem.WrongType), checkPicked("application/zip", "a.zip", ByteArray(10)))
        assertEquals(PickedFile.Refused(DocumentProblem.WrongType), checkPicked(null, "a.pdf", ByteArray(10)))
        assertEquals(PickedFile.Refused(DocumentProblem.AddFailed), checkPicked("application/pdf", "a.pdf", ByteArray(0)))
        assertEquals(80, suggestedTitle("x".repeat(120) + ".pdf").length)
        assertEquals("", suggestedTitle(null))
    }

    @Test
    fun `the owner removes their own, the creator or the admin any, nobody else`() {
        assertTrue(canDeleteDocument(document("a"), me, canManage = false))
        assertFalse(canDeleteDocument(document("b", ownerId = "m2"), me, canManage = false))
        assertTrue(canDeleteDocument(document("b", ownerId = "m2"), me, canManage = true))
    }

    @Test
    fun `the list is live, an add and a delete go through, a failure is said`() = runTest {
        val repo = FakeDocuments()
        val vm = TripDocumentsViewModel(repo, "t1", me)
        assertEquals(DocumentsState.Ready(emptyList()), vm.state.value)

        val file = checkPicked("application/pdf", "billet.pdf", ByteArray(10)) as PickedFile.Ok
        vm.add(file, "  ", TripDocumentKind.Ticket)
        assertTrue(repo.list.value.isEmpty())
        vm.add(file, "Billet aller", TripDocumentKind.Ticket)
        val added = (vm.state.value as DocumentsState.Ready).documents.single()
        assertEquals("Billet aller", added.title)
        assertFalse(vm.sending.value)

        repo.failAdd = true
        vm.add(file, "Autre", TripDocumentKind.Paper)
        assertEquals(DocumentProblem.AddFailed, vm.problem.value)
        vm.dismissProblem()
        assertNull(vm.problem.value)

        assertEquals(2, vm.bytes(added)?.size)
        assertNull(vm.bytes(document("gone")))
        assertEquals(DocumentProblem.OpenFailed, vm.problem.value)

        repo.failDelete = true
        vm.delete(added)
        assertEquals(DocumentProblem.DeleteFailed, vm.problem.value)
        repo.failDelete = false
        vm.delete(added)
        assertNull(vm.problem.value)
        assertEquals(DocumentsState.Ready(emptyList()), vm.state.value)
    }

    @Test
    fun `without the repository (« Voir comme »), nothing is read`() {
        assertEquals(DocumentsState.Failed, TripDocumentsViewModel(null, "t1", me).state.value)
    }
}
