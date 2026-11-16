package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeComment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Recipes live in `recipes/{recipeId}`. See docs/data-model.md for the fields and the rights. */
class FirestoreRecipeRepository(private val db: FirebaseFirestore) : RecipeRepository {

    override fun observeRecipes(): Flow<List<Recipe>> = callbackFlow {
        val registration = db.collection(RECIPES).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toRecipe() }.sortedWith(recipeOrder))
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeRecipe(id: String): Flow<Recipe?> = callbackFlow {
        val registration = db.collection(RECIPES).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.takeIf { it.exists() }?.toRecipe())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getRecipe(id: String): Recipe? =
        db.collection(RECIPES).document(id).get().await().takeIf { it.exists() }?.toRecipe()

    override fun newRecipeId(): String = db.collection(RECIPES).document().id

    override suspend fun saveRecipe(recipe: Recipe, isNew: Boolean) {
        val fields = mapOf(
            "title" to recipe.title,
            "origin" to recipe.origin,
            "originMemberId" to recipe.originMemberId,
            "servings" to recipe.servings?.toLong(),
            "ingredients" to recipe.ingredients,
            "steps" to recipe.steps,
            "tags" to recipe.tags,
            "authorId" to recipe.authorId,
            "authorName" to recipe.authorName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = db.collection(RECIPES).document(recipe.id)
        if (isNew) {
            ref.set(fields + ("createdAt" to FieldValue.serverTimestamp())).await()
        } else {
            ref.set(fields, SetOptions.merge()).await()
        }
    }

    override suspend fun deleteRecipe(id: String) {
        db.collection(RECIPES).document(id).delete().await()
    }

    override fun observeComments(recipeId: String): Flow<List<RecipeComment>> = callbackFlow {
        val registration = comments(recipeId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toComment() }.sortedWith(recipeCommentOrder))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addComment(recipeId: String, text: String, me: Member) {
        comments(recipeId).document().set(
            mapOf(
                "text" to text,
                "authorId" to me.id,
                "authorName" to me.firstName,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun deleteComment(recipeId: String, commentId: String) {
        comments(recipeId).document(commentId).delete().await()
    }

    private fun comments(recipeId: String) = db.collection(RECIPES).document(recipeId).collection(COMMENTS)

    private fun DocumentSnapshot.toComment(): RecipeComment? {
        val text = getString("text") ?: return null
        return RecipeComment(
            id = id,
            text = text,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
        )
    }

    private fun DocumentSnapshot.toRecipe(): Recipe? {
        val title = getString("title") ?: return null
        return Recipe(
            id = id,
            title = title,
            origin = getString("origin").orEmpty(),
            originMemberId = getString("originMemberId"),
            servings = getLong("servings")?.toInt(),
            ingredients = getString("ingredients").orEmpty(),
            steps = getString("steps").orEmpty(),
            tags = (get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
        )
    }

    private companion object {
        const val RECIPES = "recipes"
        const val COMMENTS = "comments"
    }
}
