package app.hexavore

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hexavore.domain.goal.DailyGoal
import app.hexavore.domain.goal.Goal
import app.hexavore.domain.goal.GoalId
import app.hexavore.domain.goal.GoalOrigin
import app.hexavore.domain.goal.GoalStrategy
import app.hexavore.domain.profile.Activity
import app.hexavore.domain.profile.Sex
import app.hexavore.domain.profile.UserProfile
import app.hexavore.domain.profile.WeeklySessions
import app.hexavore.domain.profile.WorkActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.hexavore.feature.home.R as HomeStrings

/**
 * Le tour guidé du premier lancement, joué jusqu'au bout.
 *
 * ### Pourquoi ce fichier existe
 *
 * Un utilisateur sur Galaxy S24 (One UI 8.5) a renseigné les cinq questions de
 * l'onboarding, puis s'est retrouvé devant le tour **sans pouvoir en sortir** : ni
 * « Passer » ni « Suivant » ne répondaient, et le tour revenait à chaque lancement parce
 * que le souvenir ne s'écrit qu'à la fin ([TourViewModel.onFinish]).
 *
 * Rien dans la chaîne ne pouvait le voir. [AppJourneyTest.congedieLeTour] clique sur
 * « Passer » **sans vérifier que le tour est parti**, puis attend l'accueil — qui est
 * déjà là, sous le voile, et dont l'arbre d'accessibilité ignore le voile. Un clic avalé
 * y ressemble donc trait pour trait à un clic reçu.
 *
 * ### Ce qu'il tient, et ce qu'il ne tient pas
 *
 * Il ne juge **ni la mise en page ni les textes** : les bulles ont leur propre
 * relecture, et un test accroché à une tournure de phrase casserait à la première
 * reformulation. Il répond à trois questions, et à trois seulement :
 *
 * 1. Le tour **s'en va** quand on le lui demande — au clic, et au doigt qui tremble.
 * 2. Il **se traverse en entier**, et ses boutons restent atteignables à chaque étape.
 * 3. Une fois répondu, il **ne revient pas**.
 *
 * Plus une quatrième, qui est le garde-fou de la correction à venir : le voile doit
 * continuer de **retenir ce qui est dessous**. Une correction qui rendrait les boutons
 * de la bulle cliquables en laissant passer le reste aurait déplacé le problème.
 *
 * ### Chaque méthode veut une installation neuve
 *
 * Le tour ne se montre qu'à qui ne l'a pas vu, et [TourSettings] n'offre aucun moyen de
 * l'oublier — c'est voulu côté produit, et c'est une contrainte ici. Ces tests
 * **exigent donc `clearPackageData`**, c'est-à-dire l'orchestrateur : sans lui, seule la
 * première méthode exécutée voit un tour, et les autres passent au vert sans rien avoir
 * regardé.
 *
 * Le profil et l'objectif sont écrits par le graphe réel avant chaque test, comme dans
 * [AppJourneyTest] : sans objectif, l'application démarre sur l'onboarding et le tour
 * n'arrive jamais. Le parcours qui traverse l'onboarding pour de vrai est
 * [PremierLancementTest].
 */
@RunWith(AndroidJUnit4::class)
class TourJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val ecran by lazy { Ecran(instrumentation) }

    private val graph: JourneyGraph
        get() = EntryPointAccessors.fromApplication(
            instrumentation.targetContext.applicationContext,
            JourneyGraph::class.java,
        )

    /**
     * **La permission de notifier est accordée d'avance**, pour que sa boîte ne vienne pas.
     *
     * L'accueil la demande à son premier affichage ([FirstRunNotificationRequest]), et la
     * boîte du système se pose **par-dessus le tour** : elle possède l'entrée, l'accueil ne
     * répond plus, et ces tests-ci regardaient alors une boîte au lieu d'une bulle.
     *
     * La collision est réelle et elle a son propre test dans [PremierLancementTest], qui
     * parcourt le premier lancement tel qu'il arrive. Ici on l'écarte : ce fichier répond à
     * une question — *le tour se laisse-t-il congédier* — et une boîte système devant lui
     * empêche d'y répondre.
     */
    @Before
    fun laPermissionDeNotifierEstAccordee() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @Before
    fun unProfilExisteEtLeTourNaPasEteVu() = runBlocking {
        val aujourdHui = graph.clock().today()
        graph.profiles().save(
            UserProfile(
                birthDate = aujourdHui.minusYears(AGE),
                sex = Sex.UNSPECIFIED,
                heightCm = TAILLE_CM,
                activity = Activity(WorkActivity.DESK, WeeklySessions(SEANCES)),
            ),
        )
        graph.goals().replace(
            Goal(
                id = GoalId(graph.ids().next()),
                startedAt = aujourdHui,
                origin = GoalOrigin.CALCULATED,
                strategy = GoalStrategy.MAINTAIN,
                daily = DailyGoal(
                    kcal = KCAL,
                    protein = PROTEINES,
                    carbs = GLUCIDES,
                    sugars = SUCRES,
                    fat = LIPIDES,
                    fiber = FIBRES,
                ),
            ),
        )

        // **La preuve que l'installation est neuve**, et non une mise en place. Si le
        // souvenir est deja pose, aucun tour ne se montrera et tout ce fichier passerait
        // au vert en regardant un accueil ordinaire. Mieux vaut le dire ici, une fois,
        // que laisser six methodes mentir.
        val dejaVu = graph.tour().observeSeen().first()
        assertFalse(
            "Le tour a deja ete vu sur cette installation : ces tests demandent " +
                "clearPackageData (orchestrateur). Voir le KDoc de la classe.",
            dejaVu,
        )
    }

    /** Le point de départ de tout le reste : sur une installation neuve, le tour arrive. */
    @Test
    fun le_tour_se_montre_au_premier_lancement() {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)
        ecran.attend(HomeStrings.string.tour_skip)
        ecran.attend(HomeStrings.string.tour_next)
    }

    /**
     * **« Passer » passe — au clic immobile.**
     *
     * ### C'est le témoin, et il est vert
     *
     * Le clic d'UiAutomator enfonce et relâche **au même pixel**. Ce test passe, et son
     * jumeau [passer_congedie_le_tour_quand_le_doigt_bouge] échoue : même bouton, même
     * état, même instant — un seul pixel de mouvement les sépare.
     *
     * Ces deux méthodes ne se doublonnent donc pas, elles **encadrent** le défaut. Garder
     * celle-ci verte est ce qui prouve que l'échec de l'autre vient du geste et de rien
     * d'autre — ni d'une bulle mal placée, ni d'une boîte système, ni d'un libellé changé.
     */
    @Test
    fun passer_congedie_le_tour() {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)

        ecran.clique(ecran.texte(HomeStrings.string.tour_skip))

        ecran.attendLaDisparition(HomeStrings.string.tour_day_title)
        ecran.attendLaDisparition(HomeStrings.string.tour_skip)
    }

    /**
     * **Et il passe aussi quand le doigt tremble.**
     *
     * Le même geste, écrit à la main pour que l'exigence soit dans le test et non dans
     * l'implémentation d'UiAutomator : enfoncé, un pixel de côté, relâché. C'est ce que
     * rend un vrai téléphone, et c'est ce qu'un clic de souris sur un émulateur ne rend
     * pas — d'où un tour qui marchait sur la machine de développement et nulle part
     * ailleurs.
     */
    @Test
    fun passer_congedie_le_tour_quand_le_doigt_bouge() {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)

        ecran.appuieCommeUnDoigt(ecran.texte(HomeStrings.string.tour_skip))

        ecran.attendLaDisparition(HomeStrings.string.tour_day_title)
        ecran.attendLaDisparition(HomeStrings.string.tour_skip)
    }

    /**
     * **Le retour du système congédie le tour**, et pose le souvenir.
     *
     * ### La sortie de secours, et pourquoi elle est à part
     *
     * Elle ne passe pas par les appuis. Le geste de retour arrive par l'activité, là où le
     * voile n'intercepte rien — c'est donc la seule porte qui tienne **quelle que soit** la
     * panne du côté des gestes, et la seule qui marchait déjà quand plus rien d'autre ne
     * marchait.
     *
     * ### Le souvenir compte autant que la disparition
     *
     * Un retour qui ferait seulement disparaître la bulle ne libérerait personne : le tour
     * reviendrait au lancement suivant, et l'utilisateur serait repris au même endroit.
     * C'est exactement le piège signalé. Ce test vérifie donc les deux.
     */
    @Test
    fun le_retour_arriere_congedie_le_tour() = runBlocking {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)

        ecran.revientEnArriere()

        ecran.attendLaDisparition(HomeStrings.string.tour_day_title)
        ecran.attendLaDisparition(HomeStrings.string.tour_skip)
        assertTrue(
            "Le tour est parti au retour arriere, mais le souvenir n'a pas ete ecrit : " +
                "il reviendra au prochain lancement.",
            graph.tour().observeSeen().first(),
        )
    }

    /** **« Suivant » avance.** La première bulle s'en va, la deuxième arrive. */
    @Test
    fun suivant_fait_avancer_le_tour() {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)

        ecran.appuieCommeUnDoigt(ecran.texte(HomeStrings.string.tour_next))

        ecran.attend(HomeStrings.string.tour_calories_title)
        ecran.attendLaDisparition(HomeStrings.string.tour_day_title)
    }

    /**
     * Le tour entier, bulle après bulle, jusqu'à « J'ai compris ».
     *
     * ### Il vérifie deux choses à chaque étape
     *
     * Que la bulle **annoncée** est bien celle qui s'affiche, et que son bouton reste
     * **entièrement à l'écran**. La deuxième n'est pas une coquetterie : la bulle se
     * place par un calcul qui a déjà déraillé deux fois (D150, D151), et une bulle dont
     * le bas sort de l'écran emporte ses deux boutons avec elle — ce qui, vu du
     * téléphone, est le même blocage que celui qu'on corrige ici.
     *
     * ### Sans clé, le tour finit par l'étape dégradée
     *
     * Une installation neuve n'a pas de clé d'IA, donc l'étape « Gemini est gratuit »
     * propose « Plus tard », et le tour enchaîne sur ce que ce refus coûte. C'est le
     * chemin que prend l'écrasante majorité des premiers lancements, et donc celui que
     * ce test parcourt.
     */
    @Test
    fun le_tour_se_raconte_en_entier_puis_s_en_va() {
        ouvreLAccueil()

        ETAPES.forEach { titre ->
            ecran.attend(titre)
            // Le bouton qui fait avancer, et la preuve qu'on peut l'atteindre.
            val suite = ecran.texte(if (titre == HomeStrings.string.tour_ai_title) LATER else NEXT)
            exigeUnBoutonEntierementVisible(suite, ecran.texte(titre))
            ecran.appuieCommeUnDoigt(suite)
        }

        // La derniere : sans cle, c'est l'etape degradee, et elle n'offre que  J'ai compris .
        ecran.attend(HomeStrings.string.tour_degraded_title)
        val fin = ecran.texte(HomeStrings.string.tour_done)
        exigeUnBoutonEntierementVisible(fin, ecran.texte(HomeStrings.string.tour_degraded_title))
        ecran.appuieCommeUnDoigt(fin)

        ecran.attendLaDisparition(HomeStrings.string.tour_degraded_title)
        // Et l'accueil repond de nouveau : le voile est bien parti, pas seulement la bulle.
        ecran.clique(ecran.texte(HomeStrings.string.home_more_ways))
        ecran.attend(HomeStrings.string.home_add_by_hand)
    }

    /**
     * **Répondu une fois, jamais revu.**
     *
     * L'assertion porte sur le **souvenir écrit** et non sur un second lancement dans le
     * même processus : [TourViewModel] garde en mémoire de processus qu'on lui a déjà
     * répondu, si bien qu'un `ActivityScenario.launch` de plus ne montrerait pas le tour
     * même si rien n'avait été écrit sur le téléphone. Le test passerait au vert en
     * prouvant le contraire de ce qu'il cherche.
     *
     * C'est précisément ce qui a manqué à l'utilisateur : son tour revenait parce que le
     * souvenir ne s'écrit qu'à la fin, et qu'il n'a jamais pu l'atteindre.
     */
    @Test
    fun le_tour_ne_revient_pas_apres_avoir_ete_passe() = runBlocking {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)

        ecran.appuieCommeUnDoigt(ecran.texte(HomeStrings.string.tour_skip))
        ecran.attendLaDisparition(HomeStrings.string.tour_day_title)

        assertTrue(
            "Le tour est parti de l'ecran mais le souvenir n'a pas ete ecrit : il " +
                "reviendra au prochain lancement.",
            graph.tour().observeSeen().first(),
        )
    }

    /**
     * Le garde-fou de la correction : **le voile retient toujours ce qui est dessous**.
     *
     * Pendant un tour, les seuls boutons qui répondent sont ceux de la bulle (D141). Une
     * correction qui rendrait la bulle cliquable en laissant passer le reste ouvrirait la
     * recherche au milieu d'une phrase qui parle d'autre chose — et ce test-ci est celui
     * qui s'y opposerait.
     *
     * Il passe aujourd'hui. Il est écrit maintenant **parce qu'il passe** : c'est la
     * propriété qu'on risque de casser en réparant l'autre.
     */
    @Test
    fun le_voile_retient_ce_qui_est_dessous() {
        ouvreLAccueil()
        ecran.attend(HomeStrings.string.tour_day_title)

        // Le  +  de la barre du bas est visible pour l'arbre d'accessibilite, voile ou
        // pas : c'est justement ce qui rend ce test necessaire, et ce qui a rendu
        // l'ancien aveugle.
        ecran.appuieCommeUnDoigt(ecran.texte(HomeStrings.string.home_more_ways))
        ecran.seRepose()

        assertFalse(
            "La feuille d'ajout s'est ouverte pendant le tour : le voile ne retient plus rien." +
                System.lineSeparator() + "Il montrait : " + ecran.ceQuOnVoit(),
            ecran.estLa(ecran.texte(HomeStrings.string.home_add_by_hand)),
        )
        // Et la bulle, elle, est toujours la : rien n'a ete congedie par ce geste.
        assertTrue(ecran.estLa(ecran.texte(HomeStrings.string.tour_day_title)))
    }

    /**
     * Ouvre l'accueil, et attend que le tour **soit posé**.
     *
     * Le tour arrive après l'écran qu'il recouvre : il lit un réglage, puis pose ses plats
     * d'exemple. L'attendre par son titre plutôt que par l'accueil est la seule façon de
     * ne pas commencer à cliquer pendant qu'il se met en place.
     */
    private fun ouvreLAccueil() {
        ActivityScenario.launch(MainActivity::class.java)
        ecran.attend(HomeStrings.string.home_more_ways)
        ecran.attend(HomeStrings.string.tour_day_title)
        ecran.seRepose()
    }

    /**
     * Ce bouton est-il **entièrement** dans l'écran, marges système comprises ?
     *
     * `visibleBounds` et non `bounds` : UiAutomator donne les deux, et c'est la portion
     * visible qui dit si un doigt peut l'atteindre. Un bouton dont la hauteur visible est
     * nulle, ou qui touche le bord, est un bouton qu'on ne peut pas viser.
     */
    private fun exigeUnBoutonEntierementVisible(bouton: String, etape: String) {
        // **On attend le bouton avant de le mesurer.** La bulle se pose en deux temps :
        // elle se place a zero le temps d'une image, puis se repositionne une fois sa
        // hauteur connue (D147). Mesurer sans attendre, c'est mesurer pendant ce
        // battement -- et l'etape de l'IA, la seule qui ne designe rien et se centre, est
        // celle ou il se voyait.
        ecran.attendLeLibelle(bouton)

        val cadre = ecran.cadre(bouton)
        assertTrue(
            "L'etape  $etape  ne montre pas  $bouton  dans l'ecran." +
                System.lineSeparator() + "Il montrait : " + ecran.ceQuOnVoit(),
            cadre != null,
        )
        requireNotNull(cadre)
        assertTrue(
            "L'etape  $etape  pose  $bouton  hors de l'ecran : $cadre pour une hauteur de " +
                "${ecran.hauteurEcran}.",
            cadre.height() > 0 && cadre.top >= 0 && cadre.bottom <= ecran.hauteurEcran,
        )
        assertTrue(
            "L'etape  $etape  pose  $bouton  hors de l'ecran en largeur : $cadre pour " +
                "${ecran.largeurEcran}.",
            cadre.width() > 0 && cadre.left >= 0 && cadre.right <= ecran.largeurEcran,
        )
    }

    private companion object {
        const val AGE = 30L
        const val TAILLE_CM = 175.0
        const val SEANCES = 2

        const val KCAL = 2400.0
        const val PROTEINES = 120.0
        const val GLUCIDES = 280.0
        const val SUCRES = 60.0
        const val LIPIDES = 80.0
        const val FIBRES = 30.0

        val NEXT = HomeStrings.string.tour_next
        val LATER = HomeStrings.string.tour_later

        /**
         * Les étapes qui portent un bouton d'avancement, dans l'ordre du tour.
         *
         * **Écrites et non déduites de `TourStep`** : l'énumération vit dans
         * `:feature:home` et n'est pas visible d'ici, et surtout un test qui lirait la
         * même liste que le code vérifierait que le code est d'accord avec lui-même. Une
         * étape ajoutée fait échouer ce test, ce qui est exactement le rappel voulu —
         * une bulle de plus est une bulle de plus à traverser au doigt.
         */
        val ETAPES = listOf(
            HomeStrings.string.tour_day_title,
            HomeStrings.string.tour_calories_title,
            HomeStrings.string.tour_protein_title,
            HomeStrings.string.tour_fiber_title,
            HomeStrings.string.tour_carbs_title,
            HomeStrings.string.tour_sugars_title,
            HomeStrings.string.tour_fat_title,
            HomeStrings.string.tour_calendar_title,
            HomeStrings.string.tour_describe_title,
            HomeStrings.string.tour_photo_title,
            HomeStrings.string.tour_more_title,
            HomeStrings.string.tour_settings_title,
            HomeStrings.string.tour_ai_title,
        )
    }
}
