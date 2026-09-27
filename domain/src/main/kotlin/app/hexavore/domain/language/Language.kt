package app.hexavore.domain.language

import kotlinx.coroutines.flow.Flow

/**
 * La langue dans laquelle l'application lit ses données et parle à ses services.
 *
 * **Ce n'est pas la langue de l'interface, et la distinction est tout le sujet.** Les
 * mots des écrans sont l'affaire des ressources Android : un `values-fr/` de plus, et
 * personne n'écrit une ligne de Kotlin. Ce que cette énumération désigne est ce
 * qu'aucune ressource ne peut porter — la colonne de libellés qu'on lit dans la table
 * de l'ANSES, l'article qu'on retire en tête d'un libellé, le prompt qu'on envoie au
 * modèle, le champ qu'on demande à Open Food Facts. Quatre endroits où `fr` était
 * écrit en dur, et qui doivent désormais dire **laquelle**.
 *
 * **Une énumération et des `when` sans `else`**, comme [app.hexavore.domain.appearance.ThemeMode]
 * et pour la même raison : une troisième langue **ne compile pas** tant qu'elle n'a pas
 * ses articles, ses pluriels, ses libellés de portion et ses trois prompts. Un `else`
 * l'aurait laissée s'ajouter à moitié, et le défaut ne se serait vu que sur l'appareil
 * de quelqu'un — une recherche qui ne rend rien, un modèle qui répond dans la mauvaise
 * langue.
 *
 * **L'anglais est le repli, pas le français.** C'est aussi ce que dit l'arborescence des
 * ressources : `values/` sans qualificatif porte l'anglais, `values-fr/` le français. Un
 * appareil réglé en allemand tombe donc sur de l'anglais, aux deux étages, sans que
 * personne ait à le décider deux fois.
 *
 * @property tag l'étiquette BCP 47 de la langue, celle des ressources et celle du
 *   système. Elle n'a pas de région : on traduit une langue, pas un pays.
 * @see docs/04-sources-de-donnees.md
 */
enum class ContentLanguage(val tag: String) {
    ENGLISH("en"),
    FRENCH("fr"),
    ;

    companion object {
        /**
         * Ce qu'on lit quand le système ne demande rien qu'on sache écrire.
         *
         * L'anglais, et c'est le même choix que le `values/` sans qualificatif : une
         * application qui replie sur le français devant un appareil japonais est une
         * application qu'on referme.
         */
        val FALLBACK: ContentLanguage = ENGLISH

        /**
         * La langue d'une étiquette, ou `null` si ce n'en est aucune des nôtres.
         *
         * **La sous-étiquette de langue seule.** `fr-CA`, `fr_FR` et `FR` désignent tous
         * le français : c'est ce que fait la résolution de ressources d'Android, et
         * s'en écarter ferait que l'interface et le catalogue ne parlent pas la même
         * langue sur un téléphone québécois.
         */
        fun ofTag(tag: String?): ContentLanguage? {
            val language = tag?.takeWhile { it != '-' && it != '_' }?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == language }
        }

        /**
         * La première langue du système que l'application sait écrire.
         *
         * **La liste entière et dans son ordre**, jamais la première seule. Quelqu'un
         * qui règle son téléphone sur « allemand, puis français » a exprimé un second
         * choix, et lui rendre de l'anglais parce que l'allemand manque serait ignorer
         * ce qu'il a écrit. C'est là encore le comportement d'Android pour ses
         * ressources, repris à l'identique pour que les deux étages s'accordent.
         */
        fun detect(systemLanguages: List<String>): ContentLanguage =
            systemLanguages.firstNotNullOfOrNull(::ofTag) ?: FALLBACK
    }
}

/**
 * Ce que l'utilisateur a réglé sur la langue.
 *
 * **Un type fermé et non une énumération à trois cas.** `SYSTEM`, `ENGLISH`, `FRENCH`
 * aurait fait deux listes à tenir : ajouter l'espagnol aurait demandé une entrée ici
 * **et** une dans [ContentLanguage], et rien n'aurait signalé l'oubli de la seconde.
 * Ici, [Chosen] porte une langue, donc la liste des choix se déduit — et une langue de
 * plus apparaît dans les réglages sans qu'on y touche.
 *
 * **[System] est un choix, pas une absence de choix**, exactement comme
 * [app.hexavore.domain.appearance.ThemeMode.SYSTEM] : c'est une intention durable —
 * *suivre le téléphone* — et la ranger comme un vide obligerait chaque lecteur à savoir
 * ce que le vide signifie.
 *
 * @property code ce qui est écrit dans les préférences. Une chaîne stable et non le nom
 *   d'une classe : renommer un type Kotlin ne doit pas changer la langue de quelqu'un.
 */
sealed interface LanguageMode {
    val code: String

    /** Ce que l'application faisait avant qu'on puisse choisir, et le défaut. */
    data object System : LanguageMode {
        override val code: String = "system"
    }

    /** Une langue imposée, quel que soit le réglage du téléphone. */
    data class Chosen(val language: ContentLanguage) : LanguageMode {
        override val code: String get() = language.tag
    }

    companion object {
        /**
         * Les choix qu'offre l'écran, dans l'ordre où il les montre.
         *
         * Construits et non écrits : c'est ce qui fait qu'une langue de plus dans
         * [ContentLanguage] apparaît toute seule dans les réglages.
         */
        val ALL: List<LanguageMode> = listOf(System) + ContentLanguage.entries.map(::Chosen)

        /**
         * Le réglage qu'un code désigne, ou [System].
         *
         * Un code inconnu retombe sur « suivre le système » plutôt que de faire tomber
         * le démarrage : le fichier de préférences n'est pas contrôlé par l'application
         * seule, et une valeur qu'on ne sait pas lire se lit comme « on n'a rien
         * choisi ». C'est la règle déjà tenue pour le thème.
         */
        fun ofCode(code: String?): LanguageMode = ALL.firstOrNull { it.code == code } ?: System
    }
}

/**
 * Quelle langue lire, le réglage et le téléphone étant ce qu'ils sont.
 *
 * **Une fonction pure, et le système arrive en paramètre** — c'est
 * [app.hexavore.domain.appearance.ThemeMode.isDark] appliquée telle quelle
 * ([D113][decisions]). La détection automatique s'éprouve alors sans écran et sans
 * appareil, et il n'y a qu'un endroit qui sache ce que « suivre le système » veut dire.
 *
 * @param systemLanguages les langues du système, dans l'ordre de préférence, en
 *   étiquettes BCP 47.
 *
 * [decisions]: docs/11-decisions.md
 */
fun LanguageMode.resolve(systemLanguages: List<String>): ContentLanguage = when (this) {
    is LanguageMode.Chosen -> language
    LanguageMode.System -> ContentLanguage.detect(systemLanguages)
}

/**
 * Les langues du système, dans l'ordre où leur propriétaire les a rangées.
 *
 * Un port parce que c'est une lecture de plateforme, au même titre que l'horloge : la
 * règle de détection est dans le domaine et se teste sur la JVM, la lecture est dans
 * `:core:common` et ne se teste pas.
 *
 * **Les langues du *système*, et non celles de l'application.** Une fois une langue
 * imposée, Android 13 place cette langue en tête de la liste de l'application : la lire
 * là ferait que « suivre le système » suivrait le dernier choix imposé, indéfiniment.
 */
fun interface SystemLanguages {
    fun tags(): List<String>
}

/**
 * La langue de contenu en vigueur, telle que le catalogue et les prompts la demandent.
 *
 * **Une lecture synchrone et non un flux**, contrairement au réglage lui-même. Ceux qui
 * la demandent sont au milieu d'une requête — une recherche dans la table de l'ANSES, un
 * appel qui part au modèle — et n'ont pas de composition à recomposer : ils ont besoin
 * d'une réponse maintenant. Le magasin la porte déjà en mémoire, lue au démarrage sans
 * suspendre, comme le thème.
 *
 * **Relue à chaque appel, jamais capturée à la construction.** Un changement de langue
 * recrée les activités mais pas les `ViewModel`, qui survivent aux changements de
 * configuration : une valeur retenue à la construction d'un cas d'usage resterait celle
 * d'avant, et la recherche continuerait d'interroger la mauvaise colonne.
 */
fun interface ContentLanguages {
    fun current(): ContentLanguage
}

/**
 * Ce qui a été réglé sur la langue, et qui l'applique.
 *
 * **Un port à part et non une méthode d'`AppearanceSettings`**, alors que les deux se
 * règlent sur le même écran. La raison n'est pas l'esthétique : la langue est la seule
 * des quatre préférences que l'application **n'applique pas elle-même**. Un thème est un
 * `when` dans une composition ; une langue fait choisir un `values-fr/` par la plateforme,
 * et à partir d'Android 13 cette dernière la retient et l'expose dans ses propres
 * réglages. Il y a donc deux portes, et une seule vérité possible : celle que la
 * plateforme applique. Ranger ce réglage avec le thème aurait fait croire à une
 * préférence comme les autres, que l'on pourrait lire dans un fichier à nous.
 *
 * Voir [D129][decisions] pour ce que cela coûte et ce que cela évite.
 *
 * [decisions]: docs/11-decisions.md
 */
interface LanguageSettings {
    /**
     * Le réglage en vigueur, relu à chaque abonnement.
     *
     * **Relu et non mémorisé**, parce qu'il peut avoir changé sans nous : sur Android 13
     * et au-delà, les réglages du système offrent leur propre sélecteur pour cette
     * application. Un flux qui ne porterait que nos écritures montrerait « suivre le
     * système » à quelqu'un qui vient de choisir le français dans Android.
     */
    fun observe(): Flow<LanguageMode>

    /** Applique la langue, et la note pour le prochain démarrage. */
    suspend fun choose(mode: LanguageMode)

    /**
     * Restaure la langue notée, si la plateforme ne l'a pas déjà fait.
     *
     * Appelé une fois au démarrage du processus, **avant la première activité**. En deçà
     * d'Android 13, rien ne réapplique une langue imposée au lancement suivant : c'est ce
     * que cette méthode rattrape. À partir d'Android 13, la plateforme l'a déjà fait, et
     * cette méthode ne touche à rien — sans quoi elle écraserait un choix fait entre-temps
     * dans les réglages d'Android.
     */
    fun restore()
}
