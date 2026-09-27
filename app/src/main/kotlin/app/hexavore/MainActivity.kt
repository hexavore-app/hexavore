package app.hexavore

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hexavore.core.designsystem.theme.NeonTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * L'unique activité de l'application.
 *
 * Elle ouvre sur l'accueil. La galerie des composants existe toujours, dans une
 * activité déclarée par la seule variante `debug` : elle sert à vérifier le design
 * system sur un appareil réel, et n'a rien à faire dans un binaire de production.
 *
 * **Le thème se lit ici et nulle part ailleurs.** C'est le seul endroit qui enveloppe
 * tout ce qui s'affiche ; le poser plus bas laisserait un écran hors du réglage, et on
 * ne s'en apercevrait qu'en l'ouvrant.
 *
 * **`AppCompatActivity` et non `ComponentActivity`**, et pour une seule raison : la
 * langue. En dessous d'Android 13, `AppCompatDelegate.setApplicationLocales` n'applique
 * la langue qu'aux activités qui passent par son délégué — la documentation d'Android le
 * dit sans détour pour Compose. Avec `minSdk 26`, s'en passer aurait laissé le réglage
 * sans effet sur une part importante du parc ([D129][decisions]).
 *
 * **La langue ne se lit pas ici**, contrairement au thème. Elle est appliquée avant que
 * la première activité s'attache — voir [HexavoreApplication] — parce qu'une langue posée
 * dans `onCreate` arriverait après que les ressources de l'écran ont été résolues.
 *
 * [decisions]: docs/11-decisions.md
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val theme: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by theme.mode.collectAsStateWithLifecycle()

            // Le reglage d'Android arrive ici, et la decision est prise par le domaine :
            // « suivre le systeme » n'a alors qu'un seul endroit ou etre defini.
            NeonTheme(darkTheme = mode.isDark(isSystemInDarkTheme())) {
                HexavoreNavHost()
            }
        }
    }
}
