package fr.familia.app.feature.recipes

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotosUiState
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.data.recipeOrder
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeCategory
import fr.familia.app.core.model.RecipeComment
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    val comments = MutableStateFlow<List<RecipeComment>>(emptyList())
    override fun observeComments(recipeId: String): Flow<List<RecipeComment>> = comments
    override suspend fun addComment(recipeId: String, text: String, me: Member, mentionIds: List<String>) {
        comments.value = comments.value + RecipeComment("c${comments.value.size + 1}", text, me.id, me.firstName, null, mentionIds)
    }
    override suspend fun deleteComment(recipeId: String, commentId: String) {
        comments.value = comments.value.filterNot { it.id == commentId }
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
    fun `a first name finds the recipes the member gave or noted, and a free origin too`() {
        val family = TestFamily.family
        val byAdult2 = soup.copy(authorId = "adult2", authorName = "Adult2", originMemberId = null)
        val fromGranny = apple.copy(origin = "Mamie Rose")
        assertTrue(matches(soup, "Adult2", family))
        assertTrue(matches(byAdult2, "adult2 potiron", family))
        assertFalse(matches(byAdult2, "adult1", family))
        assertTrue(matches(apple, "adult1", family))
        assertTrue(matches(fromGranny, "rose", family))
        assertTrue(matches(soup.copy(authorId = "gone", authorName = "Ancien"), "ancien", family))
    }

    @Test
    fun `tags are trimmed, without doubles, six at most`() {
        assertEquals(listOf("dessert", "Noël"), parseTags(" dessert, Noël , Dessert,, "))
        assertEquals(6, parseTags("a,b,c,d,e,f,g").size)
    }

    @Test
    fun `the book is grouped by category, by title inside, and filtered as one types`() = runTest {
        val tart = recipe("r3", "Tarte aux poires").copy(category = RecipeCategory.Dessert)
        val all = listOf(soup.copy(category = RecipeCategory.Entree), apple.copy(category = RecipeCategory.Dessert), tart)
        val vm = RecipesViewModel(FakeRecipes(all), FakeMembers(), currentMemberId = "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value as RecipesUiState.Ready
        assertEquals(listOf(RecipeCategory.Entree, RecipeCategory.Dessert), ready.groups.map { it.category })
        assertEquals(listOf("r1", "r3"), ready.groups[1].rows.map { it.recipe.id })
        assertTrue(ready.canAdd)

        vm.search("potiron")

        val found = vm.uiState.value as RecipesUiState.Ready
        assertEquals(listOf("r2"), found.rows.map { it.recipe.id })
        assertEquals("Adult2", found.rows.single().origin)
        assertEquals(3, found.total)
    }

    @Test
    fun `the categories keep the order of the book, an empty one has no group, and the old recipes are in Divers`() {
        val rows = listOf(
            RecipeRow(recipe("a", "Sirop"), null),
            RecipeRow(recipe("b", "Mojito").copy(category = RecipeCategory.Boisson), null),
            RecipeRow(recipe("c", "Olives").copy(category = RecipeCategory.Aperitif), null),
        )
        assertEquals(
            listOf(RecipeCategory.Aperitif, RecipeCategory.Boisson, RecipeCategory.Divers),
            groupByCategory(rows).map { it.category },
        )
        assertEquals(RecipeCategory.Divers, RecipeCategory.fromKey(null))
        assertEquals(RecipeCategory.Plat, RecipeCategory.fromKey("plat"))
    }

    @Test
    fun `without an origin a recipe says who noted it`() {
        val family = TestFamily.family
        assertEquals("Adult1", fromLabel(apple, family))
        assertEquals("Mamie Rose", fromLabel(apple.copy(origin = "Mamie Rose"), family))
    }

    @Test
    fun `a member's list searches without first names`() {
        val family = TestFamily.family
        assertTrue(matches(apple, "adult1", family))
        assertFalse(matches(apple, "adult1", family, withNames = false))
        assertTrue(matches(apple, "pommes dessert", family, withNames = false))
    }

    @Test
    fun `only the author manages a recipe, not the admin principal, and its removal closes it`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        fun detail(me: String) = RecipeDetailViewModel(repo, FakeMembers(), me, "r1").also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
        assertFalse((detail("adult2").uiState.value as RecipeDetailUiState.Ready).canManage)
        assertFalse((detail("admin1").uiState.value as RecipeDetailUiState.Ready).canManage)
        val mine = detail("adult1")

        mine.remove()

        assertEquals(RecipeDetailUiState.Removed, mine.uiState.value)
        assertTrue(repo.recipes.value.isEmpty())
    }

    @Test
    fun `a recipe without origin says « De » who noted it, and names its author by today's first name`() = runTest {
        val repo = FakeRecipes(listOf(apple.copy(authorName = "Ancien prénom")))
        val vm = RecipeDetailViewModel(repo, FakeMembers(), "adult2", "r1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value as RecipeDetailUiState.Ready
        assertEquals("Adult1" to "Adult1", ready.origin to ready.authorName)
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
    fun `the little story is kept, 600 characters at most (récit 47)`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        val vm = RecipeEditViewModel(repo, FakeMembers(), "adult1", recipeId = "r1")
        vm.update { it.copy(story = "x".repeat(601)) }
        vm.save()
        assertEquals(setOf(RecipeFormError.StoryTooLong), (vm.uiState.value as RecipeEditUiState.Editing).errors)

        vm.update { it.copy(story = "  Le gâteau des dimanches.  ") }
        vm.save()

        val saved = repo.saved.single().first
        assertEquals("Le gâteau des dimanches.", saved.story)
    }

    @Test
    fun `only the author opens the form of a recipe`() = runTest {
        val vm = RecipeEditViewModel(FakeRecipes(listOf(apple)), FakeMembers(), "adult2", recipeId = "r1")
        assertEquals(RecipeEditUiState.Unavailable, vm.uiState.value)
        val admin = RecipeEditViewModel(FakeRecipes(listOf(apple)), FakeMembers(), "admin1", recipeId = "r1")
        assertEquals(RecipeEditUiState.Unavailable, admin.uiState.value)
    }

    @Test
    fun `an edit keeps the category of the recipe`() = runTest {
        val repo = FakeRecipes(listOf(apple.copy(category = RecipeCategory.Dessert)))
        val vm = RecipeEditViewModel(repo, FakeMembers(), "adult1", recipeId = "r1")
        vm.update { it.copy(title = "Tarte aux pommes") }

        vm.save()

        assertEquals(RecipeCategory.Dessert, repo.saved.single().first.category)
    }

    @Test
    fun `a category is read by its key, and an old or unknown one is Divers`() {
        assertEquals(RecipeCategory.Accompagnement, RecipeCategory.fromKey("accompagnement"))
        assertEquals(RecipeCategory.Divers, RecipeCategory.fromKey(null))
        assertEquals(RecipeCategory.Divers, RecipeCategory.fromKey("Plat"))
        assertEquals(8, RecipeCategory.entries.map { it.key }.toSet().size)
        assertEquals(RecipeCategory.Divers, apple.category)
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

    @Test
    fun `only the author adds the photos of the dish, 3 places each`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        assertTrue(recipePhotoRights(repo, FakeMembers(), "r1", "adult1").first().canAdd)
        assertFalse(recipePhotoRights(repo, FakeMembers(), "r1", "admin1").first().canManage)
        assertFalse(recipePhotoRights(repo, FakeMembers(), "r1", "adult2").first().canAdd)
        assertEquals(null, recipePhotoRights(repo, FakeMembers(), "gone", "adult1").first().me)

        val one = AlbumPhoto(recipePhotoFolder("r1"), "adult1", "Adult1", 1, null)
        assertEquals(2, AlbumPhotosUiState(listOf(one), "adult1", canAdd = true, maxPerMember = RECIPE_PHOTOS).placesLeft)
        assertEquals(listOf(2, 3), AlbumPhoto.freeSlots(listOf(1), RECIPE_PHOTOS))
    }

    @Test
    fun `a member comments, and only the right people may remove a comment`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        val saved = SavedStateHandle()
        val vm = RecipeCommentsViewModel(repo, FakeMembers(), "adult2", "r1", savedState = saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.type("  Avec des poires aussi  ")
        assertEquals("  Avec des poires aussi  ", saved.get<String>("recipe_comment_draft"))
        vm.send()

        val mine = vm.uiState.value.comments.single()
        assertEquals("Avec des poires aussi" to "adult2", mine.comment.text to mine.comment.authorId)
        assertTrue(mine.canRemove)
        assertEquals("", vm.uiState.value.draft)

        // adult1 wrote the recipe, so may remove it; kid1 may not.
        val author = RecipeCommentsViewModel(repo, FakeMembers(), "adult1", "r1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { author.uiState.collect {} }
        assertTrue(author.uiState.value.comments.single().canRemove)
        val kid = RecipeCommentsViewModel(repo, FakeMembers(), "kid1", "r1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { kid.uiState.collect {} }
        assertFalse(kid.uiState.value.comments.single().canRemove)

        author.remove(author.uiState.value.comments.single())
        assertTrue(repo.comments.value.isEmpty())
    }

    @Test
    fun `an empty comment is never sent`() = runTest {
        val repo = FakeRecipes(listOf(apple))
        val vm = RecipeCommentsViewModel(repo, FakeMembers(), "adult2", "r1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.type("   ")
        vm.send()
        assertTrue(repo.comments.value.isEmpty())
    }

    @Test
    fun `the list from a profile keeps the recipes that member noted, without « Nouvelle recette » unless it is mine`() = runTest {
        val byAdult2 = recipe("r3", "Riz au lait").copy(authorId = "adult2", authorName = "Adult2")
        val fake = FakeRecipes(listOf(soup, apple, byAdult2))
        val vm = RecipesViewModel(fake, FakeMembers(), memberId = "adult2", currentMemberId = "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value as RecipesUiState.Ready
        // The soup only comes from adult2: adult1 noted it, so it stays on adult1's list.
        assertEquals(listOf("r3"), ready.rows.map { it.recipe.id })
        assertEquals("Adult2" to 1, ready.memberName to ready.total)
        assertFalse(ready.canAdd)
        assertFalse(ready.isMine)

        val mine = RecipesViewModel(fake, FakeMembers(), memberId = "adult1", currentMemberId = "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { mine.uiState.collect {} }
        val myList = mine.uiState.value as RecipesUiState.Ready
        assertEquals(setOf("r1", "r2"), myList.rows.map { it.recipe.id }.toSet())
        assertTrue(myList.canAdd && myList.isMine)
    }

    @Test
    fun `the shared text has the title, the origin, the ingredients and the numbered steps, no photo`() {
        val labels = RecipeShareLabels("Ingrédients", "Étapes", from = { "De $it" }, servings = { "Pour $it personnes" })
        val text = recipeShareText(apple.copy(servings = 6, steps = "Couper.\nCuire."), "Mamie Jeanne", labels)
        assertEquals(
            "Gâteau aux pommes\nDe Mamie Jeanne\nPour 6 personnes\n\nIngrédients\n• 4 pommes\n• farine\n\nÉtapes\n1. Couper.\n2. Cuire.",
            text,
        )
    }
}
