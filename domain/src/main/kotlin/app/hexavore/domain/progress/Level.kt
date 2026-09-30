package app.hexavore.domain.progress

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Ce que rapporte chaque chose, et ce que coûte chaque niveau.
 *
 * ### Ce que les points mesurent
 *
 * **Rien d'inventé.** Chaque source de points correspond à un acte réel : un plat noté,
 * une journée qui s'est terminée en ayant été suivie, une journée restée dans sa
 * fourchette. Des points attribués pour avoir ouvert l'application auraient récompensé
 * l'ouverture, et c'est exactement la mécanique dont ce projet ne veut pas — [01][perimetre]
 * cherche à faire *noter* en cinq secondes, pas à faire revenir pour rien.
 *
 * **Le plat rapporte le moins, la journée parfaite le plus.** Dans cet ordre, parce que
 * c'est l'ordre de l'effort : noter un plat prend cinq secondes, terminer une journée
 * notée demande d'y revenir trois fois, et tenir sa fourchette demande d'avoir décidé
 * quoi manger en fonction de ce qu'on avait déjà mangé.
 *
 * ### Pourquoi les points ne redescendent jamais
 *
 * Ils se **dérivent** du journal — donc un plat noté les fait monter à l'instant — mais
 * ce qui a été atteint est rangé comme un plancher ([ProgressStore]). Corriger une
 * erreur de l'an dernier ne doit pas faire perdre un niveau : la correction serait
 * punie, et personne ne corrigerait plus rien.
 *
 * [perimetre]: docs/01-perimetre.md
 */
object Points {
    /** Un plat noté, quel que soit le mode de saisie. */
    const val PER_DISH = 10

    /** Une journée close ayant reçu au moins un plat. */
    const val PER_LOGGED_DAY = 30

    /** Une journée close et parfaite, **en plus** de la journée notée. */
    const val PER_PERFECT_DAY = 100
}

/**
 * Le niveau, et où l'on en est dedans.
 *
 * **Les niveaux s'écartent à mesure qu'on monte** : passer du 1 au 2 coûte 100 points,
 * du 9 au 10 en coûte 900. C'est ce qui garde un sens au chiffre — un niveau qui
 * tomberait au même rythme pendant deux ans ne dirait plus rien de ce qu'il a fallu
 * pour l'atteindre — et c'est ce qui rend les premiers rapides, là où l'habitude se
 * joue.
 */
data class Level(val number: Int, val pointsInto: Int, val pointsToNext: Int) {
    /** Où l'on en est dans le niveau courant, dans `[0, 1]`. Pour une jauge. */
    val fraction: Float get() = if (pointsToNext <= 0) 1f else pointsInto.toFloat() / pointsToNext

    companion object {
        /**
         * Le niveau que valent ces points.
         *
         * Le seuil du niveau `n` vaut `50 × n × (n − 1)`, c'est-à-dire la somme des
         * paliers `100, 200, 300…`. L'inverse se résout au lieu de boucler : une boucle
         * qui monte niveau par niveau ferait le même travail, plus lentement, et se
         * mettrait à ramer le jour où quelqu'un atteindrait le niveau cent.
         */
        fun of(points: Long): Level {
            val safe = points.coerceAtLeast(0)
            // n = (1 + sqrt(1 + 8p/100)) / 2, arrondi vers le bas : le plus grand n
            // dont le seuil ne depasse pas p.
            val number = floor((1 + sqrt(1 + QUADRATIC * safe / STEP)) / HALVES).toInt().coerceAtLeast(1)
            val floorPoints = thresholdOf(number)
            return Level(
                number = number,
                pointsInto = (safe - floorPoints).toInt(),
                pointsToNext = (thresholdOf(number + 1) - floorPoints).toInt(),
            )
        }

        /** Les points qu'il faut avoir accumulés pour être de ce niveau. */
        fun thresholdOf(number: Int): Long = STEP.toLong() * number * (number - 1) / HALVES.toInt()

        /** Ce que coûte le premier niveau, et de combien chaque suivant s'alourdit. */
        private const val STEP = 100

        /** Les deux constantes de la résolution : `n = (1 + √(1 + 8p/pas)) / 2`. */
        private const val QUADRATIC = 8.0
        private const val HALVES = 2.0
    }
}
