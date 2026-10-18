package app.hexavore.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Interdit les réglages de barres système qu'Android 15 rend obsolètes.
 *
 * `Window.setStatusBarColor`, `setNavigationBarColor` et le mode d'encoche `SHORT_EDGES`
 * sont sans effet sous Android 15 pour une application qui le vise, et le Play Store les
 * relève dans son code — bibliothèques comprises — comme le signe d'un affichage bord à
 * bord mal tenu. `enableEdgeToEdge` d'androidx les appelle tous les trois, pour les
 * versions d'avant. Les barres se règlent dans le thème de fenêtre `Theme.Hexavore`, et
 * leurs icônes par `WindowCompat` (D170).
 *
 * La règle lit des **noms**, pas des types : detekt tourne ici sans résolution de types,
 * et aucun de ces noms n'a d'autre sens dans une application Android. Les imports ne
 * comptent pas : c'est l'usage qui se signale, une fois.
 */
class DeprecatedSystemBars(config: Config = Config.empty) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "DeprecatedSystemBars",
            severity = Severity.Defect,
            description = MESSAGE,
            debt = Debt.TWENTY_MINS,
        )

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        if (expression.getReferencedName() !in DEPRECATED) return
        if (expression.getStrictParentOfType<KtImportDirective>() != null) return

        report(CodeSmell(issue, Entity.from(expression), MESSAGE))
    }

    private companion object {
        val DEPRECATED =
            setOf(
                "statusBarColor",
                "setStatusBarColor",
                "navigationBarColor",
                "setNavigationBarColor",
                "navigationBarDividerColor",
                "setNavigationBarDividerColor",
                "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES",
                "enableEdgeToEdge",
            )

        const val MESSAGE =
            "Reglage de barre systeme obsolete depuis Android 15. Le Play Store le releve dans le code " +
                "de l'application, et il est sans effet pour qui vise l'API 35. Les couleurs des barres " +
                "et le mode d'encoche sont dans le theme de fenetre Theme.Hexavore, leurs icones se " +
                "reglent par WindowCompat.getInsetsController (edgeToEdgeFollowing). " +
                "Voir docs/11-decisions.md, D170."
    }
}
