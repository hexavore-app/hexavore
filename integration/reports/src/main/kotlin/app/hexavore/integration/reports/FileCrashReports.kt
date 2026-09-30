package app.hexavore.integration.reports

import android.content.Context
import android.os.Build
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.report.CrashReports
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La trace du dernier plantage, dans un fichier.
 *
 * **Un fichier et non une préférence** : on y écrit depuis un gestionnaire d'exception
 * non rattrapée, c'est-à-dire depuis un processus qui va mourir dans l'instant. Un
 * `SharedPreferences.commit()` y arriverait sans doute ; un flux, un dispatcher ou une
 * base de données, non — ils supposent tous que quelque chose survive à l'appel.
 *
 * **Une seule trace**, la plus récente : quelqu'un qui plante trois fois de suite
 * n'enverra pas trois courriels, et la troisième dit ce que les deux autres disaient.
 */
@Singleton
class FileCrashReports @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CrashReports {
    private val file: File get() = File(context.cacheDir, FILE_NAME)

    override suspend fun pending(): String? = withContext(dispatchers.io) {
        runCatching { file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        runCatching { file.delete() }
        Unit
    }

    /**
     * Note ce qui vient de tomber. **Ne suspend pas, et n'échoue jamais.**
     *
     * Appelée sur le fil qui meurt : lui demander une coroutine reviendrait à espérer
     * qu'un ordonnanceur tourne encore. Et une exception lancée depuis un gestionnaire
     * d'exceptions ferait perdre la trace qu'on essayait justement de garder.
     */
    fun record(thread: Thread, error: Throwable) {
        runCatching {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            file.writeText(
                """
                |Hexavore ${BuildConfig.VERSION_LABEL}
                |Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                |${Build.MANUFACTURER} ${Build.MODEL}
                |Fil : ${thread.name}
                |
                |$trace
                """.trimMargin(),
            )
        }
    }

    private companion object {
        const val FILE_NAME = "dernier-plantage.txt"
    }
}
