package fr.familia.app.feature.recipes

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.data.recipeOrder
import fr.familia.app.core.model.Recipe
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FakeRecipes(initial: List<Recipe> = emptyList()) : RecipeRepository {
    val recipes = MutableStateFlow(initial)
    val saved = mutableListOf<Pair<Recipe, Boolean>>()
    override fun observeRecipes(): Flow<List<Recipe>> = recipes.map { it.sortedWith(recipeOrder) }
    override fun observeRecipe(id: String): Flow<Recipe?> = recipes.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getRecipe(id: String): Recipe? = recipes.value.firstOrNull { it.id == id }
    override fun newRecipeId(): String = "r-new"
    override suspend fun saveRecipe(recipe: Recipe, isNew: Boolean) {
        saved += recipe to isNew
        recipes.value = recipes.value.filterNot { it.id == recipe.id } + recipe
    }
    override suspend fun deleteRecipe(id: String) {
        recipes.value = recipes.value.filterNot { it.id == id }
    }
}

/** Fictional recipes of the test family (see TestFamily): adult1 wrote both. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun recipe(id: String, title: String, ingredients: String = "farine", tags: List<String> = emptyList(), originMemberId: String? = null) =
        Recipe(id, title, "", originMemberId, null, ingredients, "Cuire.", tags, "adult1", "Adult1", null)

    private val apple = recipe("r1", "Gâteau aux pommes", "4 pommes\nfarine", listOf("dessert"))
    private val soup = recipe("r2", "Soupe de potiron", "1 potiron\n1 oignon", listOf("hiver"), originMemberId = "adult2")

    @Test
    fun `the search ignores case and accents, and looks in ingredients, tags and origin`() {
        val family = TestFamily.family
        assertTrue(matches(apple, "gateau", family))
        assertTrue(matches(apple, "POMMES dessert", family))
        assertTrue(matches(soup, "adult2", family))
        assertFalse(matches(soup, "pommes", family))
        assertTrue(matches(soup, "  ", family))
    }

    @Test
    fun `tags are trimmed, without doubles, six at most`() {
        assertEquals(listOf("dessert", "Noël"), parseTags(" dessert, Noël , Dessert,, "))
        assertEquals(6, parseTags("a,b,c,d,e,f,g").size)
    }

    @Test
    fun `the book is by title and filtered as one types`() = runTest {
        val vm = RecipesViewModel(FakeRecipes(listOf(soup, apple)), FakeMembers())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("r1", "r2"), (vm.uiState.value as RecipesUiState.Ready).rows.map { it.recipe.id })

        vm.search("potiron")

        val ready = vm.uiState.value as RecipesUiState.Ready
        assertEquals(listOf("r2"), ready.rows.map { it.recipe.id })
        assertEquals("Adult2", ready.rows.single().origin)
        assertEquals(2, ready.total)
    }

    @Test
    fun `the author or the admin principal manages a recipe, and its removal closes it`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        fun detail(me: String) = RecipeDetailViewModel(repo, FakeMembers(), me, "r1").also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
        assertFalse((detail("adult2").uiState.value as RecipeDetailUiState.Ready).canManage)
        assertTrue((detail("admin1").uiState.value as RecipeDetailUiState.Ready).canManage)
        val mine = detail("adult1")

        mine.remove()

        assertEquals(RecipeDetailUiState.Removed, mine.uiState.value)
        assertTrue(repo.recipes.value.isEmpty())
    }

    @Test
    fun `a new recipe is checked, then saved line by line in the author's name`() = runTest {
        val repo = FakeRecipes()
        val vm = RecipeEditViewModel(repo, FakeMembers(), "adult2", recipeId = null)
        vm.save()
        assertEquals(
            setOf(RecipeFormError.TitleMissing, RecipeFormError.IngredientsMissing, RecipeFormError.StepsMissing),
            (vm.uiState.value as RecipeEditUiState.Editing).errors,
        )

        vm.update {
            it.copy(title = " Crêpes ", servings = "4", ingredients = "farine\n\n  lait ", steps = "Mélanger.\nCuire.", tags = "goûter", origin = "Mamie Jeanne")
        }
        vm.save()

        val (saved, isNew) = repo.saved.single()
        assertTrue(isNew)
        assertEquals("Crêpes", saved.title)
        assertEquals("farine\nlait", saved.ingredients)
        assertEquals(4, saved.servings)
        assertEquals(listOf("goûter"), saved.tags)
        assertEquals("adult2" to "Adult2", saved.authorId to saved.authorName)
        assertEquals("Mamie Jeanne", saved.origin)
        assertEquals(RecipeEditUiState.Done("r-new"), vm.uiState.value)
    }

    @Test
    fun `a member of the family as origin replaces the free one, and parts are 1 to 50`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        val vm = RecipeEditViewModel(repo, FakeMembers(), "adult1", recipeId = "r1")
        vm.update { it.copy(servings = "60") }
        vm.save()
        assertEquals(setOf(RecipeFormError.ServingsInvalid), (vm.uiState.value as RecipeEditUiState.Editing).errors)

        vm.update { it.copy(servings = "6", origin = "Mamie Jeanne", originMemberId = "adult2") }
        vm.save()

        val (saved, isNew) = repo.saved.single()
        assertFalse(isNew)
        assertEquals("" to "adult2", saved.origin to saved.originMemberId)
    }

    @Test
    fun `only the author or the admin principal opens the form of a recipe`() = runTest {
        val vm = RecipeEditViewModel(FakeRecipes(listOf(apple)), FakeMembers(), "adult2", recipeId = "r1")
        assertEquals(RecipeEditUiState.Unavailable, vm.uiState.value)
    }

    @Test
    fun `the draft comes back after Android stopped the app`() = runTest {
        val saved = SavedStateHandle()
        val first = RecipeEditViewModel(FakeRecipes(), FakeMembers(), "adult1", recipeId = null, savedState = saved)
        first.update { it.copy(title = "Crêpes", steps = "Mélanger.\nCuire.") }

        val again = RecipeEditViewModel(FakeRecipes(), FakeMembers(), "adult1", recipeId = null, savedState = saved)

        val editing = again.uiState.value as RecipeEditUiState.Editing
        assertEquals("Crêpes" to "Mélanger.\nCuire.", editing.form.title to editing.form.steps)
        assertTrue(editing.hasChanges)
    }
}
