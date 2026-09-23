package app.hexavore

import android.app.Application
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.usecase.SweepDishPhotos
import app.hexavore.feature.capture.sweepCapturePhotos
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Racine du graphe d'injection.
 *
 * Chaque module Gradle expose son propre module Hilt et lie ses adaptateurs aux
 * ports du domaine ; `:app` ne fait qu'assembler. C'est ce qui permet de remplacer
 * une implémentation en mémoire par Room en changeant une seule ligne.
 *
 * @see docs/06-architecture.md
 */
@HiltAndroidApp
class HexavoreApplication : Application() {
    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var sweepPhotos: SweepDishPhotos

    /**
     * Les deux ménages du démarrage, et ils ne ramassent pas la même chose.
     *
     * Le premier vide le **cache de capture** : une photo en route vers un modèle est
     * supprimée dès qu'elle est lue, dans un `finally`, et ce balayage couvre le cas
     * anormal où le processus est tué entre le déclencheur et la lecture
     * ([docs/05][ia]).
     *
     * Le second retire les **photos de plats qui n'existent plus**. Une photo survit à
     * la suppression de son plat le temps que la barre d'annulation soit passée, et à
     * une restauration le temps qu'on puisse revenir à la copie de sécurité : c'est
     * ici, au lancement suivant, que ce qui n'a plus de plat à décrire s'en va.
     *
     * **Hors du fil principal**, parce qu'ils touchent au disque, et sans rien
     * attendre : personne ne dépend de leur résultat.
     *
     * [ia]: docs/05-ia.md
     */
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            sweepCapturePhotos(this@HexavoreApplication)
            sweepPhotos()
        }
    }
}
