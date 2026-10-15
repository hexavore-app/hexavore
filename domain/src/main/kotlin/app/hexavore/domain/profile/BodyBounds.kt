package app.hexavore.domain.profile

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Ce qu'un corps peut mesurer, et les âges pour lesquels le calcul a un sens.
 *
 * ### Des bornes, là où il n'y en avait aucune
 *
 * Rien ne bornait la saisie : 1 cm, 120 ans ou 999 kg entraient tels quels dans
 * Mifflin-St Jeor, et l'objectif qui en sortait s'affichait avec l'autorité d'un chiffre
 * personnel ([D160][decisions]). Les bornes retenues sont celles de l'**impossible ou
 * presque** : elles n'écartent pas un corps rare, elles écartent une faute de frappe — un
 * zéro de trop, deux champs intervertis, une taille tapée en mètres.
 *
 * ### L'IMC, en plus de chaque mesure
 *
 * Deux mesures plausibles chacune peuvent former un couple qui ne l'est pas : 100 cm et
 * 300 kg. Seul leur rapport le dit, et l'IMC est ce rapport. Le poids plausible se déduit
 * donc de la taille, et c'est **lui** que l'écran annonce : « pour 175 cm, entre 37 et
 * 245 kg » se corrige, « IMC hors limites » ne dit rien à personne.
 *
 * **Les bornes de poids sont des kilos entiers**, arrondis vers l'intérieur. Ce que
 * l'écran annonce est alors exactement ce que la règle accepte : un « 37 kg » affiché
 * qui laisserait passer 36,8 serait une règle que personne ne lit.
 *
 * [decisions]: docs/11-decisions.md
 */
object BodyBounds {
    /**
     * Les âges acceptés, en années révolues.
     *
     * **Seize ans au moins.** La dépense, les planchers caloriques et les seuils d'IMC
     * sont ceux d'un adulte : plus jeune, un objectif de perte se calculerait sur un
     * corps qui grandit encore. **Cent au plus** : au-delà, c'est la grille des années
     * qui a été mal lue.
     */
    val AGES: IntRange = 16..100

    /** De 1 m à 2,50 m. Hors de là, une taille tapée en mètres, ou à un zéro près. */
    val HEIGHTS_CM: ClosedFloatingPointRange<Double> = 100.0..250.0

    /** Ce que pèse un adulte, quelle que soit sa taille. */
    val WEIGHTS_KG: ClosedFloatingPointRange<Double> = 25.0..350.0

    /**
     * L'IMC d'un corps qui existe.
     *
     * Sous 12, la survie elle-même est en jeu ; au-delà de 80, on ne compte que des cas
     * isolés. Entre les deux, tout ce qu'un adulte peut peser — y compris ce qu'un
     * soignant voudrait savoir, et que ce filtre n'a pas à juger.
     */
    val BMI: ClosedFloatingPointRange<Double> = 12.0..80.0

    /** L'IMC sous lequel commence la maigreur, et sous lequel une perte n'est plus proposée. */
    const val LEAN_BMI = 18.5

    /**
     * Les naissances acceptées au jour dit : du lendemain des cent un ans au jour des
     * seize ans.
     *
     * Bornées au jour et non à l'année : sans quoi la grille des années laisserait
     * choisir, en décembre de l'année la plus récente, quelqu'un qui n'a pas seize ans.
     */
    fun birthDatesOn(today: LocalDate): ClosedRange<LocalDate> =
        today.minusYears(AGES.last + 1L).plusDays(1)..today.minusYears(AGES.first.toLong())

    /**
     * Les poids plausibles pour cette taille.
     *
     * Une taille absente, ou elle-même invraisemblable, ne dit rien du poids : on retombe
     * alors sur [WEIGHTS_KG], et c'est la taille qu'il faut corriger d'abord.
     */
    fun weightsFor(heightCm: Double?): ClosedFloatingPointRange<Double> {
        val squared = heightCm?.takeIf { it in HEIGHTS_CM }?.squaredMetres() ?: return WEIGHTS_KG
        val lightest = maxOf(WEIGHTS_KG.start, ceil(BMI.start * squared))
        val heaviest = minOf(WEIGHTS_KG.endInclusive, floor(BMI.endInclusive * squared))
        return lightest..heaviest
    }

    /**
     * Le poids le plus bas qu'une perte peut viser à cette taille : celui d'un IMC de 18,5.
     *
     * Au kilo **supérieur** : arrondir en dessous proposerait comme limite un poids qui
     * est déjà celui de la maigreur.
     */
    fun leanWeightFor(heightCm: Double): Double =
        maxOf(ceil(LEAN_BMI * heightCm.squaredMetres()), weightsFor(heightCm).start)

    /**
     * Des centimètres au carré, en mètres carrés.
     *
     * Le carré se prend **avant** la division : 175 × 175 / 10 000 retombe juste, là où
     * 1,75 au carré passe par une virgule que le binaire n'écrit pas, et un plafond
     * arrondi vers le haut prendrait alors un kilo de trop.
     */
    private fun Double.squaredMetres(): Double = this * this / SQUARE_CM_PER_SQUARE_METRE

    private const val SQUARE_CM_PER_SQUARE_METRE = 10_000.0
}

/** Les trois mesures du corps qu'une saisie peut rendre invraisemblables. */
enum class BodyMeasure {
    BIRTH_DATE,
    HEIGHT,
    WEIGHT,
}

/**
 * La première mesure invraisemblable, ou `null`.
 *
 * **Une mesure absente n'est pas invraisemblable** : elle manque, et c'est une autre
 * question, que l'écran pose déjà. L'ordre est celui du formulaire — l'âge, la taille,
 * puis le poids —, et il compte pour le dernier : le poids se juge **sur** la taille, et
 * une taille fausse le ferait refuser à tort.
 */
fun implausibleMeasure(birthDate: LocalDate?, heightCm: Double?, weightKg: Double?, today: LocalDate): BodyMeasure? =
    when {
        birthDate != null && birthDate !in BodyBounds.birthDatesOn(today) -> BodyMeasure.BIRTH_DATE
        heightCm != null && heightCm !in BodyBounds.HEIGHTS_CM -> BodyMeasure.HEIGHT
        weightKg != null && weightKg !in BodyBounds.weightsFor(heightCm) -> BodyMeasure.WEIGHT
        else -> null
    }
