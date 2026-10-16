package app.hexavore.core.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Ce qu'une saisie garde pour survivre à la mort du processus ([D166][decisions]).
 *
 * Android tue volontiers une application en arrière-plan — pendant que l'appareil photo
 * du système est ouvert, c'est presque la règle — puis la recrée sur l'écran qu'on avait
 * quitté. Le `ViewModel`, lui, ne survit pas : sans cette mémoire, l'écran repartait de
 * ce que sa route lui donnait, et la saisie était perdue — une proposition d'IA payée
 * comprise, qui ne se rejoue pas.
 *
 * **Dans le `SavedStateHandle` du `ViewModel`**, et pas dans celui de l'entrée de pile :
 * ce sont deux objets différents ([D52][decisions]). C'est celui-là qu'Android range avec
 * l'écran et rend à sa recréation.
 *
 * **Quelques kilo-octets, jamais une image.** Android plafonne ce qu'une application garde
 * pour ses écrans, et le dépasser fait tout perdre : une photo se range dans un fichier,
 * et seul son chemin passe ici.
 *
 * **Une mémoire illisible ne vaut rien**, et c'est la règle qui la rend sûre : un état
 * écrit par une autre version, ou abîmé, fait repartir l'écran de sa route plutôt que de
 * le faire tomber à la réouverture.
 *
 * [decisions]: docs/11-decisions.md
 */
class FormMemory<T>(
    private val handle: SavedStateHandle,
    private val key: String,
    private val serializer: KSerializer<T>,
) {
    /** Ce que la saisie était quand le processus est mort, ou `null` s'il n'y a rien à rendre. */
    fun recall(): T? = handle.get<String>(key)?.let { text ->
        runCatching { FORMAT.decodeFromString(serializer, text) }.getOrNull()
    }

    fun keep(value: T) {
        handle[key] = FORMAT.encodeToString(serializer, value)
    }

    /** La saisie est arrivée à destination : il n'y a plus rien à rendre. */
    fun forget() {
        handle.remove<String>(key)
    }

    private companion object {
        /**
         * Un champ inconnu ne rend pas la mémoire illisible : c'est la seule différence
         * qu'une version suivante peut lui faire, et elle ne retire rien.
         */
        val FORMAT = Json { ignoreUnknownKeys = true }
    }
}
