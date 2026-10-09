package app.hexavore

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import app.hexavore.core.designsystem.R as DesignStrings
import app.hexavore.feature.home.R as HomeStrings
import app.hexavore.feature.onboarding.R as OnboardingStrings

/**
 * Le parcours d'un premier lancement, du tout début jusqu'à un accueil utilisable.
 *
 * ### Pourquoi ce parcours-là et pas un autre
 *
 * C'est **celui qui a cassé**. Un utilisateur sur Galaxy S24 a traversé les cinq
 * questions sans encombre, puis s'est arrêté au tour guidé : plus aucun appui ne portait,
 * et le tour revenait à chaque lancement. Aucun test du dépôt ne couvrait cette
 * jonction — [OnboardingJourneyTest] s'arrête à la deuxième question, et [AppJourneyTest]
 * écrit un profil pour sauter l'onboarding d'un bloc. Entre les deux, l'endroit exact où
 * la panne s'est produite n'était regardé par personne.
 *
 * Il va donc d'un bout à l'autre, dans l'ordre où un humain les rencontre :
 *
 * ```
 * avertissement → qui vous êtes → activité → objectif → vos cibles
 *     → tour guidé → accueil → un repas noté
 * ```
 *
 * ### C'est le test le plus fragile du dépôt, et c'est assumé
 *
 * [OnboardingJourneyTest] s'arrête à la deuxième question pour une raison écrite : les
 * suivantes demandent un sélecteur de date et des champs numériques, et un test qui les
 * traverse casse à la première question déplacée **sans rien dire de la version
 * d'Android**. Le raisonnement tient toujours ; ce qu'il manquait, c'est que la panne
 * cherchée vit après ces questions, et qu'on ne l'atteint pas sans les franchir.
 *
 * La réponse n'est pas de rendre ce test infaillible mais de le rendre **bavard** :
 * chaque étape échoue en nommant ce que l'écran portait, de sorte qu'une question
 * déplacée se lise comme une question déplacée et non comme une régression d'Android.
 *
 * Les tests du tour lui-même, eux, ne dépendent pas de l'onboarding : voir
 * [TourJourneyTest], qui écrit le profil par le graphe et reste la référence pour toute
 * la matrice d'émulateurs.
 *
 * ### Il demande une installation neuve
 *
 * Sans objectif, l'application démarre sur l'onboarding ; avec, elle démarre sur
 * l'accueil et ce fichier n'a plus de sujet. Ces tests **exigent `clearPackageData`**,
 * c'est-à-dire l'orchestrateur.
 */
@RunWith(AndroidJUnit4::class)
class PremierLancementTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val ecran by lazy { Ecran(instrumentation) }

    /**
     * **La permission de notifier est accordée d'avance**, et ce n'est pas une commodité.
     *
     * ### Ce que sa boîte fait au parcours
     *
     * L'onboarding la demande à son dernier geste, puis l'accueil **la redemande** : il
     * relit `permissionAsked()`, que l'onboarding n'écrit jamais. La seconde boîte se pose
     * alors par-dessus le tour, possède l'entrée, et plus rien ne répond — c'est ce que le
     * premier passage sur émulateur a montré, sept échecs sur sept.
     *
     * ### Pourquoi l'écarter plutôt que la traverser
     *
     * Une boîte du système n'appartient pas à l'application : ses libellés sont ceux
     * d'Android, ils changent de langue et de version, et un parcours accroché à « Allow »
     * casserait sur un téléphone en français sans rien dire du chemin qu'il teste.
     *
     * **La collision est un vrai défaut et elle reste entière** — elle a besoin de son
     * correctif, pas d'un contournement de test. Ce fichier répond à une autre question :
     * *les cinq questions mènent-elles à un accueil utilisable*. Une boîte système devant
     * lui empêche d'y répondre.
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

    /**
     * Le parcours complet, en une seule méthode.
     *
     * **Et non six méthodes enchaînées** : chacune aurait dû refaire les cinq questions
     * pour arriver à son sujet, ce qui multiplie par six le temps de la matrice et par six
     * les occasions de casser ailleurs que sur ce qu'on regarde. Un parcours est une
     * histoire ; on la raconte une fois.
     */
    @Test
    fun une_installation_neuve_va_de_l_avertissement_a_un_repas_note() {
        ActivityScenario.launch(MainActivity::class.java)

        accepteLAvertissement()
        ditQuiOnEst()
        ditCommentOnBouge()
        ditCeQuOnVeut()
        regardeSesCibles()

        traverseLeTour()
        noteUnRepasALaMain()
    }

    /** **1.** Le nom, la figure, et la case qui débloque « Continuer ». */
    private fun accepteLAvertissement() {
        ecran.attend(OnboardingStrings.string.onboarding_welcome_title)
        ecran.attend(OnboardingStrings.string.onboarding_disclaimer)

        // La case d'abord : le bouton reste inerte tant qu'elle n'est pas cochee.
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_disclaimer))
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_next))
    }

    /** **2.** Date de naissance, sexe, taille, poids. */
    private fun ditQuiOnEst() {
        ecran.attend(OnboardingStrings.string.onboarding_you_title)
        // Contrainte ferme de docs/01 : la phrase de confidentialite se lit a l'endroit
        // ou l'on commence a saisir, sur toutes les versions.
        ecran.attend(OnboardingStrings.string.onboarding_privacy)

        choisitUneDateDeNaissance()
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_sex_unspecified))
        remplitTailleEtPoids()

        ecran.fermeLeClavier()
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_next))
    }

    /** **3.** Le métier, puis le nombre de séances. */
    private fun ditCommentOnBouge() {
        ecran.attend(OnboardingStrings.string.onboarding_work_title)
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_work_desk))

        ecran.attend(OnboardingStrings.string.onboarding_sessions_title)
        // Le selecteur de seances propose des nombres ; deux est au milieu de ce qu'il
        // offre, et ne depend d'aucun libelle traduit.
        ecran.clique(SEANCES)

        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_next))
    }

    /**
     * **4.** L'objectif.
     *
     * « Maintenir » et non « Perdre » : c'est la seule des trois qui ne fait apparaître ni
     * poids cible ni échéance. Le but de ce parcours est d'atteindre le tour, pas
     * d'éprouver les garde-fous de perte de poids — ceux-là ont leurs propres tests, sur
     * la JVM, où ils sont instantanés.
     */
    private fun ditCeQuOnVeut() {
        ecran.attend(OnboardingStrings.string.onboarding_objective_title)
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_strategy_maintain))
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_next))
    }

    /** **5.** Les six chiffres, puis « C'est parti ». */
    private fun regardeSesCibles() {
        ecran.attend(OnboardingStrings.string.onboarding_result_title)
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_start))
    }

    /**
     * Le tour guidé, traversé **au doigt** jusqu'à ce qu'il s'en aille.
     *
     * ### C'est l'étape qui a bloqué, et c'est pour elle que ce fichier existe
     *
     * On passe par « Passer » plutôt que par les quatorze bulles : [TourJourneyTest] les
     * parcourt une par une, et ce parcours-ci cherche autre chose — que la sortie du tour
     * **rende l'accueil utilisable**. Une bulle congédiée qui laisserait son voile en
     * place passerait les assertions du tour et échouerait ici, à la première chose qu'on
     * demande à l'accueil.
     *
     * L'appui est explicitement celui d'un doigt : sur l'émulateur, un clic de souris
     * n'émet aucun mouvement, et c'est le seul geste que le tour acceptait.
     */
    private fun traverseLeTour() {
        ecran.attend(HomeStrings.string.home_more_ways)
        ecran.attend(HomeStrings.string.tour_day_title)
        ecran.seRepose()

        ecran.appuieCommeUnDoigt(ecran.texte(HomeStrings.string.tour_skip))

        ecran.attendLaDisparition(HomeStrings.string.tour_day_title)
        ecran.attendLaDisparition(HomeStrings.string.tour_skip)
    }

    /**
     * Un repas noté à la main, par le catalogue embarqué.
     *
     * **Par le catalogue et non par l'IA** : une installation neuve n'a pas de clé, le
     * champ de description est verrouillé, et c'est le chemin qui marche sans rien
     * demander à personne. C'est aussi la preuve que ce que le tour vient de promettre est
     * vrai.
     */
    private fun noteUnRepasALaMain() {
        ecran.clique(ecran.texte(HomeStrings.string.home_more_ways))
        ecran.attend(HomeStrings.string.home_add_by_hand)
        ecran.clique(ecran.texte(HomeStrings.string.home_add_by_hand))

        val champs = ecran.champsDeSaisie()
        assertTrue(
            "La recherche n'offre pas de champ de saisie." + System.lineSeparator() +
                "Elle montrait : " + ecran.ceQuOnVoit(),
            champs.isNotEmpty(),
        )
        champs.first().text = LETTRES

        // `kcal` s'ecrit pareil dans les deux langues : c'est ce qu'une ligne de resultat
        // porte toujours, et ce qu'aucune traduction ne deplace.
        ecran.attendLeLibelle(ENERGIE)
    }

    /**
     * Choisit une date de naissance dans le sélecteur.
     *
     * ### Par la grille des années
     *
     * Le champ s'ouvre sur un mois, et une naissance ne se cherche pas en feuilletant les
     * mois. L'en-tête du sélecteur porte l'année affichée et bascule sur la grille : on la
     * reconnaît à ses quatre chiffres, ce qui est vrai dans toutes les langues et sur
     * toutes les versions, là où son libellé complet (« octobre 2026 ») ne l'est dans
     * aucune.
     *
     * Le jour vient ensuite, et c'est le 15 : il existe dans les douze mois, et il n'est
     * jamais dans le futur pour une année choisie trente ans en arrière.
     */
    private fun choisitUneDateDeNaissance() {
        ecran.clique(ecran.texte(OnboardingStrings.string.onboarding_birth_date))
        ecran.attend(DesignStrings.string.ds_date_confirm)

        basculeSurLaGrilleDesAnnees()

        val annee = anneeDeNaissance()
        ecran.clique(annee)
        ecran.clique(JOUR)
        ecran.clique(ecran.texte(DesignStrings.string.ds_date_confirm))

        // Le champ porte desormais une date : sans elle,  Continuer  reste inerte et
        // l'echec se produirait deux ecrans plus loin, sur une cause introuvable.
        ecran.attendLaDisparitionDuLibelle(ecran.texte(DesignStrings.string.ds_date_confirm))
    }

    /**
     * Ouvre la grille des années en cliquant l'en-tête du sélecteur.
     *
     * On cherche **un libellé qui contient une année sur quatre chiffres**, parce que
     * c'est tout ce que l'en-tête garantit d'avoir en commun d'une langue à l'autre.
     */
    private fun basculeSurLaGrilleDesAnnees() {
        val entete = ecran.ceQuOnVoit().let { ANNEE.matcher(it) }
        if (!entete.find()) {
            fail(
                "Le selecteur de date n'affiche aucune annee a quatre chiffres : " +
                    "l'en-tete a change de forme." + System.lineSeparator() +
                    "Il montrait : " + ecran.ceQuOnVoit(),
            )
        }
        ecran.clique(entete.group())
    }

    /** Trente ans en arrière : un adulte, et bien à l'intérieur des bornes du sélecteur. */
    private fun anneeDeNaissance(): String {
        val cetteAnnee = ANNEE.matcher(ecran.ceQuOnVoit())
        if (!cetteAnnee.find()) {
            fail("La grille des annees ne montre aucune annee." + System.lineSeparator() + ecran.ceQuOnVoit())
        }
        return (cetteAnnee.group().toInt() - AGE).toString()
    }

    /**
     * Écrit la taille puis le poids.
     *
     * **Les deux derniers champs de l'écran, dans cet ordre.** Le premier est la date, qui
     * ne s'écrit pas au clavier. On compte ce qu'on reçoit avant d'y toucher : trois champs
     * attendus, et un écart se dit ici plutôt que de remplir le poids avec une taille.
     */
    private fun remplitTailleEtPoids() {
        val champs = ecran.champsDeSaisie()
        if (champs.size < CHAMPS_ATTENDUS) {
            fail(
                "  Qui vous etes  montre ${champs.size} champs de saisie au lieu de " +
                    "$CHAMPS_ATTENDUS : la question a change de forme." + System.lineSeparator() +
                    "Elle montrait : " + ecran.ceQuOnVoit(),
            )
        }
        champs[champs.size - 2].text = TAILLE_CM
        champs[champs.size - 1].text = POIDS_KG
    }

    private companion object {
        /** Date, taille, poids : la date ne s'ecrit pas, mais elle compte dans l'arbre. */
        const val CHAMPS_ATTENDUS = 3

        const val AGE = 30
        const val TAILLE_CM = "175"
        const val POIDS_KG = "70"
        const val JOUR = "15"
        const val SEANCES = "2"

        /** Deux lettres : la recherche n'ouvre pas en dessous, et elle le dit elle-meme. */
        const val LETTRES = "to"

        /** Ce qu'une ligne de resultat porte toujours, et qui ne se traduit pas. */
        const val ENERGIE = "kcal"

        /** Une annee sur quatre chiffres, qui commence par 1 ou 2. */
        val ANNEE: Pattern = Pattern.compile("""\b[12]\d{3}\b""")
    }
}
