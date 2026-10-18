package app.hexavore.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Les quatre règles de docs/10-qualite-et-livraison.md.
 *
 * Elles encodent des décisions de conception qui, autrement, ne tiendraient que
 * par la vigilance de la revue. Une règle écrite dans un document se contourne
 * par distraction ; une règle qui casse le build, non.
 */
class HexavoreRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = RULE_SET_ID

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            HardcodedColor(config),
            AndroidImportInDomain(config),
            TimeOutsideClock(config),
            DeprecatedSystemBars(config),
        ),
    )

    companion object {
        /** Identifiant repris tel quel comme clé de premier niveau dans detekt.yml. */
        const val RULE_SET_ID: String = "hexavore"
    }
}
