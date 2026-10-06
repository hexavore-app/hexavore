package app.hexavore

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.fail

/**
 * Ce que l'écran montre, et ce qu'on lui fait.
 *
 * ### Pourquoi une classe plutôt que six méthodes recopiées
 *
 * [AppJourneyTest] et [OnboardingJourneyTest] portent chacun sa copie de `attend`, de
 * `voit`, de `clique` et du diagnostic qui les accompagne. Une troisième classe de
 * parcours en aurait fait une troisième, et elles divergent déjà — les deux `clique`
 * existants ne réessaient pas le même nombre de fois.
 *
 * ### Elle sait aussi dire qu'une chose **est partie**
 *
 * C'est ce qui manquait, et c'est ce qui a laissé passer le blocage du tour guidé. Le
 * renvoi du tour s'écrivait « clique sur Passer, puis attends l'accueil » — or l'accueil
 * est **déjà là**, sous le voile, et son arbre d'accessibilité ne dit rien du voile posé
 * dessus. Le test regardait donc un écran qu'il croyait libre, et le clic qui n'avait
 * rien congédié passait pour un succès.
 *
 * [attendLaDisparition] est la seule forme qui prouve qu'un geste a porté.
 */
internal class Ecran(private val instrumentation: Instrumentation) {
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    fun texte(id: Int): String = instrumentation.targetContext.getString(id)

    /** Ce libellé est-il à l'écran **à l'instant** ? Sans attendre, pour pouvoir le nier. */
    fun estLa(libelle: String): Boolean = device.hasObject(By.textContains(libelle)) ||
        device.hasObject(By.descContains(libelle))

    /**
     * Ce libellé paraît-il, **en texte ou en description** ?
     *
     * Les deux, parce que Compose expose les deux : une entrée de la feuille d'ajout fond
     * son titre et son explication dans une seule description, et le même libellé est du
     * texte partout ailleurs.
     */
    fun voit(libelle: String): Boolean = device.wait(Until.hasObject(By.textContains(libelle)), PAS_MS) == true ||
        device.wait(Until.hasObject(By.descContains(libelle)), PAS_MS) == true

    fun attend(id: Int) = attendLeLibelle(texte(id))

    /**
     * Attend qu'un libellé paraisse, et échoue **en nommant ce qu'il y avait à la place**.
     *
     * Un nombre d'essais et non une échéance : lire l'horloge système est ce que ce dépôt
     * évite partout ailleurs, et chaque essai attend déjà par lui-même.
     */
    fun attendLeLibelle(libelle: String) {
        repeat(ESSAIS) { if (voit(libelle)) return }
        fail("L'ecran n'a jamais montre : $libelle" + System.lineSeparator() + "Il montrait : " + ceQuOnVoit())
    }

    fun attendLaDisparition(id: Int) = attendLaDisparitionDuLibelle(texte(id))

    /**
     * Attend qu'un libellé **s'en aille**, et échoue en disant qu'il est resté.
     *
     * C'est l'assertion que demande tout geste censé refermer quelque chose : une bulle
     * qu'on passe, une feuille qu'on referme, un voile qui se lève. Sans elle, un geste
     * avalé par un parent se lit comme un geste reçu.
     */
    fun attendLaDisparitionDuLibelle(libelle: String) {
        repeat(ESSAIS) {
            if (!estLa(libelle)) return
            device.waitForIdle(PAS_MS)
        }
        fail(
            "Toujours a l'ecran apres $ESSAIS essais : $libelle" + System.lineSeparator() +
                "Il montrait : " + ceQuOnVoit(),
        )
    }

    /**
     * Cherche, puis clique — et recommence si la cible a bougé entre les deux.
     *
     * Une liste se recompose pendant qu'on la regarde : le nœud trouvé à l'instant d'avant
     * n'existe déjà plus, et UiAutomator le dit en jetant. Un doigt qui rate recommence,
     * lui aussi.
     */
    fun clique(libelle: String) {
        repeat(ESSAIS) {
            val cible = trouve(libelle)
            if (cible != null && runCatching { cible.click() }.isSuccess) return
            device.waitForIdle(PAS_MS)
        }
        fail("Jamais cliquable : $libelle" + System.lineSeparator() + "Il montrait : " + ceQuOnVoit())
    }

    /**
     * Appuie **comme un doigt**, et non comme une souris posée sur un pixel.
     *
     * ### Pourquoi la différence compte
     *
     * Un vrai doigt tremble. Entre l'instant où il touche la vitre et celui où il la
     * quitte, le numériseur d'un téléphone rend au moins un `ACTION_MOVE`, et souvent
     * dix. Un clic de souris sur un émulateur, lui, n'en rend aucun : enfoncé puis
     * relâché au même pixel.
     *
     * Ce sont donc **deux gestes différents** pour Compose, et un parent qui consomme les
     * mouvements n'annule que le second. Vérifier le tour guidé à la souris revient à
     * vérifier le seul geste qu'aucun utilisateur ne fait.
     *
     * `sendPointerSync` et non `UiObject2.click()` : celui-ci rend bien des mouvements,
     * mais c'est un détail de son implémentation et non une promesse. Un test qui pose une
     * exigence sur la forme du geste la pose lui-même.
     */
    fun appuieCommeUnDoigt(libelle: String) {
        val cible = requireNotNull(trouve(libelle)) {
            "Introuvable : $libelle" + System.lineSeparator() + "Il montrait : " + ceQuOnVoit()
        }
        val point = cible.visibleBounds
        appuie(point.centerX().toFloat(), point.centerY().toFloat())
        device.waitForIdle(PAS_MS)
    }

    private fun appuie(x: Float, y: Float) {
        // `uptimeMillis` est la base de temps que `MotionEvent` exige ; il n'y a pas
        // d'autre horloge acceptable ici, et celle-ci ne decide de rien.
        val depart = SystemClock.uptimeMillis()

        fun geste(action: Int, decalage: Float, apres: Long): MotionEvent =
            MotionEvent.obtain(depart, depart + apres, action, x + decalage, y + decalage, 0)

        listOf(
            geste(MotionEvent.ACTION_DOWN, 0f, 0),
            geste(MotionEvent.ACTION_MOVE, TREMBLEMENT, 1),
            geste(MotionEvent.ACTION_UP, TREMBLEMENT, 2),
        ).forEach { evenement ->
            runCatching { instrumentation.sendPointerSync(evenement) }
            evenement.recycle()
        }
    }

    /** Le rectangle qu'occupe ce libellé, ou `null` s'il n'est pas là. */
    fun cadre(libelle: String): android.graphics.Rect? = trouve(libelle)?.visibleBounds

    /**
     * Les champs de saisie de l'écran, **dans l'ordre où l'arbre les donne**.
     *
     * Par la classe et non par le libellé : ce que Compose expose sous « Taille (cm) » est
     * l'étiquette, et écrire dedans n'écrit nulle part. Un champ de saisie se présente
     * comme un `EditText` à l'accessibilité, quelle que soit la version d'Android.
     *
     * L'ordre est celui de la composition, donc celui de lecture de l'écran. C'est une
     * hypothèse, et l'appelant doit la vérifier en comptant ce qu'il reçoit plutôt que de
     * remplir à l'aveugle le troisième champ d'une liste qui n'en a que deux.
     */
    fun champsDeSaisie(): List<UiObject2> = device.findObjects(By.clazz(SAISIE))

    val hauteurEcran: Int get() = device.displayHeight

    val largeurEcran: Int get() = device.displayWidth

    /** Referme le clavier, et seulement s'il est ouvert. */
    fun fermeLeClavier() {
        val ouvert = instrumentation.uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        if (ouvert) {
            device.pressBack()
            device.waitForIdle(PAS_MS)
        }
    }

    fun seRepose() = device.waitForIdle(REPOS_MS)

    fun revientEnArriere() {
        device.pressBack()
        device.waitForIdle(PAS_MS)
    }

    /**
     * Ce que l'écran porte, pour le dire quand on ne trouve pas ce qu'on cherchait.
     *
     * Un test qui échoue sur sept versions d'Android doit dire **où il s'est arrêté**, et
     * pas seulement qu'il s'est arrêté : sans cela, chaque échec demande de rejouer la
     * session à la main sur l'émulateur concerné.
     */
    fun ceQuOnVoit(): String {
        val tout = Regex(".+").toPattern()
        return runCatching {
            device.findObjects(By.text(tout)).mapNotNull { runCatching { it.text }.getOrNull() } +
                device.findObjects(By.desc(tout)).mapNotNull { runCatching { it.contentDescription }.getOrNull() }
        }.getOrDefault(emptyList()).distinct().joinToString(separator = " | ").take(EXTRAIT)
    }

    private fun trouve(libelle: String): UiObject2? =
        device.findObject(By.textContains(libelle)) ?: device.findObject(By.descContains(libelle))

    internal companion object {
        /** Vingt essais d'une demi-seconde : vingt secondes par recherche. */
        const val ESSAIS = 20
        const val PAS_MS = 500L

        /** Le temps qu'on laisse a un ecran qui se compose avant d'y toucher. */
        const val REPOS_MS = 1_500L

        /**
         * Un pixel de tremblement, et c'est assez.
         *
         * Le seuil de glissement de Compose est bien plus haut : ce mouvement-ci ne
         * transforme pas l'appui en glissement, il le rend seulement **réel**.
         */
        const val TREMBLEMENT = 1f

        /** Ce qu'un champ de saisie Compose annonce a l'arbre d'accessibilite. */
        const val SAISIE = "android.widget.EditText"

        const val EXTRAIT = 1_200
    }
}
