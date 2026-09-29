package app.hexavore

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.language.LanguageSettings
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSettings
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
class HexavoreApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var sweepPhotos: SweepDishPhotos

    @Inject
    lateinit var languages: LanguageSettings

    @Inject
    lateinit var reminders: ReminderSettings

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * La fabrique de travailleurs, pour que `WorkManager` passe par Hilt.
     *
     * Sans elle, un `@HiltWorker` est instancié par la fabrique par défaut, qui ne sait
     * rien de ses dépendances : le rappel échouerait à chaque exécution, dans un
     * journal que personne ne lit.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

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
     * **La langue, elle, est appliquee ici et tout de suite.** Pas dans une coroutine :
     * elle doit etre en place avant que la premiere activite resolve ses ressources, et
     * un `launch` la poserait une image trop tard -- l'accueil s'ouvrirait dans la langue
     * du systeme avant de basculer, ce qui se voit exactement comme le clignotement de
     * theme que D113 a evite. La lecture est une preference deja en memoire, et le plus
     * souvent il n'y a rien a appliquer du tout (D129).
     *
     * [ia]: docs/05-ia.md
     */
    override fun onCreate() {
        super.onCreate()
        languages.restore()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            sweepCapturePhotos(this@HexavoreApplication)
            sweepPhotos()
            // Les rappels se reposent a chaque lancement. C'est ce qui les fait
            // survivre a un redemarrage du telephone : `WorkManager` garde ses
            // travaux, mais un travail unique deja passe ne se replanifie pas tout
            // seul si le processus a ete tue avant qu'il s'execute. Replacer ce qui
            // est deja place ne coute rien -- `REPLACE` y pourvoit (D134).
            runCatching { scheduler.reschedule(reminders.current()) }
        }
    }
}
