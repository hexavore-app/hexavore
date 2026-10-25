package app.hexavore.domain.opening

/**
 * Ce que l'accueil peut avoir à dire en s'ouvrant ([D179][decisions]) : une astuce, ou la
 * demande d'avis.
 *
 * ### Un message, et non une pastille
 *
 * Une pastille désigne une situation et s'éteint quand elle cesse ; un message se lit une
 * fois, et se ferme. Les deux ne se confondent pas : une pastille qu'on éteint en la
 * lisant serait une notification déguisée, un message qui reviendrait tant qu'on n'a pas
 * agi serait une pastille qui crie.
 *
 * ### Un champ ouvert
 *
 * Deux sortes aujourd'hui. Une troisième — les nouveautés d'une version, par exemple —
 * s'ajoute ici sans toucher au rythme qui les espace : c'est le `when` exhaustif de
 * l'écran qui dira qu'il lui manque un texte.
 *
 * [decisions]: docs/11-decisions.md
 */
sealed interface OpeningMessage {
    /** Une astuce du catalogue, montrée une fois. */
    data class Tip(val tip: OpeningTip) : OpeningMessage

    /**
     * La demande d'avis : cinq étoiles, et **la fiche du Play Store derrière chacune**.
     *
     * Quelle que soit l'étoile touchée, c'est la fiche qui s'ouvre, et c'est là qu'on note.
     * Trier les notes — les bonnes vers le Play Store, les mauvaises vers un courriel —
     * est courant, et contraire aux consignes de Google sur les avis : l'application ne
     * choisit pas qui a le droit de se faire entendre.
     */
    data object Rating : OpeningMessage
}

/**
 * La fiche d'Hexavore sur le Play Store, là où l'on note l'application.
 *
 * **L'identifiant de la version publiée**, jamais celui de la variante en cours : la
 * variante de mise au point porte le sien, et n'a pas de fiche. Une adresse `https`
 * plutôt que `market://` : le Play Store la reconnaît comme sienne et s'ouvre dessus, et
 * un téléphone sans lui l'ouvre dans le navigateur au lieu de ne rien faire.
 *
 * Écrite une fois, ici, pour la demande d'avis et pour l'écran de contact : deux adresses
 * recopiées finissent par différer, et c'est la seconde que personne ne relève.
 */
const val STORE_LISTING: String = "https://play.google.com/store/apps/details?id=app.hexavore"

/**
 * Les astuces, **dans l'ordre où elles se proposent**.
 *
 * Du plus utile au plus anecdotique : ce qui change la façon de noter d'abord — la clé
 * qui manque, le quartier qui s'explique, la photo qui se précise —, ce qui se découvre
 * de toute façon ensuite. Chacune ne paraît **qu'une fois**, et une astuce qui ne
 * s'applique pas — la photo sans clé, la clé quand on en a une — passe son tour sans
 * être perdue.
 *
 * **Rien de ce qu'elles disent n'est ici** : le domaine ne connaît pas les ressources. Le
 * texte vit à l'écran, derrière un `when` sans branche `else` — une astuce ajoutée sans
 * son texte ne compile pas.
 */
enum class OpeningTip(val audience: TipAudience = TipAudience.EVERYONE) {
    /** Pas encore de clé : la phrase et la photo sont à deux minutes, gratuitement. */
    FREE_KEY(TipAudience.WITHOUT_KEY),

    /** Un quartier de l'hexagone dit ce qui l'a rempli. */
    QUARTER_SOURCES,

    /** Sous une photo, un mot précise l'assiette. */
    PHOTO_NOTE(TipAudience.WITH_KEY),

    /** L'appui long sur un plat : copier, mettre en favori, supprimer. */
    DISH_MENU,

    /** Un plat habituel se rejoue depuis les favoris. */
    FAVOURITES,

    /** Un jour du calendrier rattrape un repas oublié. */
    CATCH_UP,

    /** Un balayage de côté change de jour. */
    SWIPE_DAYS,

    /** Ce que l'IA a estimé se signale sur la ligne. */
    ESTIMATES(TipAudience.WITH_KEY),

    /** La recherche ignore les accents. */
    ACCENTS,

    /** Un produit scanné une fois reste disponible sans réseau. */
    BARCODE_OFFLINE,

    /** Une pesée par semaine fait la tendance, et l'ajustement. */
    WEIGH_IN,

    /** Un rappel prévient quand un repas manque. */
    REMINDERS,

    /** Tout s'exporte dans un fichier. */
    BACKUP,
}

/** À qui une astuce s'adresse. */
enum class TipAudience {
    EVERYONE,

    /** Ce qui ne sert qu'avec une clé : la photo, l'estimation. */
    WITH_KEY,

    /** Ce qui ne sert qu'à qui n'en a pas encore. */
    WITHOUT_KEY,
    ;

    fun includes(aiConfigured: Boolean): Boolean = when (this) {
        EVERYONE -> true
        WITH_KEY -> aiConfigured
        WITHOUT_KEY -> !aiConfigured
    }
}

/**
 * Ce qu'on a répondu à un message.
 *
 * **Trois réponses closent la demande d'avis pour de bon** : une étoile touchée, « J'ai
 * déjà noté », « Ne plus demander ». On ne sait pas si la note a été laissée une fois la
 * fiche ouverte, et ce n'est pas à l'application de le vérifier : redemander à quelqu'un
 * qui a fait le geste serait le punir de l'avoir fait.
 */
enum class OpeningAnswer {
    /** L'astuce est lue, ou la boîte refermée. */
    SEEN,

    /** « Ne plus afficher d'astuces ». Se rallume dans les réglages. */
    NO_MORE_TIPS,

    /** Une étoile touchée : la fiche du Play Store s'est ouverte. */
    RATED,

    /** « J'ai déjà noté ». */
    ALREADY_RATED,

    /** « Ne plus demander ». */
    NEVER,

    /** « Plus tard », ou la demande d'avis refermée sans réponse. */
    LATER,
}
