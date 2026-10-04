package app.hexavore.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hexavore.core.designsystem.R

/**
 * Le thème de l'application : schéma Material 3, typographie, formes, et les
 * jetons qui n'existent pas dans Material.
 *
 * Le parti pris est un néon **fonctionnel** : la lumière hiérarchise l'information,
 * elle ne décore pas. Trois règles en découlent, visibles dans le code : une seule
 * chose brille par zone, le fond reste sombre et neutre, une couleur porte un sens
 * et un seul.
 *
 * @see docs/08-design-system.md
 */
@Composable
fun NeonTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val macros = remember(darkTheme) { macroColorScheme(darkTheme) }
    val colors = remember(darkTheme) { neonColorScheme(darkTheme) }

    CompositionLocalProvider(
        LocalMacroColors provides macros,
        LocalMotion provides rememberMotion(),
        LocalGold provides if (darkTheme) DarkGold else LightGold,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = NeonTypography,
            shapes = NeonShapes,
            content = content,
        )
    }
}

/** Accès aux jetons que Material 3 ne couvre pas. */
object NeonTheme {
    /** Les six teintes et leurs déclinaisons, pour le thème courant. */
    val macros: MacroColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalMacroColors.current

    /** Les durées d'animation, déjà ramenées à zéro si l'appareil le demande. */
    val motion: Motion
        @Composable @ReadOnlyComposable
        get() = LocalMotion.current

    /**
     * L'or d'une journée tenue de bout en bout.
     *
     * **La seule teinte hors des six et de Material**, et elle ne désigne pas une
     * donnée : elle récompense. C'est ce qui la distingue de ce que la règle ci-dessous
     * interdit — une septième couleur qui dirait un septième nutriment ferait de la
     * teinte un canal ambigu, alors que celle-ci ne se pose jamais à côté des six pour
     * être comparée à elles ([D146][decisions]).
     *
     * L'anneau fermé seul ne suffisait pas : il se distinguait d'un anneau brisé par un
     * détail de tracé, à vingt millimètres de diamètre, dans un bandeau de sept jours.
     * Ce qui se veut exceptionnel doit se voir sans être cherché.
     *
     * [decisions]: docs/11-decisions.md
     */
    val gold: Color
        @Composable @ReadOnlyComposable
        get() = LocalGold.current
}

// Il n'y a volontairement pas de rôle de couleur au-delà de Material 3, des six
// macros et de l'or. L'estimation IA, candidate sérieuse à une teinte de plus, se
// signale par la forme — voir SourceBadge et D25 dans docs/11-decisions.md. L'or fait
// exception parce qu'il ne dit aucune donnée : il récompense une journée tenue, ne se
// compare jamais aux six, et D146 dit pourquoi la forme seule n'y suffisait pas.

// --- Fonds et surfaces -------------------------------------------------------

/**
 * Les fonds d'un thème.
 *
 * Le noir pur est écarté : il crée des bords durs autour des lueurs et provoque du
 * smearing sur les dalles OLED au défilement. `#08080C` conserve l'économie
 * d'énergie sans les artefacts.
 */
@Immutable
private data class Surfaces(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val error: Color,
)

private val DarkSurfaces =
    Surfaces(
        background = Color(0xFF08080C),
        surface = Color(0xFF111119),
        surfaceVariant = Color(0xFF1A1A26),
        outline = Color(0xFF2A2A3A),
        onSurface = Color(0xFFEDEDF5),
        onSurfaceVariant = Color(0xFF9A9AB0),
        error = Color(0xFFFF5C7A),
    )

// Thème clair fourni mais secondaire : l'application est pensée pour le sombre.
// Les deux fonds viennent de la documentation ; les surfaces intermédiaires en sont
// déduites pour conserver la même hiérarchie de profondeur.
private val LightSurfaces =
    Surfaces(
        background = Color(0xFFFAFAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEFEFF4),
        outline = Color(0xFFD5D5E0),
        onSurface = Color(0xFF14141C),
        onSurfaceVariant = Color(0xFF5A5A70),
        error = Color(0xFFB3153B),
    )

private fun surfaces(dark: Boolean): Surfaces = if (dark) DarkSurfaces else LightSurfaces

/**
 * Le voile posé sur une image qu'on ne maîtrise pas — l'aperçu du scan aujourd'hui,
 * la photo de l'écran d'IA demain.
 *
 * **Sombre dans les deux thèmes, et c'est un choix.** Un panneau posé sur une image
 * n'a pas de fond dont hériter : il apporte le sien. Le thème clair se contente
 * d'assombrir les teintes de 25 %, ce qui suffit à un trait mais pas à du texte — le
 * cyan des calories tombe alors à 2,8:1 sur du blanc, très en dessous du AA que la
 * charte exige. Sur ce voile, il en tient 6,7:1 quel que soit le thème, et le néon
 * reste l'élément clair de la paire comme docs/08-design-system.md le pose.
 */
internal val ScrimBackground: Color = DarkSurfaces.surface.copy(alpha = SCRIM_ALPHA)

/** L'encre du voile. Elle vient du thème sombre parce que le fond en vient aussi. */
internal val ScrimInk: Color = DarkSurfaces.onSurface

/**
 * Opacité du voile.
 *
 * Ce n'est pas une affaire de contraste — à 0,85 le texte tenait déjà 10:1 sur le
 * pire des fonds — mais de **bruit** : ce qui traverse un voile trop transparent est
 * une image de caméra, et sa texture passe sous les lettres là où elle ne les efface
 * pas. Ce qu'on gagne à laisser deviner l'image sous un panneau de trois lignes ne
 * vaut pas ce qu'on perd à lire par-dessus un rayon de supermarché.
 */
private const val SCRIM_ALPHA = 0.92f

/**
 * Le schéma Material 3, dérivé de la palette plutôt que réécrit.
 *
 * Les teintes ne sont pas recopiées ici : elles viennent de [macroColorScheme], qui
 * reste la seule source de vérité chromatique.
 */
private fun neonColorScheme(dark: Boolean): ColorScheme {
    val macros = macroColorScheme(dark)
    val fonds = surfaces(dark)

    // Jamais de texte foncé sur un aplat néon : illisible en extérieur, et le néon
    // doit rester l'élément clair de la paire. Ces rôles ne servent donc qu'aux
    // composants Material qu'on n'utilise pas en aplat plein.
    val onAccent = if (dark) fonds.background else fonds.surface
    val base = if (dark) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = macros.calories.base,
        onPrimary = onAccent,
        primaryContainer = fonds.surfaceVariant,
        onPrimaryContainer = macros.calories.base,
        secondary = macros.protein.base,
        onSecondary = onAccent,
        tertiary = macros.fiber.base,
        onTertiary = onAccent,
        background = fonds.background,
        onBackground = fonds.onSurface,
        surface = fonds.surface,
        onSurface = fonds.onSurface,
        surfaceVariant = fonds.surfaceVariant,
        onSurfaceVariant = fonds.onSurfaceVariant,
        outline = fonds.outline,
        outlineVariant = fonds.outline,
        error = fonds.error,
        onError = onAccent,
    )
}

// --- Typographie -------------------------------------------------------------

/**
 * Chiffres tabulaires.
 *
 * Sans eux, un total qui passe de 1 199 à 1 200 fait sauter la mise en page. Le
 * défaut est discret, mais il se produit à chaque saisie, c'est-à-dire dix fois
 * par jour.
 */
private const val TABULAR_FIGURES = "tnum"

private const val LINE_HEIGHT_RATIO = 1.3f

/**
 * Une graisse d'Inter, obtenue en fixant l'axe `wght` de la police variable.
 *
 * L'API des axes variables est encore marquée expérimentale par Compose. L'adhésion
 * est déclarée ici et nulle part ailleurs : c'est le seul endroit du projet qui
 * touche à la police, donc le jour où la signature change, il y a un fichier à
 * corriger.
 */
@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: FontWeight, axis: Int) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

// Inter, variable, embarquée : aucun appel réseau à un service de polices.
private val InterFontFamily =
    FontFamily(
        interFont(FontWeight.Normal, 400),
        interFont(FontWeight.Medium, 500),
        interFont(FontWeight.SemiBold, 600),
        interFont(FontWeight.Bold, 700),
    )

private fun neonStyle(sizeSp: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    // sp et non dp : la mise en page doit tenir jusqu'à 200 % de taille de police.
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * LINE_HEIGHT_RATIO).sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

private fun TextStyle.withInter(): TextStyle = copy(fontFamily = InterFontFamily, fontFeatureSettings = TABULAR_FIGURES)

private val MaterialDefaults = Typography()

// Les six styles de docs/08-design-system.md sont posés sur les emplacements
// Material correspondants ; les autres héritent simplement d'Inter, pour qu'un
// composant Material tiers ne fasse pas apparaître une seconde police.
private val NeonTypography =
    Typography(
        displayLarge = neonStyle(48, FontWeight.Bold),
        displayMedium = MaterialDefaults.displayMedium.withInter(),
        displaySmall = MaterialDefaults.displaySmall.withInter(),
        headlineLarge = MaterialDefaults.headlineLarge.withInter(),
        headlineMedium = neonStyle(28, FontWeight.SemiBold),
        headlineSmall = MaterialDefaults.headlineSmall.withInter(),
        titleLarge = MaterialDefaults.titleLarge.withInter(),
        titleMedium = neonStyle(20, FontWeight.SemiBold),
        titleSmall = MaterialDefaults.titleSmall.withInter(),
        bodyLarge = neonStyle(16, FontWeight.Normal),
        bodyMedium = MaterialDefaults.bodyMedium.withInter(),
        bodySmall = MaterialDefaults.bodySmall.withInter(),
        labelLarge = neonStyle(14, FontWeight.Medium),
        labelMedium = MaterialDefaults.labelMedium.withInter(),
        labelSmall = neonStyle(12, FontWeight.Normal),
    )

// --- Espacement et formes ----------------------------------------------------

/** Grille de 4 dp. Toute marge du projet est un multiple pris ici. */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    /** Marge latérale d'un écran. */
    val screenMargin: Dp = 16.dp

    /** Espace entre deux cartes. */
    val betweenCards: Dp = 12.dp

    /** Padding interne d'une carte. */
    val cardPadding: Dp = 16.dp
}

/** Rayons de coin. */
object Radius {
    /** Champs et badges. */
    val field: Dp = 8.dp

    /** Cartes. */
    val card: Dp = 16.dp

    /** Feuilles modales. */
    val sheet: Dp = 24.dp

    /** Pastilles et boutons : entièrement arrondis. */
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Cible tactile minimale.
 *
 * Elle vaut aussi pour les pastilles du calendrier, qui mesurent 44 dp à l'œil :
 * la zone tactile déborde le visuel plutôt que l'inverse.
 */
object TouchTarget {
    val min: Dp = 48.dp
}

private val NeonShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Radius.field),
        small = RoundedCornerShape(Radius.field),
        medium = RoundedCornerShape(Radius.card),
        large = RoundedCornerShape(Radius.sheet),
        extraLarge = RoundedCornerShape(Radius.sheet),
    )

// --- Locaux de composition ---------------------------------------------------

private val LocalMacroColors =
    staticCompositionLocalOf<MacroColorScheme> {
        error("Aucun NeonTheme dans l'arbre : envelopper le contenu dans NeonTheme { }.")
    }

private val LocalMotion = staticCompositionLocalOf { Motion.Standard }

/**
 * L'or des deux thèmes.
 *
 * Plus clair sur fond sombre, plus profond sur fond clair : c'est le même or, et il doit
 * se lire comme tel des deux côtés. Un jaune unique virait au citron la nuit et
 * disparaissait dans le blanc le jour.
 */
private val DarkGold = Color(0xFFFFD24A)
private val LightGold = Color(0xFFB8860B)

private val LocalGold = staticCompositionLocalOf { DarkGold }
