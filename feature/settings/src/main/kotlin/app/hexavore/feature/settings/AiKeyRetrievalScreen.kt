package app.hexavore.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hexavore.core.designsystem.component.NeonButton
import app.hexavore.core.designsystem.component.NeonButtonStyle
import app.hexavore.core.designsystem.component.ScreenTopBar
import app.hexavore.core.designsystem.theme.Spacing
import app.hexavore.domain.ai.AiProvider

/**
 * Comment obtenir une clé : **un écran, et non un lien discret**.
 *
 * ### Le mur sur lequel tout le monde s'est arrêté
 *
 * L'écran des fournisseurs demandait une clé d'API et proposait, en petit et souligné,
 * « Comment obtenir une clé ? ». Aucun testeur n'a franchi cette étape seul. Le lien
 * ouvrait trois paragraphes honnêtes qui ne disaient pas la seule chose qui compte :
 * **il va falloir sortir de l'application et faire quelque chose soi-même**. Une
 * personne qui ne sait pas ce qu'est une clé d'API n'a aucune raison d'appuyer sur un
 * lien qui lui en parle comme d'une évidence ([D156][decisions]).
 *
 * ### Pourquoi un avertissement et pas une invitation
 *
 * Parce que c'est vrai, et parce que c'est ce qui prépare : quelqu'un qui part sans
 * savoir qu'il part se perd, referme le navigateur et abandonne. L'écran annonce la
 * sortie, dit qu'elle ne peut pas être faite à sa place, combien de temps elle prend,
 * et qu'**elle ne se fait qu'une fois**. Cette dernière phrase est celle qui décide :
 * un effort unique se consent, un effort dont on ignore la fin se refuse.
 *
 * ### Des étapes numérotées, malgré le risque
 *
 * Le projet refusait toute marche à suivre : les consoles changent de forme plusieurs
 * fois par an, et une documentation fausse est pire qu'aucune. L'arbitrage s'inverse ici
 * parce que l'absence de marche à suivre a, elle, un coût mesuré — personne n'y arrive.
 * Les étapes sont donc écrites **courtes, sans capture d'écran et sans nommer de
 * libellé dont on dépende** : ce qu'elles décrivent — se connecter, créer une clé, la
 * copier — ne changera pas même quand les boutons auront déménagé.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun AiKeyRetrievalRoute(provider: AiProvider, onClose: () -> Unit) {
    AiKeyRetrievalScreen(provider = provider, onClose = onClose)
}

@Composable
internal fun AiKeyRetrievalScreen(provider: AiProvider, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.ai_get_title),
                onClose = onClose,
                closeLabel = stringResource(R.string.ai_get_close),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards),
        ) {
            LeavingWarning(provider)

            Steps(provider)

            Text(
                text = stringResource(R.string.ai_get_privacy, provider.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Le bouton est en bas, apres ce qu'il faut avoir lu : le mettre en tete
            // ferait partir avant d'avoir su quoi faire une fois parti.
            //
            // **Il ouvre le site et referme cet ecran dans le meme geste** (D157) : au
            // retour du navigateur, on retombe sur le champ ou coller la cle, et non sur
            // des explications deja lues qu'il faudrait quitter d'un appui de plus.
            // L'ecran ne se referme que si le site s'est ouvert -- sans navigateur, il
            // reste la pour dire ce qu'il y avait a faire.
            NeonButton(
                text = stringResource(R.string.ai_get_open, provider.displayName),
                onClick = { runCatching { uriHandler.openUri(provider.consoleUrl) }.onSuccess { onClose() } },
                modifier = Modifier.fillMaxWidth(),
                style = NeonButtonStyle.FILLED,
            )

            NeonButton(
                text = stringResource(R.string.ai_get_back),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )

            // De l'air sous le dernier bouton : il colle au bord bas et se manque.
            Spacer(modifier = Modifier.padding(bottom = Spacing.xl))
        }
    }
}

/**
 * L'avertissement, **dans la carte d'erreur et avec son triangle**.
 *
 * Ce n'est pas une erreur, et la couleur d'erreur est pourtant la bonne : c'est la
 * seule du thème qui arrête l'œil, et ce que cette carte dit doit être lu avant qu'on
 * appuie. La forme accompagne la couleur — un triangle, un titre en gras —, comme
 * partout dans ce projet où une couleur ne travaille jamais seule.
 */
@Composable
private fun LeavingWarning(provider: AiProvider) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    // Muet : le titre qui suit dit la meme chose, et un lecteur
                    // d'ecran annoncerait deux fois le meme avertissement.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(WarningIconSize),
                )
                Text(
                    text = stringResource(R.string.ai_get_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = stringResource(R.string.ai_get_warning_body, provider.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.ai_get_warning_once),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Les quatre étapes, numérotées.
 *
 * **La deuxième dit « aucune carte bancaire » quand c'est vrai**, et se tait sinon. Le
 * mot vient de [AiProvider.free] et non d'une chaîne écrite ici : le jour où Google
 * ferme son palier gratuit, la promesse disparaît avec le drapeau, et l'écran ne
 * continue pas à rassurer à tort.
 */
@Composable
private fun Steps(provider: AiProvider) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.ai_get_steps_title),
                style = MaterialTheme.typography.titleMedium,
            )
            // Les numeros viennent du rang et non d'un litteral : en ajouter une au
            // milieu renumerote les suivantes toute seule.
            listOf(
                stringResource(R.string.ai_get_step_open, provider.displayName),
                if (provider.free) {
                    stringResource(R.string.ai_get_step_account_free)
                } else {
                    stringResource(R.string.ai_get_step_account)
                },
                stringResource(R.string.ai_get_step_create),
                stringResource(R.string.ai_get_step_paste),
            ).forEachIndexed { rang, texte -> Step(rang + 1, texte) }
            Text(
                text = stringResource(R.string.ai_get_step_copy_once),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Un numéro dans une pastille, et la phrase à côté. */
@Composable
private fun Step(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(StepBadgeSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

private val WarningIconSize: Dp = 28.dp

/** Assez pour qu'un chiffre y respire, assez peu pour que la phrase reste le sujet. */
private val StepBadgeSize: Dp = 28.dp
