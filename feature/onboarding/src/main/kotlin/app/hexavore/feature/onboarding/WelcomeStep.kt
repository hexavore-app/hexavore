package app.hexavore.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.MacroHexagon
import app.hexavore.core.designsystem.component.MacroQuarter
import app.hexavore.core.designsystem.theme.Radius
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro
import app.hexavore.core.designsystem.R as DesignStrings

/**
 * **1. Accueil.** La figure des six compteurs, une phrase, et l'avertissement.
 *
 * ### Une figure en tête, et un seul axe
 *
 * L'écran mêlait deux alignements : le titre et l'hexagone centrés, mais la phrase et
 * l'avertissement calés à gauche dès qu'ils passaient à la ligne, avec une case qui
 * pendait en haut d'un paragraphe de quatre lignes. Rien ne s'alignait sur rien, et
 * c'était le premier écran de l'application ([D158][decisions]).
 *
 * Il se lit désormais de haut en bas sur **un seul axe** : la figure, le titre et la
 * phrase centrés, comme la couverture qu'ils sont ; puis l'avertissement dans une
 * carte, en pleine largeur, où un paragraphe calé à gauche est à sa place.
 *
 * L'hexagone plutôt qu'un paragraphe de plus : c'est la figure qui donne son nom à
 * l'application, elle existe déjà, et elle montre en une image ce qu'un texte
 * raconterait — six compteurs, pas seulement des calories. Les valeurs sont un
 * échantillon, et l'écran ne prétend pas le contraire.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun WelcomeStep(answers: OnboardingAnswers, onAnswers: (OnboardingAnswers) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
    ) {
        MacroHexagon(quarters = SAMPLE_DAY)

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DisclaimerCard(
            accepted = answers.disclaimerAccepted,
            onAccepted = { onAnswers(answers.copy(disclaimerAccepted = it)) },
        )
    }
}

/**
 * L'avertissement, **dans une carte qu'on coche tout entière**.
 *
 * ### Ce qu'on lit, et ce qu'on coche
 *
 * La case portait le paragraphe entier en libellé : quatre lignes à côté d'une case de
 * 24 dp, qui s'alignait sur la première et laissait les trois autres dessous. Le
 * paragraphe devient ce qu'il est — une information, avec son icône — et la case reçoit
 * un libellé d'une ligne, « J'ai lu et compris », qui se centre avec elle.
 *
 * ### Une seule cible, et la carte entière
 *
 * C'est toute la carte qui bascule, et non la seule case : une cible de 24 dp est une
 * cible qu'on rate. TalkBack l'annonce comme **une** case à cocher, avec l'avertissement
 * et l'accord dans la même phrase — on ne peut pas accepter sans entendre ce qu'on
 * accepte.
 *
 * ### Ce que l'accord change à l'œil
 *
 * Le contour passe à la couleur d'accent et s'épaissit : la case cochée le dit déjà, et
 * le contour le redit à la taille de la carte. Une forme et une couleur, jamais une
 * couleur seule ([D25][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun DisclaimerCard(accepted: Boolean, onAccepted: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = if (accepted) AcceptedEdge else RestingEdge,
                color = if (accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = onAccepted)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Muette : le paragraphe juste a cote dit tout, et l'icone ne fait que
            // signaler qu'il s'agit d'une information a lire.
            Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = ink)
            Text(
                text = stringResource(DesignStrings.string.ds_medical_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // `onCheckedChange = null` : la case ne recoit plus le clic, c'est la carte
            // qui le porte. Sans cela, TalkBack annoncerait deux cibles pour une seule
            // decision.
            Checkbox(checked = accepted, onCheckedChange = null)
            Text(
                text = stringResource(R.string.onboarding_disclaimer_accept),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Le contour d'une carte au repos : assez pour la détacher du fond, pas plus. */
private val RestingEdge: Dp = 1.dp

/** Celui d'un accord donné : plus épais, pour que la carte entière le dise. */
private val AcceptedEdge: Dp = 2.dp

/**
 * Une journée d'exemple, pour que la figure d'accueil montre quelque chose.
 *
 * Des valeurs plausibles et volontairement inégales : six quartiers identiques
 * ressembleraient à un motif décoratif plutôt qu'à des compteurs.
 */
private val SAMPLE_DAY = mapOf(
    Macro.CALORIES to MacroQuarter(0.61f),
    Macro.PROTEIN to MacroQuarter(0.78f),
    Macro.FIBER to MacroQuarter(0.34f),
    Macro.CARBS to MacroQuarter(0.72f),
    Macro.SUGARS to MacroQuarter(0.45f),
    Macro.FAT to MacroQuarter(0.55f),
)
