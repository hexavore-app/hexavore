package app.hexavore.feature.home.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.feature.home.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Le tour guidé, posé par-dessus l'accueil réel.
 *
 * ### Un voile percé, et non une suite d'écrans
 *
 * Ce que montre le tour est l'application elle-même : le voile assombrit tout **sauf**
 * ce dont la bulle parle ([D141][decisions]). Un diaporama d'images aurait expliqué une
 * application qui n'existe pas encore à l'écran, et aurait vieilli à la première
 * refonte.
 *
 * Le trou se découpe en `BlendMode.Clear`, ce qui demande une couche hors écran : sans
 * elle, l'effacement mordrait sur ce qui est derrière, c'est-à-dire sur l'accueil.
 *
 * ### La bulle se pose contre sa cible
 *
 * Elle ne reste plus en haut de l'écran : elle se glisse **juste au-dessus ou juste en
 * dessous** de ce qu'elle désigne, du côté où il y a de la place ([D143][decisions]).
 * Une bulle collée au plafond pendant qu'un bouton s'éclaire en bas laisse à l'œil le
 * soin de faire le lien — et cet œil-là découvre l'application.
 *
 * ### Rien ne passe au travers
 *
 * Le voile prend tous les gestes. Pendant un tour, les seuls boutons qui répondent sont
 * ceux de la bulle — on ne veut pas que quelqu'un ouvre la recherche au milieu d'une
 * phrase qui parle d'autre chose, et retrouve l'accueil sans savoir ce qui l'a quitté.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun GuidedTour(
    step: TourStep,
    anchors: TourAnchors,
    scroll: ScrollState,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    val cible = step.target?.let { anchors[it] }

    SuitLaCible(step, anchors, scroll, cible != null) { cible }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().blockingTaps()) {
        val hauteur = with(LocalDensity.current) { maxHeight.toPx() }
        val ecart = with(LocalDensity.current) { GAP.toPx() }

        Veil(cible)
        Bubble(
            step = step,
            cadre = BubbleFrame(cible, hauteur, ecart),
            keyless = keyless,
            onNext = onNext,
            onConfigureAi = onConfigureAi,
            onFinish = onFinish,
        )
    }
}

/**
 * Amène ce dont l'étape parle **sous les yeux**, et allume le quartier qu'elle décrit.
 *
 * ### Pourquoi l'écran doit bouger
 *
 * Le tour désigne des éléments d'une page qui défile. Les six compteurs, la barre du
 * bas, les réglages : rien ne garantit qu'ils soient visibles au moment où la phrase les
 * concerne. Sans ce défilement, le voile s'ouvrait sur du vide et la bulle parlait d'un
 * élément resté deux écrans plus bas ([D147][decisions]).
 *
 * **La cible va au milieu**, et pas seulement « quelque part dans l'écran » : c'est la
 * seule position qui laisse de la place à la bulle des deux côtés, quelle que soit sa
 * hauteur.
 *
 * ### Pourquoi la clé n'est pas le rectangle
 *
 * Défiler déplace la cible, donc son rectangle, donc relancerait l'effet — qui
 * défilerait encore. La clé est l'**étape**, plus le seul fait qu'une position soit
 * connue ; le rectangle se lit au moment où l'on en a besoin.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun SuitLaCible(
    step: TourStep,
    anchors: TourAnchors,
    scroll: ScrollState,
    placee: Boolean,
    cible: () -> Rect?,
) {
    val hauteur = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    LaunchedEffect(step, placee) {
        anchors.spotlight = step.macro
        val rect = cible() ?: return@LaunchedEffect
        val ecart = rect.center.y - hauteur / 2f
        if (abs(ecart) > SCROLL_TOLERANCE) scroll.animateScrollBy(ecart)
    }

    // La figure ne doit pas rester allumee quand le tour s'en va : le quartier mis en
    // avant est l'affaire du tour, pas un etat de l'accueil.
    DisposableEffect(Unit) {
        onDispose { anchors.spotlight = null }
    }
}

/** En deçà, l'écran est déjà au bon endroit : défiler de trois pixels serait un tic. */
private const val SCROLL_TOLERANCE = 24f

/** Le voile, et le trou de lumière sur ce dont on parle. */
@Composable
private fun Veil(target: Rect?) {
    val scrim = MaterialTheme.colorScheme.scrim
    val marge = with(LocalDensity.current) { HOLE_MARGIN.toPx() }
    val rayon = with(LocalDensity.current) { HOLE_CORNER.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Hors ecran : `Clear` efface ce qui est dans *cette* couche, et sans elle
            // il effacerait l'accueil lui-meme.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color = scrim, alpha = VEIL_ALPHA)

        target?.let {
            drawRoundRect(
                color = scrim,
                topLeft = Offset(it.left - marge, it.top - marge),
                size = Size(it.width + marge * 2, it.height + marge * 2),
                cornerRadius = CornerRadius(rayon, rayon),
                blendMode = BlendMode.Clear,
            )
        }
    }
}

/**
 * Ce que l'étape dit, et ce qu'elle propose de faire.
 *
 * ### Elle ne peut plus sortir de l'écran
 *
 * Elle se posait par alignement et décalage : collée en bas, puis remontée de la
 * distance qui la séparait du haut de sa cible. Sur une cible **haute** — l'hexagone,
 * le bloc de la journée — cette distance valait presque toute la hauteur de l'écran, et
 * la bulle montait d'autant : on n'en voyait que le bas ([D147][decisions]).
 *
 * Elle calcule donc une position absolue, puis la **borne** entre les deux marges
 * système. Pour cela il faut sa hauteur, qui ne se connaît qu'une fois mesurée : elle
 * se place à zéro le temps d'une image, puis se repositionne. C'est le prix d'un
 * placement qui tient quelle que soit la longueur du texte, et il se paie une fois par
 * étape.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
private fun Bubble(
    step: TourStep,
    cadre: BubbleFrame,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    // **Jamais remise a zero d'une etape a l'autre.** Elle l'etait, et c'est ce qui
    // faisait sortir la bulle par le bas : `onSizeChanged` ne rappelle que lorsque la
    // taille **change**, si bien que deux etapes de meme hauteur -- calories et
    // proteines, par exemple -- laissaient la mesure a zero. La bulle se placait alors
    // comme si elle n'avait pas de hauteur, et tout ce qu'elle en avait depassait
    // (D151). Garder la derniere mesure connue est a la fois plus juste et plus simple.
    var mesure by remember { mutableIntStateOf(0) }
    val marges = WindowInsets.systemBars
    val densite = LocalDensity.current
    val haut = marges.getTop(densite).toFloat()
    val bas = marges.getBottom(densite).toFloat()
    val plafond = with(densite) { cadre.placeLibre(haut, bas).toDp() }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Jamais plus haute que la place entre les deux marges, et ce qui
                // depasse se fait defiler : une bulle plus grande que l'ecran n'a
                // aucune position correcte, et la borner sans la rendre lisible
                // reviendrait a en couper la fin (D150).
                .heightIn(max = plafond)
                .onSizeChanged { mesure = it.height }
                .offset { IntOffset(0, cadre.sommet(mesure, haut, bas).roundToInt()) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(step.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(step.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Actions(step, keyless, onNext, onConfigureAi, onFinish)
            }
        }
    }
}

/**
 * Où la bulle a le droit de se poser : la cible, et la place disponible autour.
 *
 * Les trois voyagent ensemble parce qu'ils ne servent qu'à une chose — placer la bulle —
 * et parce que passés un par un ils poussaient la composable au-delà du seuil de
 * paramètres. La réponse du projet est de regrouper selon ce que les choses sont.
 */
private data class BubbleFrame(val cible: Rect?, val hauteur: Float, val ecart: Float) {
    /** La place qu'une bulle peut occuper au plus : l'écran, ses deux marges retirées. */
    fun placeLibre(haut: Float, bas: Float): Float = (hauteur - haut - bas - ecart * 2).coerceAtLeast(0f)

    /**
     * Le haut de la bulle, en pixels depuis le haut de l'écran.
     *
     * **Du côté où il reste de la place**, puis borné. Sous la cible quand elle occupe
     * la moitié haute, au-dessus sinon — et si le compte tombe en dehors de l'écran, il
     * est ramené dedans. Le recouvrement qui en résulte est préférable à une bulle qu'on
     * ne peut pas lire : une cible qui prend presque tout l'écran n'a de place nulle
     * part, et il faut bien choisir.
     *
     * Sans cible, la bulle se centre : c'est ce qu'on veut d'une étape qui ne désigne
     * rien.
     *
     * @param mesure la hauteur mesurée de la bulle, `0` tant qu'elle n'est pas posée.
     */
    fun sommet(mesure: Int, haut: Float, bas: Float): Float {
        val bulle = mesure.toFloat()
        val minimum = haut + ecart
        val maximum = (hauteur - bas - ecart - bulle).coerceAtLeast(minimum)

        val rect = cible ?: return ((hauteur - bulle) / 2f).coerceIn(minimum, maximum)
        val dessous = rect.center.y < hauteur / 2f
        val vise = if (dessous) rect.bottom + ecart else rect.top - ecart - bulle
        return vise.coerceIn(minimum, maximum)
    }
}

/**
 * Les boutons, qui ne sont pas les mêmes partout.
 *
 * **L'étape de l'IA a deux issues et le dit.** « Plus tard » n'est pas caché dans un
 * coin : un refus qu'on doit chercher n'est pas un choix, et le tour montre ensuite ce
 * que ce refus coûte.
 */
@Composable
private fun Actions(
    step: TourStep,
    keyless: Boolean,
    onNext: () -> Unit,
    onConfigureAi: () -> Unit,
    onFinish: () -> Unit,
) {
    val derniere = step.next(keyless) == null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step == TourStep.AI) {
            TextButton(onClick = onNext) { Text(stringResource(R.string.tour_later)) }
            Button(onClick = onConfigureAi) { Text(stringResource(R.string.tour_configure)) }
        } else {
            if (!derniere) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.tour_skip)) }
            }
            Button(onClick = if (derniere) onFinish else onNext) {
                Text(stringResource(if (derniere) R.string.tour_done else R.string.tour_next))
            }
        }
    }
}

/**
 * Le voile avale les gestes : pendant un tour, seule la bulle répond.
 *
 * `pointerInput` et non `clickable` : on ne veut ni l'ondulation, ni le rôle de bouton
 * annoncé au lecteur d'écran. Ce n'est pas un bouton, c'est un mur.
 */
private fun Modifier.blockingTaps(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

private const val VEIL_ALPHA = 0.82f

/** L'air entre la cible éclairée et le bord de la bulle. */
private val GAP = 12.dp

private val HOLE_MARGIN = 8.dp
private val HOLE_CORNER = 16.dp
