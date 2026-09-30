package app.hexavore.domain.profile

import java.time.LocalDate
import java.time.Period

/**
 * Le sexe, tel que la formule de Mifflin-St Jeor le demande.
 *
 * Trois valeurs pour deux variantes de formule : [UNSPECIFIED] applique la **moyenne
 * des deux**, soit un terme constant à −78. Ce n'est pas un repli technique, c'est une
 * réponse — l'écran d'accueil du profil le dit en toutes lettres, parce qu'un chiffre
 * inexpliqué est un chiffre auquel on ne fait pas confiance ([docs/02][parcours]).
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 */
enum class Sex(val bmrConstant: Double) {
    MALE(MALE_CONSTANT),
    FEMALE(FEMALE_CONSTANT),
    UNSPECIFIED((MALE_CONSTANT + FEMALE_CONSTANT) / 2),
}

private const val MALE_CONSTANT = 5.0
private const val FEMALE_CONSTANT = -161.0

/**
 * Ce que le **travail** demande au corps, dans une journée ordinaire.
 *
 * ### Pourquoi le travail est séparé du sport
 *
 * Une seule question — « quel est votre niveau d'activité » — mélange deux choses qui
 * n'ont ni la même nature ni la même ampleur ([D137][decisions]). Un maçon qui ne fait
 * aucun sport dépense davantage qu'un cadre qui court trois fois par semaine, et les
 * deux se reconnaissaient dans « modérément actif ». Chacun choisissait alors au
 * jugé, et l'objectif calculé valait ce que valait ce jugement.
 *
 * Le travail occupe huit heures par jour, cinq jours sur sept : c'est le **socle**, et
 * il ne se devine pas depuis un nombre de séances. Le sport s'y ajoute.
 *
 * Les libellés vivent dans les ressources d'un écran — `:domain` ne connaît pas
 * Android — mais le **facteur** est une règle nutritionnelle et il est ici.
 *
 * [decisions]: docs/11-decisions.md
 */
enum class WorkActivity(val factor: Double) {
    /** Assis la majeure partie du temps : bureau, conduite, études. */
    DESK(DESK_FACTOR),

    /** Debout et en mouvement sans port de charge : vente, service, enseignement. */
    ON_FEET(ON_FEET_FACTOR),

    /** Effort physique soutenu : bâtiment, manutention, agriculture. */
    PHYSICAL(PHYSICAL_FACTOR),
}

/**
 * Le sport, compté en **séances par semaine**.
 *
 * Un nombre plutôt qu'un adjectif : « trois fois par semaine » se répond en une
 * seconde et ne demande de se comparer à personne. Au-delà de [MAX_SESSIONS], l'écart
 * entre six et sept séances ne change presque rien au besoin calculé, et prétendre le
 * contraire donnerait une précision que la formule ne tient pas.
 *
 * **L'exercice reste intégré au multiplicateur, jamais ajouté au jour le jour.** Un
 * utilisateur qui note ses séances finit par « manger ses calories brûlées » : les
 * montres surévaluent la dépense de 20 à 90 %. Un multiplicateur stable est moins
 * précis un jour donné, plus juste sur un mois ([docs/03][calculs]).
 *
 * [calculs]: docs/03-nutrition-calculs.md
 */
@JvmInline
value class WeeklySessions(val count: Int) {
    init {
        require(count in 0..MAX_SESSIONS) { "Des séances hors de la plage : $count." }
    }

    companion object {
        /** Aucune séance : la valeur de départ, et celle de la plupart des gens. */
        val NONE = WeeklySessions(0)

        const val MAX_SESSIONS = 5
    }
}

/**
 * Ce que le corps dépense au-delà du repos, travail et sport réunis.
 *
 * **Le travail donne le socle, chaque séance ajoute** ([D137][decisions]) : c'est la
 * forme la plus simple qui dise quelque chose de vrai — deux personnes au même poste
 * se distinguent par leur sport, et deux sportifs égaux par leur métier.
 *
 * **Le plafond n'est pas décoratif.** Au-delà de 1,90, la formule de Mifflin-St Jeor
 * sort du domaine où elle a été validée, et l'objectif calculé cesserait d'être un
 * objectif pour devenir une extrapolation.
 *
 * [decisions]: docs/11-decisions.md
 */
data class Activity(val work: WorkActivity, val sessions: WeeklySessions = WeeklySessions.NONE) {
    val factor: Double
        get() = (work.factor + sessions.count * PER_SESSION).coerceAtMost(MAX_FACTOR)

    companion object {
        /**
         * Le couple qui rend le mieux un ancien niveau unique.
         *
         * **Choisi sur le facteur, pas sur le mot** ([D137][decisions]) : chaque
         * traduction retombe à moins de deux centièmes de l'ancienne valeur, donc
         * l'objectif calculé ne bouge pas de façon perceptible. « Actif » devient un
         * métier physique avec deux séances plutôt qu'un bureau avec dix, parce que
         * c'est ce que ce mot décrivait le plus souvent — et parce qu'un bureau avec
         * dix séances n'existe pas dans l'échelle qu'on propose.
         *
         * Cette règle vit dans le domaine et non dans un mappeur : c'est une décision
         * nutritionnelle, et deux endroits qui la porteraient — la base et le fichier
         * de sauvegarde — finiraient par en donner deux versions.
         *
         * [decisions]: docs/11-decisions.md
         */
        fun ofLegacy(level: String): Activity = when (level) {
            "SEDENTARY" -> Activity(WorkActivity.DESK, WeeklySessions.NONE)
            "LIGHT" -> Activity(WorkActivity.DESK, WeeklySessions(LEGACY_LIGHT))
            "MODERATE" -> Activity(WorkActivity.ON_FEET, WeeklySessions(LEGACY_MODERATE))
            "ACTIVE" -> Activity(WorkActivity.PHYSICAL, WeeklySessions(LEGACY_ACTIVE))
            "VERY_ACTIVE" -> Activity(WorkActivity.PHYSICAL, WeeklySessions(WeeklySessions.MAX_SESSIONS))
            // Un nom qu'on ne connait pas : le socle le plus bas, comme le mappeur le
            // faisait deja. Une lecture prudente vaut mieux qu'un profil refuse.
            else -> Activity(WorkActivity.DESK, WeeklySessions.NONE)
        }
    }
}

// Les trois socles et l'increment de docs/03. Nommes un par un plutot que poses dans
// le constructeur : ils sont la seule chose de ce fichier qu'une relecture de la
// litterature pourrait faire bouger, et ils doivent se retrouver d'un coup d'oeil.
private const val DESK_FACTOR = 1.20
private const val ON_FEET_FACTOR = 1.40
private const val PHYSICAL_FACTOR = 1.60

// Les seances que chaque ancien niveau vaut : choisies pour que le facteur retombe a
// moins de deux centiemes de l'ancienne valeur (D137).
private const val LEGACY_LIGHT = 3
private const val LEGACY_MODERATE = 3
private const val LEGACY_ACTIVE = 2

/**
 * Ce qu'une seance hebdomadaire ajoute.
 *
 * Un vingtieme, et les trois socles ont ete choisis avec lui : "debout, trois seances"
 * retombe **exactement** sur 1,55, le facteur de l'exemple de reference de docs/03.
 * L'echelle change de forme sans que le chiffre publie bouge (D137).
 */
private const val PER_SESSION = 0.05

/** Au-dela, Mifflin-St Jeor sort du domaine ou elle a ete validee. */
private const val MAX_FACTOR = 1.90

/** Ce que l'affichage montre. Le stockage est **toujours** métrique ([docs/07][modele]). */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

/**
 * Qui utilise l'application, pour ce que le calcul en a besoin.
 *
 * **La date de naissance est stockée, jamais l'âge.** Un âge se périme en silence :
 * l'objectif calculé un 3 janvier resterait celui d'une personne d'un an plus jeune,
 * indéfiniment, et rien dans l'interface ne le dirait.
 *
 * Le poids n'est pas ici : il vit dans le journal de pesées, parce qu'il change et
 * qu'on veut sa tendance ([docs/07][modele]).
 *
 * [modele]: docs/07-modele-de-donnees.md
 */
data class UserProfile(
    val birthDate: LocalDate,
    val sex: Sex,
    val heightCm: Double,
    /**
     * Ce que le corps dépense : le métier, et le sport qui s'y ajoute.
     *
     * **Deux questions là où il n'y en avait qu'une** ([D137][decisions]). Un profil
     * écrit avant cette version portait un niveau unique ; il se traduit en couple à
     * la relecture, sans rien demander à personne — l'objectif ne bouge pas, et les
     * deux réponses se corrigent dans les réglages quand on veut.
     *
     * [decisions]: docs/11-decisions.md
     */
    val activity: Activity,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
) {
    /**
     * L'âge **à une date donnée**, et non « aujourd'hui ».
     *
     * La date vient de l'appelant, qui la tient d'une [app.hexavore.domain.time.Clock]
     * injectée : c'est ce qui rend le calcul reproductible, et c'est aussi ce qui
     * permettra de rejuger une journée passée avec l'âge qu'on avait ce jour-là.
     */
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years
}
