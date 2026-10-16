package app.hexavore.core.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * La mémoire d'une saisie à travers la mort du processus ([D166][decisions]).
 *
 * Le `SavedStateHandle` partagé entre deux mémoires tient le rôle de ce qu'Android range
 * avec l'écran et lui rend à sa recréation.
 *
 * [decisions]: docs/11-decisions.md
 */
class FormMemoryTest {
    private val handle = SavedStateHandle()

    @Test
    fun `ce qui est garde se relit apres la recreation`() {
        FormMemory(handle, CLE, Saisie.serializer()).keep(SAISIE)

        assertEquals(SAISIE, FormMemory(handle, CLE, Saisie.serializer()).recall())
    }

    @Test
    fun `sans rien de garde, il n y a rien a rendre`() {
        assertNull(FormMemory(handle, CLE, Saisie.serializer()).recall())
    }

    @Test
    fun `une memoire illisible ne vaut rien`() {
        // Un etat abime, ou ecrit par une autre version : l'ecran repart de sa route
        // plutot que de tomber a la reouverture.
        handle[CLE] = "{ pas du json"

        assertNull(FormMemory(handle, CLE, Saisie.serializer()).recall())
    }

    @Test
    fun `un champ inconnu ne rend pas la memoire illisible`() {
        handle[CLE] = """{"nom":"Riz","grammes":"180","ajoutPlusTard":true}"""

        assertEquals(SAISIE, FormMemory(handle, CLE, Saisie.serializer()).recall())
    }

    @Test
    fun `une saisie arrivee a destination est oubliee`() {
        val memoire = FormMemory(handle, CLE, Saisie.serializer())
        memoire.keep(SAISIE)

        memoire.forget()

        assertNull(memoire.recall())
    }

    @Test
    fun `la memoire ne touche pas aux arguments de la route`() {
        // Le meme SavedStateHandle porte les arguments de navigation : la memoire n'en
        // ecrase aucun, et les oublier ne les emporte pas.
        handle["dishId"] = "plat-1"
        val memoire = FormMemory(handle, CLE, Saisie.serializer())

        memoire.keep(SAISIE)
        memoire.forget()

        assertEquals("plat-1", handle.get<String>("dishId"))
    }

    @Serializable
    private data class Saisie(val nom: String, val grammes: String)

    private companion object {
        const val CLE = "hexavore.test.saisie"
        val SAISIE = Saisie(nom = "Riz", grammes = "180")
    }
}
