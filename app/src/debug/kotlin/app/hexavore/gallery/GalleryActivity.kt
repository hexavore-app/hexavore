package app.hexavore.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * La galerie des composants, accessible depuis le lanceur en variante `debug`.
 *
 * Une seconde icône plutôt qu'un écran caché derrière un geste : elle se trouve
 * sans documentation, et elle n'existe pas du tout en `release` — pas masquée,
 * absente du binaire.
 *
 * C'est l'écran qui a servi à valider l'itération 0, et il continue de servir à
 * chaque composant ajouté au design system.
 *
 * **`AppCompatActivity` comme l'activité principale**, pour la même raison et non par
 * symétrie : elle porte le thème de fenêtre `Theme.Hexavore`, qui descend désormais de
 * `Theme.AppCompat` ([D129][decisions]). Une `ComponentActivity` sous ce thème démarre
 * encore, mais la galerie sert justement à regarder le design system dans les deux
 * thèmes et les deux langues — autant qu'elle le fasse par le même chemin que
 * l'application.
 *
 * [decisions]: docs/11-decisions.md
 */
@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryRoute()
        }
    }
}
