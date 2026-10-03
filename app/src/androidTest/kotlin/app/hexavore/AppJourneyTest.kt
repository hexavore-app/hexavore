package app.hexavore

import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.hexavore.feature.entry.R as EntryStrings
import app.hexavore.feature.home.R as HomeStrings
import app.hexavore.feature.search.R as SearchStrings
import app.hexavore.feature.settings.R as SettingsStrings

/**
 * Le tour de l'application, sur l'appareil qui l'exécute.
 *
 * ### Ce qu'il cherche, et ce qu'il ne cherche pas
 *
 * **Il cherche les écrans qui ne s'ouvrent pas.** Un utilisateur sous Android 11 a vu la
 * saisie et l'édition se fermer d'un coup, là où les mêmes écrans tiennent sous Android
 * 15 ([D139][decisions]) : ce genre de panne ne se voit ni à la compilation, ni dans un
 * test unitaire, ni dans un `lint` — elle ne se voit qu'en ouvrant l'écran sur la
 * version concernée.
 *
 * Il ne juge donc **ni la mise en page ni les chiffres**. Les calculs ont leurs tests,
 * qui sont rapides et ne demandent pas d'appareil. Celui-ci répond à une seule question,
 * posée une fois par version d'Android : *est-ce que ça s'ouvre*.
 *
 * ### UiAutomator, et non le testeur de Compose
 *
 * Le testeur de Compose ne voit que la fenêtre de l'activité. La feuille du « + » est
 * une fenêtre à elle — elle s'affiche à l'écran, et l'arbre sémantique l'ignore : le
 * test passait à côté du menu d'ajout tout en le regardant.
 *
 * UiAutomator lit l'arbre d'accessibilité **de toutes les fenêtres**, qui est aussi ce
 * qu'un lecteur d'écran lit. C'est le bon outil ici pour une seconde raison : il ne
 * dépend ni de la version de Compose ni de sa synchronisation, là où ce test doit
 * justement survivre à sept versions d'Android.
 *
 * ### Le vrai graphe, et non un graphe de test
 *
 * Aucun adaptateur n'est remplacé : Room, DataStore, le catalogue embarqué et les
 * ressources sont ceux de l'application. C'est voulu — ce sont précisément les
 * adaptateurs qui se comportent autrement d'une version d'Android à l'autre, et un faux
 * les aurait tous cachés.
 *
 * Le profil et l'objectif sont écrits **par ce même graphe** avant chaque test. Sans
 * objectif, l'application démarre sur l'onboarding ([StartDestinationViewModel]), et
 * aucun des écrans visés n'est atteignable ; traverser les cinq questions à la main
 * rendrait chaque test otage de l'écran qui les pose. L'onboarding a sa propre classe,
 * qui tourne avant celle-ci sur une installation neuve.
 *
 * [decisions]: docs/11-decisions.md
 */
@RunWith(AndroidJUnit4::class)
class AppJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    private val graph: JourneyGraph
        get() = EntryPointAccessors.fromApplication(context.applicationContext, JourneyGraph::class.java)

    @Before
    fun unProfilExiste() = runBlocking {
        val aujourdHui = graph.clock().today()
        graph.profiles().save(
            UserProfile(
                birthDate = aujourdHui.minusYears(AGE),
                sex = Sex.UNSPECIFIED,
                heightCm = TAILLE_CM,
                activity = Activity(WorkActivity.DESK, WeeklySessions(SEANCES)),
            ),
        )
        // Ecrit en dur plutot que calcule : ce test ne juge pas la formule, et un
        // objectif calcule le ferait dependre d'elle.
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
    }

    @Test
    fun l_accueil_s_ouvre() {
        ouvreLAccueil()
    }

    @Test
    fun le_menu_d_ajout_s_ouvre() {
        ouvreLAccueil()
        ouvreLeMenuDAjout()
        attend(HomeStrings.string.home_scan)
        attend(HomeStrings.string.home_add_by_hand)
        attend(SearchStrings.string.search_favorites)
    }

    @Test
    fun la_recherche_s_ouvre() {
        ouvreLAccueil()
        ouvreLaRecherche()
    }

    /** Le premier des deux ecrans qu'un telephone sous Android 11 refusait d'ouvrir. */
    @Test
    fun la_saisie_s_ouvre_depuis_la_recherche() {
        ouvreLAccueil()
        ouvreLaRecherche()
        choisitLePremierAliment()
        attend(EntryStrings.string.entry_title_new)
        attend(EntryStrings.string.entry_save)
    }

    /** Le second : le meme ecran, sur un plat deja ecrit. */
    @Test
    fun l_edition_d_un_plat_s_ouvre() {
        ouvreLAccueil()
        ouvreLaRecherche()
        choisitLePremierAliment()
        attend(EntryStrings.string.entry_save)
        clique(texte(EntryStrings.string.entry_save))
        attend(HomeStrings.string.home_more_ways)

        ouvreLePremierPlat()
        attend(EntryStrings.string.entry_title_edit)
    }

    @Test
    fun les_reglages_s_ouvrent() {
        ouvreLAccueil()
        clique(texte(HomeStrings.string.home_open_profile))
        attend(SettingsStrings.string.settings_title)
    }

    /**
     * Les sept sections des reglages, ouvertes une par une.
     *
     * Chacune est un ecran a part entiere, et elles sont les plus denses de
     * l'application : profil, objectifs, IA, apparence, sauvegarde, photos, mentions.
     * Un defaut de version s'y logerait sans que rien d'autre bouge.
     */
    @Test
    fun chaque_section_des_reglages_s_ouvre() {
        ouvreLAccueil()
        clique(texte(HomeStrings.string.home_open_profile))
        attend(SettingsStrings.string.settings_title)

        SECTIONS.forEach { section ->
            val titre = texte(section)
            clique(titre)
            attendLeLibelle(titre)
            device.pressBack()
            attend(SettingsStrings.string.settings_title)
        }
    }

    @Test
    fun le_journal_de_poids_s_ouvre() {
        ouvreLAccueil()
        clique(texte(HomeStrings.string.home_open_weight))
        // Le titre de l'ecran reprend le libelle de son bouton : sur l'accueil il n'est
        // qu'une description d'icone, ici c'est du texte.
        attend(HomeStrings.string.home_open_weight)
    }

    @Test
    fun la_progression_s_ouvre() {
        ouvreLAccueil()
        // L'anneau de niveau n'a pas de libelle fixe : sa description porte le niveau et
        // la serie. La derniere phrase, elle, ne bouge pas.
        clique(OUVRE_LA_PROGRESSION)
        // L'ecran de progression ne porte pas de libelle qui ne bouge pas ; la preuve
        // qu'on y est, c'est que la barre d'ajout de l'accueil a disparu.
        val barre = By.descContains(texte(HomeStrings.string.home_more_ways))
        val parti = device.wait(Until.gone(barre), ATTENTE_MS)
        assertTrue("L'accueil est reste a l'ecran : la progression ne s'est pas ouverte.", parti ?: false)
    }

    // --- les gestes, nommes comme on les ferait --------------------------------------

    /**
     * Lance l'application, et attend le « + » de la barre du bas.
     *
     * **Et non le libelle du champ** : sans cle d'IA, la barre est verrouillee et dit
     * autre chose ([D136][decisions]). C'est l'etat d'une installation neuve, donc celui
     * de six emulateurs sur sept — un test accroche au libelle du champ n'arrivait
     * jamais a l'ecran suivant, et accusait la version d'Android.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun ouvreLAccueil() {
        ActivityScenario.launch(MainActivity::class.java)
        congedieLeTour()
        attend(HomeStrings.string.home_more_ways)
    }

    /**
     * Passe le tour guide s'il se presente.
     *
     * **Une installation neuve le montre toujours** ([D141][decisions]), et son voile
     * avale les gestes : sans ce renvoi, chaque test de ce fichier parlerait a une
     * bulle. Le tour a son propre test, qui lui le regarde.
     *
     * [decisions]: docs/11-decisions.md
     */
    private fun congedieLeTour() {
        val passer = By.textContains(texte(HomeStrings.string.tour_skip))
        if (device.wait(Until.hasObject(passer), REPOS_MS) == true) {
            device.findObject(passer)?.click()
            device.waitForIdle(REPOS_MS)
        }
    }

    private fun ouvreLeMenuDAjout() = clique(texte(HomeStrings.string.home_more_ways))

    private fun ouvreLaRecherche() {
        ouvreLeMenuDAjout()
        attend(HomeStrings.string.home_add_by_hand)
        clique(texte(HomeStrings.string.home_add_by_hand))
        attend(SearchStrings.string.search_field_label)
    }

    /**
     * Tape deux lettres, puis prend ce qui remonte.
     *
     * **Sans nommer d'aliment** : le catalogue embarque est celui de la vraie
     * application, et un test qui citerait une fiche se casserait le jour ou elle
     * changerait de libelle. `kcal` s'ecrit pareil dans les deux langues.
     *
     * Le champ se trouve par sa **classe** et non par son libelle : ce que Compose
     * expose sous « Search for a food » est l'etiquette, et ecrire dedans n'ecrit nulle
     * part. Un champ de saisie se presente comme un `EditText` a l'accessibilite, quelle
     * que soit la version.
     */
    private fun choisitLePremierAliment() {
        val champ = device.wait(Until.findObject(By.clazz(SAISIE)), ATTENTE_MS)
        requireNotNull(champ) { "Le champ de recherche ne repond pas." }.text = LETTRES
        prendLaPremiereLigne()
    }

    /**
     * Fait defiler l'accueil jusqu'a un plat, puis l'ouvre.
     *
     * La liste est **sous la pliure** : l'accueil montre d'abord l'hexagone et les six
     * compteurs. Et une fois deroulee, un plat a demi glisse sous le bandeau du
     * calendrier, qui reste en haut, recevrait un clic qui part dans le bandeau.
     *
     * On attend donc que le plat **existe**, puis on ne prend que celui qui se trouve
     * franchement au milieu de l'ecran, hors du bandeau comme de la barre d'ajout.
     */
    private fun ouvreLePremierPlat() {
        fermeLeClavier()
        // On regarde, puis on deroule -- et non l'inverse. UiAutomator ne voit que ce
        // qui est a l'ecran : attendre le plat avant d'avoir deroule, c'est attendre
        // qu'une chose apparaisse la ou elle ne peut pas etre.
        repeat(DEROULES) {
            if (cliqueLaLigne(platEnPleinEcran())) return
            glisse()
        }
        fail("Aucun plat n'est venu sous les yeux." + System.lineSeparator() + "Il montrait : " + ceQuOnVoit())
    }

    /**
     * Laisse la liste se poser, puis ouvre la premiere ligne.
     *
     * **On recommence jusqu'a ce que l'ecran change**, et non jusqu'a ce qu'un clic
     * parte. Les resultats arrivent par vagues -- une premiere reponse du catalogue,
     * puis une seconde mieux classee -- et un clic sur une ligne en train d'etre
     * remplacee est accepte sans rien ouvrir. Verifier le depart du champ de recherche
     * est la seule preuve qui vaille.
     */
    private fun prendLaPremiereLigne() {
        attendLeLibelle(ENERGIE)
        attendUneListePosee()
        repeat(ESSAIS) {
            if (cliqueLaLigne(device.findObject(By.textContains(ENERGIE)))) return
            device.waitForIdle(PAS_MS)
        }
        fail("Aucune ligne ne s'est laissee ouvrir." + System.lineSeparator() + "Il montrait : " + ceQuOnVoit())
    }

    /**
     * Attend que la premiere ligne cesse de changer.
     *
     * **Deux lectures identiques, et non un delai.** Les resultats arrivent par vagues
     * -- une premiere reponse du catalogue, puis une seconde mieux classee -- et
     * cliquer pendant qu'une ligne est remplacee revient a cliquer sur rien : le clic
     * part, l'ecran ne s'ouvre pas, et le test accuse la saisie. Un delai fixe aurait
     * ete trop court sur une machine chargee et trop long partout ailleurs.
     */
    private fun attendUneListePosee() {
        var precedent: String? = null
        repeat(ESSAIS) {
            val actuel = runCatching { device.findObject(By.textContains(ENERGIE))?.text }.getOrNull()
            if (actuel != null && actuel == precedent) return
            precedent = actuel
            device.waitForIdle(PAS_MS)
        }
    }

    /**
     * Clique la ligne reperee par son energie.
     *
     * **Sur le noeud trouve, et non sur son parent.** Compose annonce la plupart de ses
     * noeuds comme non cliquables tout en traitant le geste : UiAutomator le dit en
     * clair dans le journal, puis clique par coordonnees -- et cela atteint la ligne.
     * Remonter d'un cran semblait plus propre, mais le parent n'est pas toujours la
     * ligne : sur Android 16 c'etait le conteneur de l'ecran, et le clic refermait la
     * recherche au lieu d'ouvrir la fiche.
     */
    private fun cliqueLaLigne(valeur: UiObject2?): Boolean = valeur != null && runCatching { valeur.click() }.isSuccess

    /** Un plat dont le centre tombe entre le bandeau du calendrier et la barre d'ajout. */
    private fun platEnPleinEcran() = device
        .findObjects(By.textContains(ENERGIE))
        .lastOrNull { it.visibleBounds.centerY() in device.displayHeight / 4..device.displayHeight * 7 / 8 }

    /**
     * Referme le clavier, et seulement s'il est ouvert.
     *
     * Apres une recherche, le clavier reste leve et couvre le tiers bas de l'ecran : le
     * plat qu'on vient d'ecrire est dessous. Un `pressBack()` systematique aurait quitte
     * l'accueil quand il n'y avait pas de clavier — on demande donc au systeme s'il y en
     * a un.
     */
    private fun fermeLeClavier() {
        val ouvert = instrumentation.uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        if (ouvert) {
            device.pressBack()
            device.waitForIdle(PAS_MS)
        }
    }

    private fun glisse() {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            GLISSE_PAS,
        )
        device.waitForIdle(PAS_MS)
    }

    private fun texte(id: Int): String = context.getString(id)

    private fun attend(id: Int) = attendLeLibelle(texte(id))

    /**
     * Ce libelle est-il a l'ecran, **en texte ou en description** ?
     *
     * Les deux, parce que Compose expose les deux. Une entree de la feuille d'ajout fond
     * son titre et sa ligne d'explication dans une seule description — c'est ce qu'un
     * lecteur d'ecran doit entendre d'un coup — et le meme libelle est du texte partout
     * ailleurs. Chercher dans un seul des deux rendait le test aveugle a un menu qu'il
     * avait pourtant sous les yeux.
     */
    private fun voit(libelle: String): Boolean =
        device.wait(Until.hasObject(By.textContains(libelle)), PAS_MS) == true ||
            device.wait(Until.hasObject(By.descContains(libelle)), PAS_MS) == true

    /**
     * Attend qu'un libelle paraisse, et echoue en le nommant.
     *
     * Un nombre d'essais et non une echeance : lire l'horloge systeme est precisement ce
     * que ce depot interdit, et chaque essai attend deja par lui-meme.
     */
    private fun attendLeLibelle(libelle: String) {
        repeat(ESSAIS) { if (voit(libelle)) return }
        fail("L'ecran n'a jamais montre : $libelle" + System.lineSeparator() + "Il montrait : " + ceQuOnVoit())
    }

    /**
     * Ce que l'ecran porte, pour le dire quand on ne trouve pas ce qu'on cherchait.
     *
     * Un test qui echoue sur sept versions d'Android doit dire **ou il s'est arrete**,
     * pas seulement qu'il s'est arrete : sans cela, chaque echec demande de rejouer la
     * session a la main sur l'emulateur concerne.
     */
    private fun ceQuOnVoit(): String {
        // Chaque lecture est protegee : un noeud peut disparaître entre l'instant ou on
        // le liste et celui ou on le lit, et un diagnostic qui jette en route efface
        // justement le message qu'on etait en train d'ecrire.
        val tout = Regex(".+").toPattern()
        return runCatching {
            device.findObjects(By.text(tout)).mapNotNull { runCatching { it.text }.getOrNull() } +
                device.findObjects(By.desc(tout)).mapNotNull { runCatching { it.contentDescription }.getOrNull() }
        }.getOrDefault(emptyList()).distinct().joinToString(separator = " | ").take(EXTRAIT)
    }

    /**
     * Cherche, puis clique — et recommence si la cible a bouge entre les deux.
     *
     * Une liste se recompose pendant qu'on la regarde : le noeud trouve a l'instant
     * d'avant n'existe deja plus, et UiAutomator le dit en jetant. C'est la vie d'un
     * ecran vivant, pas un defaut — un doigt qui rate recommence, lui aussi.
     */
    private fun clique(libelle: String) {
        repeat(ESSAIS) {
            val cible = device.findObject(By.textContains(libelle)) ?: device.findObject(By.descContains(libelle))
            if (cible != null && runCatching { cible.click() }.isSuccess) return
            device.waitForIdle(PAS_MS)
        }
        fail("Jamais cliquable : $libelle" + System.lineSeparator() + "Il montrait : " + ceQuOnVoit())
    }

    private companion object {
        const val AGE = 30L
        const val TAILLE_CM = 175.0
        const val SEANCES = 2
        const val ATTENTE_MS = 20_000L
        const val PAS_MS = 500L

        /** Vingt essais d'une demi-seconde par recherche : vingt secondes en tout. */
        const val ESSAIS = 20

        /** Le temps qu'on laisse a une liste qui se complete avant d'y toucher. */
        const val REPOS_MS = 1_500L

        /** Six glissees : deux suffisent a un journal court, six a une journee chargee. */
        const val DEROULES = 6

        /**
         * Un glisse lent, et non une chiquenaude.
         *
         * Dix pas lancent la liste, qui continue sur son erre : le clic qui suit vise
         * alors des coordonnees que le plat a deja quittees.
         */
        const val GLISSE_PAS = 60

        const val KCAL = 2400.0
        const val PROTEINES = 120.0
        const val GLUCIDES = 280.0
        const val SUCRES = 60.0
        const val LIPIDES = 80.0
        const val FIBRES = 30.0

        /**
         * Deux lettres, et pas une.
         *
         * La recherche n'ouvre pas en dessous — elle le dit elle-meme : *tapez deux
         * lettres*. Et « to » plutot qu'autre chose parce que tomate et tomato
         * commencent pareil : le catalogue suit la langue de l'application, et ce test
         * tourne dans les deux.
         */
        const val LETTRES = "to"

        /** Ce qu'un champ de saisie Compose annonce a l'arbre d'accessibilite. */
        const val SAISIE = "android.widget.EditText"

        /** Ce qu'une ligne de resultat porte toujours, et qui ne se traduit pas. */
        const val ENERGIE = "kcal"

        /** La fin de la description de l'anneau, celle qui ne porte aucun chiffre. */
        const val OUVRE_LA_PROGRESSION = "progress"

        /** De quoi reconnaitre l'ecran sans noyer le journal de test. */
        const val EXTRAIT = 700

        /** Les sept entrees du hub, dans l'ordre ou il les presente. */
        val SECTIONS = listOf(
            SettingsStrings.string.settings_profile_title,
            SettingsStrings.string.settings_ai_title,
            SettingsStrings.string.settings_backup_title,
            SettingsStrings.string.settings_photos_title,
            SettingsStrings.string.settings_appearance_title,
            SettingsStrings.string.settings_notices_title,
            SettingsStrings.string.settings_contribution_title,
        )
    }
}
