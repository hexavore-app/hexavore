package app.hexavore.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La photo d'un plat, lue à la taille où on la regarde.
 *
 * **Le décodeur ne lit qu'un pixel sur n.** Une photo de repas fait 1 024 px de côté,
 * et l'accueil en montre une vignette de 56 dp : la décoder entière coûterait quatre
 * mégaoctets de mémoire par ligne de liste, pour n'en afficher qu'un cinquantième.
 * `inSampleSize` le règle à la source, et c'est [BoxWithConstraints] qui dit à quelle
 * taille, parce que c'est la mise en page qui sait.
 *
 * **Rien ne s'affiche tant que rien n'est lu, et rien ne s'affiche si la lecture
 * échoue.** Un cadre vide est la bonne réponse à un fichier disparu : la photo est un
 * accessoire, et son absence n'est pas une erreur dont il y ait quelque chose à dire.
 *
 * **Le fichier, et pas des octets.** Le port des photos rend un chemin pour cette
 * raison précise : porter l'image entière en mémoire jusqu'ici annulerait tout le
 * bénéfice de l'échantillonnage.
 *
 * @param path ce que `DishPhotos` a rendu. Le composant ne le fabrique jamais.
 * @see app.hexavore.domain.diary.DishPhotos
 */
@Composable
fun DishPhoto(path: String, contentDescription: String?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val target = longestSide(constraints)
        // `remember` cle sur le fichier et sur la taille, plutot qu'un `produceState` :
        // l'image repart a null des que l'un des deux change, ce qui evite qu'une
        // vignette recyclee montre un instant celle du plat precedent.
        var image by remember(path, target) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(path, target) {
            image = withContext(Dispatchers.IO) { decodeAtMost(path, target) }
        }

        image?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                // `Crop` et non `Fit` : le cadre a une forme, et une assiette qui le
                // remplit se reconnait mieux qu'une assiette posee sur des bandes vides.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Le côté le plus long que la mise en page accorde, en pixels.
 *
 * Une contrainte peut être infinie — une hauteur dans une colonne défilante — et un
 * infini ne dit rien du nombre de pixels à lire. Le repli est la taille pleine de
 * l'image : mieux vaut la décoder en entier que de la décoder pour rien.
 */
private fun longestSide(constraints: Constraints): Int {
    val sides = listOfNotNull(
        constraints.maxWidth.takeIf { constraints.hasBoundedWidth },
        constraints.maxHeight.takeIf { constraints.hasBoundedHeight },
    )
    return sides.maxOrNull()?.takeIf { it > 0 } ?: FULL_SIZE
}

/**
 * L'image décodée, sans jamais lire plus de pixels que nécessaire.
 *
 * Deux passes, et l'ordre est ce qui empêche la mémoire d'exploser : mesurer sans
 * décoder, puis décoder en sautant des pixels. C'est le même raisonnement que la
 * réduction d'une photo avant de l'envoyer à un modèle, dans `:feature:capture`.
 *
 * `null` sur un fichier absent ou illisible. L'appelant n'affiche alors rien.
 */
private fun decodeAtMost(path: String, maxSide: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), maxSide)
    }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()

/**
 * La puissance de deux qui ramène [source] sous [target] sans descendre en dessous.
 *
 * Des puissances de deux parce que `BitmapFactory` n'accepte que celles-là, et arrondir
 * vers le bas parce qu'une image sous-échantillonnée à moitié de la taille demandée se
 * voit floue sur un écran dense.
 */
private fun sampleSizeFor(source: Int, target: Int): Int {
    var sample = 1
    while (source / (sample * 2) >= target) sample *= 2
    return sample
}

/** Ce qu'on décode quand la mise en page ne borne rien : le côté long d'une photo gardée. */
private const val FULL_SIZE = 1024
