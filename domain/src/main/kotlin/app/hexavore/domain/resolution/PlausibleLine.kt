package app.hexavore.domain.resolution

import app.hexavore.domain.nutrition.Macro
import app.hexavore.domain.nutrition.NutrientValues

/**
 * Ce qu'une ligne ne peut pas être, et qui arrive pourtant.
 *
 * ### Pourquoi un garde-fou en plus du prompt
 *
 * Le prompt dit déjà qu'un aliment à un gramme n'existe pas, et qu'une valeur inconnue
 * s'écrit `null` plutôt que zéro. Un modèle qui dérive une fois sur dix continuera d'y
 * déroger une fois sur dix : une consigne se contourne, une vérification non
 * ([D138][decisions]).
 *
 * ### Ce qu'on en fait, et ce qu'on n'en fait pas
 *
 * **Rien n'est rejeté, rien n'est corrigé.** La ligne arrive à l'écran de validation
 * avec le marqueur que le projet réserve depuis [D25][decisions] à ce qui a été estimé —
 * le contour en pointillés — et l'œil va droit dessus.
 *
 * Corriger d'office remplacerait le chiffre du modèle par un chiffre de l'application,
 * ce que [01][perimetre] lui interdit : elle propose, elle ne décide pas. Rejeter la
 * ligne ferait disparaître un aliment qu'on a bel et bien mangé, et sans le dire.
 *
 * [perimetre]: docs/01-perimetre.md
 * [decisions]: docs/11-decisions.md
 */
object PlausibleLine {
    /**
     * En dessous, un poids ne décrit plus une portion.
     *
     * Cinq grammes, et non un : une épice ou un condiment se pèse vraiment ainsi, et
     * refuser un gramme de sel ferait signaler ce qui est juste. Ce qui arrive en
     * pratique — une brochette à un gramme, une pomme à deux — tombe très en dessous de
     * ce seuil, parce que c'est une erreur d'échelle et non une imprécision.
     */
    const val MINIMUM_GRAMS = 5.0

    /**
     * Ce poids décrit-il une portion qu'on mange ?
     *
     * Zéro n'est pas suspect **ici** : il dit qu'aucune quantité n'a pu être estimée, et
     * c'est l'écran de validation qui réclame alors le chiffre manquant. Le cas qu'on
     * veut attraper est celui qui passe inaperçu — un chiffre plausible en apparence, et
     * faux d'un facteur cent.
     */
    fun suspiciousWeight(grams: Double): Boolean = grams > 0.0 && grams < MINIMUM_GRAMS

    /**
     * Ces valeurs sont-elles vides alors qu'elles prétendent être complètes ?
     *
     * **Six zéros ne décrivent aucun aliment.** L'eau est le seul cas réel, et elle n'a
     * pas de calories parce qu'elle n'en a pas — mais elle arrive du catalogue, pas
     * d'une estimation. Six zéros venus d'un modèle ou d'une fiche mal rejointe veulent
     * dire *« je n'ai rien trouvé »*, et l'application les afficherait comme un repas
     * sans apport.
     *
     * La distinction avec l'inconnu compte : `null` se voit à l'écran de validation, qui
     * désigne le champ manquant. Zéro se lit comme une mesure.
     */
    fun emptyValues(values: NutrientValues): Boolean = Macro.entries.all { values[it] == 0.0 }
}
