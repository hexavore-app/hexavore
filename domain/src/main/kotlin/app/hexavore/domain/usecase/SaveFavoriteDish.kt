package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.FavoriteComponent
import app.hexavore.domain.diary.FavoriteDish
import app.hexavore.domain.diary.FavoriteDishId
import app.hexavore.domain.diary.FavoriteDishes
import app.hexavore.domain.identity.IdGenerator

/**
 * Ce qu'a donné une mise en favori.
 *
 * Un type de retour plutôt qu'une exception : un nom déjà pris n'est pas une panne,
 * c'est une réponse. L'écran la traduit en phrase et laisse le champ ouvert, là où une
 * exception aurait obligé à distinguer, dans un `runCatching`, ce qui se corrige de ce
 * qui se réessaie.
 */
sealed interface FavoriteOutcome {
    data class Saved(val id: FavoriteDishId) : FavoriteOutcome

    /** Un autre favori porte déjà ce nom, aux accents et à la casse près. */
    data object NameTaken : FavoriteOutcome
}

/**
 * Enregistre un brouillon comme plat favori.
 *
 * **Le favori est un modèle, pas une copie du journal.** Chaque ligne y entre avec la
 * fiche dont elle vient quand elle en vient d'une, pour que « mes flocons du matin »
 * reflète la fiche courante au prochain rejeu ([docs/07][modele]) ; ses valeurs sont
 * enregistrées en plus, comme contenu d'une ligne tapée à la main et comme repli le
 * jour où la fiche citée aura disparu ([D62][decisions]).
 *
 * **Sauf pour une ligne corrigée à la main**, qui entre **déliée** de sa fiche. Le
 * modèle vivant est une bonne règle tant que la fiche dit vrai ; celui qui a complété
 * lui-même les valeurs d'un aliment mal renseigné a précisément dit le contraire, et
 * rejouer la fiche lui reprendrait son travail sans prévenir.
 *
 * **Le nom est unique.** Deux « Petit-déj » dans une liste ne se distinguent plus, et
 * choisir devient un pari. La vérification est faite ici, et la base la double d'un
 * index : une règle d'unicité tenue par la seule discipline d'écriture n'en est pas une.
 *
 * [modele]: docs/07-modele-de-donnees.md
 * [decisions]: docs/11-decisions.md
 */
class SaveFavoriteDish(private val favorites: FavoriteDishes, private val ids: IdGenerator) {
    /**
     * @param existing le favori qu'on renomme, ou `null` pour en créer un.
     * @throws IllegalArgumentException si le brouillon n'a aucune ligne complète : un
     *   favori sans contenu ne rejouerait rien.
     */
    suspend operator fun invoke(draft: EntryDraft, name: String, existing: FavoriteDishId? = null): FavoriteOutcome {
        require(draft.lines.isNotEmpty() && draft.lines.all { it.complete }) {
            "Brouillon incomplet : un favori sans ligne enregistrable ne rejouerait rien."
        }

        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Un favori sans nom serait introuvable." }

        return if (favorites.nameTaken(trimmed, excluding = existing)) {
            FavoriteOutcome.NameTaken
        } else {
            val id = existing ?: FavoriteDishId(ids.next())
            favorites.save(FavoriteDish(id = id, name = trimmed, components = draft.toComponents()))
            FavoriteOutcome.Saved(id)
        }
    }
}

/**
 * Les lignes du brouillon, sous la forme qu'un favori conserve.
 *
 * Ni identifiant de ligne, ni identifiant d'entrée de journal : un favori n'est pas
 * une saisie, et rejouer le même favori deux fois doit produire deux plats distincts.
 * Les traîner ici aurait fait écrire le second par-dessus le premier.
 */
private fun EntryDraft.toComponents(): List<FavoriteComponent> = lines.map { line ->
    FavoriteComponent(
        // **Une ligne corrigee a la main se delie de sa fiche.** Le favori est un
        // modele vivant : une ligne qui cite une fiche se rejoue depuis la fiche
        // courante, et corriger ses flocons corrige tous les petits-dejeuners a venir
        // (D62). Mais celui qui a complete lui-meme les valeurs d'une fiche
        // incomplete -- la feta, les capres, une ligne proposee par un modele --
        // n'attend pas qu'on les lui reprenne au rejeu. Sa correction gagne, et le
        // lien tombe : c'est la meme regle que `edited` fait deja respecter au
        // recalcul, appliquee au rejeu.
        foodId = line.foodId.takeIf { line.edited.isEmpty() },
        name = line.name.trim(),
        quantity = checkNotNull(line.quantity) { "Ligne sans quantite : ${line.name}" },
        unit = line.unit,
        grams = checkNotNull(line.grams) { "Ligne sans quantite : ${line.name}" },
        values = line.values,
    )
}

/**
 * Réécrit un favori existant, **sans enregistrer de repas**.
 *
 * C'est la seconde vie de l'écran de validation : il sert d'éditeur au modèle
 * lui-même. Le plat n'entre pas au journal — on est venu corriger un modèle, pas noter
 * un repas —, et le nom du favori ne change pas : on le modifie depuis sa liste, où
 * il est déjà nommé.
 *
 * **Les plats déjà enregistrés qui citaient ce favori le perdent.** Pas de
 * répercussion en chaîne : leurs lignes ne bougent pas d'un gramme. Ce qui tombe est
 * la **provenance** — « rejoué depuis les Flocons du matin » n'est plus vérifiable
 * quand les Flocons du matin ont changé de contenu. Un lien qui ment vaut moins qu'un
 * lien absent.
 */
class UpdateFavoriteDish(private val favorites: FavoriteDishes, private val diary: DiaryRepository) {
    /**
     * @return `null` si le favori a disparu entre l'ouverture et l'enregistrement.
     * @throws IllegalArgumentException si le brouillon n'a aucune ligne enregistrable :
     *   un favori vidé ne rejouerait rien, et ce n'est pas ainsi qu'on le supprime.
     */
    suspend operator fun invoke(draft: EntryDraft, id: FavoriteDishId): FavoriteDish? {
        require(draft.lines.isNotEmpty() && draft.lines.all { it.complete }) {
            "Brouillon incomplet : un favori sans ligne enregistrable ne rejouerait rien."
        }

        val existing = favorites.byId(id) ?: return null
        favorites.save(existing.copy(components = draft.toComponents()))
        diary.unlinkFavorite(id)

        return existing
    }
}

/**
 * Le nom à proposer pour un favori, à partir du titre du plat.
 *
 * **Le titre du plat est déjà le nom qu'on cherche.** L'utilisateur l'a sous les yeux
 * depuis l'accueil — « Déjeuner », « Poke bowl » —, et c'est celui qu'il reconnaîtra
 * dans une liste de modèles. Les deux propositions précédentes ne valaient rien à
 * côté : la liste des aliments du plat donnait des titres de cinquante caractères
 * qu'on efface au lieu de les corriger, et « Plat 3 » ne disait rien de rien.
 *
 * **Le nom d'un favori est unique** ([SaveFavoriteDish]), et « Déjeuner » a de bonnes
 * chances d'être déjà pris. Le rang suit donc le même principe que l'ancien numéro :
 * on avance jusqu'au premier libre, et c'est l'appelant qui écrit « Déjeuner 2 » —
 * l'espace, la forme du nombre et la langue sont des questions d'interface.
 *
 * @param numbered comment l'écran écrit le n-ième plat d'un même nom.
 */
class ProposeFavoriteName(private val favorites: FavoriteDishes) {
    /** @return un nom libre, ou [base] tel quel si la lecture des favoris échoue. */
    suspend operator fun invoke(base: String, numbered: (base: String, rank: Int) -> String): String {
        val trimmed = base.trim()
        if (trimmed.isEmpty() || !favorites.nameTaken(trimmed)) return trimmed

        var rank = SECOND
        // La borne evite une boucle sans fin si quelque chose repondait « pris » a
        // tout ; au-dela de cent, le nom propose n'est de toute facon plus le sujet.
        while (rank < MAX_ATTEMPTS && favorites.nameTaken(numbered(trimmed, rank))) {
            rank++
        }
        return numbered(trimmed, rank)
    }
}

/** Le premier rang qui s'écrit : le plat sans numéro est le premier. */
private const val SECOND = 2

/** Cent noms déjà pris d'affilée : au-delà, le nom proposé n'est plus le sujet. */
private const val MAX_ATTEMPTS = 100
