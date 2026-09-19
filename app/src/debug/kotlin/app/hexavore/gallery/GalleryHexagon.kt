package app.hexavore.gallery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hexavore.R
import app.hexavore.core.designsystem.component.MacroHexagon
import app.hexavore.core.designsystem.component.MacroQuarter
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.nutrition.Macro

/**
 * Les trois cas que l'hexagone doit savoir montrer.
 *
 * Dans un fichier à part et non dans `GallerySections` : la figure est assez
 * grande pour mériter sa propre section, et le fichier des sections était déjà à
 * la limite de fonctions que detekt tolère.
 */
@Composable
internal fun HexagonSection() = GallerySection(R.string.gallery_section_hexagon) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        HexagonCase(
            captionRes = R.string.gallery_hexagon_normal,
            quarters = mapOf(
                Macro.CALORIES to MacroQuarter(ratio = 0.61f),
                Macro.PROTEIN to MacroQuarter(ratio = 0.78f),
                Macro.FIBER to MacroQuarter(ratio = 0.34f),
                Macro.CARBS to MacroQuarter(ratio = 0.72f),
                Macro.SUGARS to MacroQuarter(ratio = 0.65f),
                Macro.FAT to MacroQuarter(ratio = 0.58f),
            ),
        )

        // Les sucres depassent le plafond de representation : leur arete doit
        // apparaitre en dents de scie, et l'hexagone cible doit avoir retreci.
        HexagonCase(
            captionRes = R.string.gallery_hexagon_over,
            quarters = mapOf(
                Macro.CALORIES to MacroQuarter(ratio = 1.12f),
                Macro.PROTEIN to MacroQuarter(ratio = 0.9f),
                Macro.FIBER to MacroQuarter(ratio = 0.4f),
                Macro.CARBS to MacroQuarter(ratio = 1.4f),
                Macro.SUGARS to MacroQuarter(ratio = 2.3f),
                Macro.FAT to MacroQuarter(ratio = 0.8f),
            ),
        )
    }
}

@Composable
private fun HexagonCase(@StringRes captionRes: Int, quarters: Map<Macro, MacroQuarter>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(captionRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MacroHexagon(quarters = quarters)
    }
}
