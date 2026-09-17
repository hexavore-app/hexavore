package app.hexavore.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.hexavore.core.database.entity.DishEntity
import app.hexavore.core.database.entity.FoodEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class DiaryDaoTest {
    private lateinit var database: HexavoreDatabase

    @Before
    fun ouvrir() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HexavoreDatabase::class.java,
        ).build()
    }

    @After
    fun fermer() {
        database.close()
    }

    @Test
    fun `un plat ressort avec ses lignes, dans l ordre de saisie`() = runBlocking {
        database.diaryDao().saveDish(dish(), listOf(entry("Riz"), entry("Poulet")))

        val jour = database.diaryDao().observeDay(JOUR).first()

        assertEquals(1, jour.size)
        assertEquals(2, jour.single().entries.size)
    }

    @Test
    fun `une valeur inconnue ressort nulle et non a zero`() = runBlocking {
        // Le seul endroit ou la distinction peut se perdre sans que rien ne le
        // signale : un NOT NULL DEFAULT 0 sur ces colonnes fausserait des mois de
        // journal en silence.
        database.diaryDao().saveDish(dish(), listOf(entry("Sauce", fiberG = null)))

        val ligne = database.diaryDao().observeDay(JOUR).first().single().entries.single()

        assertNull("des fibres non renseignees ne sont pas zero gramme de fibres", ligne.fiberG)
        assertEquals(1.0, ligne.proteinG)
    }

    @Test
    fun `une journee sans plat rend une liste vide`() = runBlocking {
        val jour = database.diaryDao().observeDay(JOUR).first()

        assertTrue("rien de note ne doit pas produire un plat a zero", jour.isEmpty())
    }

    @Test
    fun `supprimer un plat emporte ses lignes`() = runBlocking {
        database.diaryDao().saveDish(dish(), listOf(entry("Riz")))

        database.openHelper.writableDatabase.execSQL("DELETE FROM dish WHERE id = '$DISH_ID'")

        assertNull(database.diaryDao().dish(DISH_ID))
        assertTrue(database.diaryDao().observeDay(JOUR).first().isEmpty())
    }

    @Test
    fun `reecrire un plat remplace ses lignes`() = runBlocking {
        database.diaryDao().saveDish(dish(), listOf(entry("Riz"), entry("Poulet")))

        database.diaryDao().saveDish(dish(), listOf(entry("Riz complet")))

        val plat = database.diaryDao().observeDay(JOUR).first().single()
        assertEquals(1, plat.entries.size)
        assertEquals("Riz complet", plat.entries.single().displayName)
    }

    @Test
    fun `reecrire un plat lui laisse sa date de premiere ecriture`() = runBlocking {
        // created_at qui devient egal a updated_at des la premiere correction est un
        // created_at qui ne dit plus rien -- or c'est lui qui rendra une fusion de
        // sauvegardes possible.
        database.diaryDao().saveDish(dish(), listOf(entry("Riz", id = "ligne-fixe")))

        database.diaryDao().saveDish(dish(updatedAt = PLUS_TARD), listOf(entry("Riz", id = "ligne-fixe")))

        val plat = database.diaryDao().dish(DISH_ID)!!
        assertEquals("le plat garde sa date de creation", INSTANT, plat.dish.createdAt)
        assertEquals("la ligne aussi", INSTANT, plat.entries.single().createdAt)
    }

    // --- Decor ---------------------------------------------------------------

    private fun dish(updatedAt: Long = INSTANT) = DishEntity(
        id = DISH_ID,
        date = JOUR,
        source = "MANUAL",
        loggedAt = INSTANT,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private var suivant = 0

    private fun entry(nom: String, fiberG: Double? = 2.0, id: String = "ligne-${suivant++}") = FoodEntryEntity(
        id = id,
        dishId = DISH_ID,
        // Une ligne tapee a la main ne vient d'aucune fiche, et c'est le cas de
        // toutes celles de ce test.
        foodId = null,
        displayName = nom,
        quantity = 100.0,
        unit = "g",
        grams = 100.0,
        kcal = 120.0,
        proteinG = 1.0,
        carbG = 20.0,
        sugarG = 3.0,
        fatG = 4.0,
        fiberG = fiberG,
        createdAt = INSTANT,
        updatedAt = INSTANT,
    )

    private companion object {
        const val JOUR = "2026-03-15"
        const val DISH_ID = "plat-1"
        const val INSTANT = 1_773_000_000_000L
        const val PLUS_TARD = 1_773_009_999_000L
    }
}
