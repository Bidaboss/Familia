package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.canCreateSurprise
import fr.familia.app.core.model.nearestSurpriseOccasion
import fr.familia.app.core.model.surpriseCandidates
import fr.familia.app.core.model.surpriseDateFor
import fr.familia.app.core.model.surpriseMembers
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Créer une surprise (brief Cadeaux et Surprise, §3): who may start one, who it can be shown to, its
 * default occasion and date, saving, « Rejoindre ». TestFamily: admin1 and adult1 in h1, adult2 in h2,
 * kid1 (child with the app, parent adult1), kid2 (child without the app, parent adult2).
 */
class SurpriseEditTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 25)

    /** adult2 turns 40 on 3 February. */
    private val family = TestFamily.family.copy(
        members = TestFamily.family.members.map { if (it.id == "adult2") it.copy(birthDate = LocalDate.of(1987, 2, 3)) else it },
    )

    private fun surprise(id: String = "s1", members: List<String> = listOf("adult1"), creator: String = "adult1") = Surprise(
        id, "adult2", "Les 40 ans", SurpriseOccasion.Birthday, LocalDate.of(2027, 2, 3), members, creator, creator.replaceFirstChar { it.uppercase() },
    )

    private fun vm(me: String, beneficiary: String? = "adult2", surpriseId: String? = null, repo: FakeSurprises = FakeSurprises()) =
        SurpriseEditViewModel(repo, FakeMembers(family), me, beneficiary, surpriseId, today = { today })

    private fun editing(vm: SurpriseEditViewModel) = vm.uiState.value as SurpriseEditUiState.Editing

    @Test
    fun `only an adult starts a surprise, never for themself`() {
        assertTrue(canCreateSurprise(TestFamily.adult))
        assertFalse(canCreateSurprise(TestFamily.kid))
        assertEquals(SurpriseEditUiState.Unavailable, vm("kid1").uiState.value)
        assertEquals(SurpriseEditUiState.Unavailable, vm("adult2").uiState.value)
    }

    @Test
    fun `the surprise is shown to everyone with the app but the beneficiary`() {
        val ids = surpriseCandidates(family, "adult2").map { it.id }
        assertEquals(listOf("admin1", "adult1", "kid1"), ids)
        // Asked for the beneficiary or a child without the app: left out. The creator is always in.
        assertEquals(listOf("adult1"), surpriseMembers(family, "adult2", "adult1", setOf("adult2", "kid2")))
    }

    @Test
    fun `a new surprise starts at the nearest occasion, for the whole family but the person`() = runTest {
        val form = editing(vm("adult1")).form
        assertEquals(SurpriseOccasion.Christmas, form.occasion)
        assertEquals(LocalDate.of(2026, 12, 25), form.date)
        assertEquals(setOf("admin1", "adult1", "kid1"), form.memberIds)
        val adult2 = family.members.first { it.id == "adult2" }
        assertEquals(SurpriseOccasion.Birthday, nearestSurpriseOccasion(adult2, LocalDate.of(2027, 1, 2)))
        assertEquals(null, surpriseDateFor(SurpriseOccasion.Other, adult2, today))
    }

    @Test
    fun `changing the occasion moves the date, unless it was picked by hand`() = runTest {
        val vm = vm("adult1")
        vm.update { it.copy(occasion = SurpriseOccasion.Birthday) }
        assertEquals(LocalDate.of(2027, 2, 3), editing(vm).form.date)
        vm.update { it.copy(date = LocalDate.of(2027, 2, 6), dateTouched = true) }
        vm.update { it.copy(occasion = SurpriseOccasion.Other) }
        assertEquals(LocalDate.of(2027, 2, 6), editing(vm).form.date)
    }

    @Test
    fun `nobody else leaves me in, and an empty title takes the suggested one`() = runTest {
        val repo = FakeSurprises()
        val vm = vm("adult1", repo = repo)
        vm.update { it.copy(memberIds = emptySet()) }
        assertEquals(setOf("adult1"), editing(vm).form.memberIds)
        vm.save("Le Noël de Adult2")
        val saved = repo.surprises.value.single()
        assertEquals("Le Noël de Adult2", saved.title)
        assertEquals(listOf("adult1"), saved.memberIds)
        assertEquals("adult1", saved.creatorId)
        assertEquals(SurpriseEditUiState.Done(saved.id), vm.uiState.value)
    }

    @Test
    fun `a missing or past date is refused`() = runTest {
        val vm = vm("adult1")
        vm.update { it.copy(occasion = SurpriseOccasion.Other) }
        vm.save("Une surprise")
        assertEquals(setOf(SurpriseFormError.DateMissing), editing(vm).errors)
        vm.update { it.copy(date = LocalDate.of(2026, 11, 1), dateTouched = true) }
        assertEquals(setOf(SurpriseFormError.DatePast), editing(vm).errors)
    }

    @Test
    fun `a surprise already started by someone else is offered to join`() = runTest {
        val repo = FakeSurprises(listOf(surprise(members = listOf("admin1"), creator = "admin1")))
        val vm = vm("adult1", repo = repo)
        assertEquals(listOf("s1"), editing(vm).joinable.map { it.id })
        vm.join("s1")
        assertEquals(listOf("admin1", "adult1"), repo.surprises.value.single().memberIds)
        assertEquals(SurpriseEditUiState.Done("s1"), vm.uiState.value)
    }

    @Test
    fun `my own surprise for the same person is opened, not joined`() = runTest {
        val vm = vm("adult1", repo = FakeSurprises(listOf(surprise())))
        assertEquals(listOf("s1"), editing(vm).mine.map { it.id })
        assertTrue(editing(vm).joinable.isEmpty())
        vm.open("s1")
        assertEquals(SurpriseEditUiState.Done("s1"), vm.uiState.value)
    }

    @Test
    fun `only the creator changes or deletes the surprise`() = runTest {
        val repo = FakeSurprises(listOf(surprise(members = listOf("adult1", "admin1"))))
        assertEquals(SurpriseEditUiState.Unavailable, vm("admin1", beneficiary = null, surpriseId = "s1", repo = repo).uiState.value)
        val vm = vm("adult1", beneficiary = null, surpriseId = "s1", repo = repo)
        assertFalse(editing(vm).isNew)
        vm.delete()
        assertTrue(repo.surprises.value.isEmpty())
        assertEquals(SurpriseEditUiState.Done(null), vm.uiState.value)
    }

    @Test
    fun `a refused save says so and keeps the form`() = runTest {
        val vm = vm("adult1", repo = FakeSurprises(failWith = IllegalStateException("rules")))
        vm.save("Le Noël de Adult2")
        assertTrue(editing(vm).saveFailed)
    }
}
