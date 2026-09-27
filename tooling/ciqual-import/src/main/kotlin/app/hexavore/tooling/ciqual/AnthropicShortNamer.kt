package app.hexavore.tooling.ciqual

import app.hexavore.domain.language.ContentLanguage
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams

/**
 * Le raccourcissement, demandé à Claude.
 *
 * **Hors de l'application, et c'est tout le choix.** L'application ne gagne ni
 * bouton, ni prompt en asset, ni compteur à payer : la passe tourne une fois, sur la
 * machine de développement, et son résultat est un fichier versionné que tout le
 * monde reçoit ensuite. Une table de l'ANSES qui ne change qu'à chaque publication
 * n'a pas besoin d'être re-raccourcie sur trois mille téléphones.
 *
 * **La clé n'est jamais écrite nulle part.** Elle est passée à l'invocation de la
 * tâche, tenue en mémoire le temps de la passe, et n'apparaît dans aucun fichier,
 * aucun journal et aucun message d'erreur. C'est la règle du projet, et elle vaut ici
 * comme dans l'application.
 *
 * @param model l'identifiant du modèle. Relevé, jamais écrit de mémoire.
 * @param language la langue des libellés reçus, **et** celle des titres attendus. La
 *   consigne est écrite dans cette langue : une consigne française devant des libellés
 *   anglais rend des titres français, que le contrôle de longueur laisse passer.
 */
internal class AnthropicShortNamer(
    apiKey: String,
    private val model: String,
    private val language: ContentLanguage,
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build(),
) : ShortNamer {
    override fun shorten(labels: List<LongLabel>): Map<String, String> {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(language.systemPrompt)
            .addUserMessage(labels.joinToString("\n") { "${it.code}\t${it.name}" })
            .build()

        val answer = client.messages().create(params).content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("\n")

        return parseShortNames(answer)
    }

    private companion object {
        /**
         * Cinquante titres tiennent très large là-dedans.
         *
         * Le compte est volontairement haut : une réponse tronquée coûterait le lot
         * entier, et les jetons non produits ne se paient pas.
         */
        const val MAX_TOKENS = 8_000L

        /**
         * La consigne, ici et non dans un asset.
         *
         * L'application a ses prompts en asset parce qu'ils y sont relisibles sans
         * recompiler ; celui-ci ne sert qu'à une tâche d'outillage lancée à la main,
         * et l'y mettre l'aurait fait entrer dans l'APK sans jamais y servir.
         */
        val FRENCH_PROMPT =
            """
            Tu raccourcis des libelles d'aliments de la table CIQUAL de l'ANSES.

            Chaque ligne recue porte un code, une tabulation, puis le libelle publie.
            Reponds une ligne par aliment : le meme code, une tabulation, le titre court.
            Rien d'autre -- ni preambule, ni numerotation, ni ligne de commentaire.

            Le titre court doit :
            - nommer l'aliment tel qu'une personne le dirait a table ;
            - tenir en ${ShortNamesCsv.MAX_LENGTH} caracteres au plus, et rester plus court que le libelle ;
            - garder ce qui distingue cette fiche d'une autre du meme aliment -- la cuisson,
              la teneur en matiere grasse, le fait qu'il soit cru, en conserve ou sucre ;
            - abandonner ce qui ne distingue rien : « sans precision sur le rayon »,
              « aliment moyen », « tout type de », les repetitions du nom generique.
            - garder les accents, et commencer par une majuscule.

            Exemples :
            Poulet, blanc, sans peau, cuit au four, sans matiere grasse ajoutee -> Blanc de poulet au four
            Specialite de fruits, tout type de fruits, sans sucres ajoutes, preemballee -> Specialite de fruits sans sucres
            Yaourt ou specialite laitiere, nature, au lait entier -> Yaourt nature au lait entier

            Si un libelle ne peut pas etre raccourci sans mentir, omets sa ligne.
            Une omission se corrige a la main ; un titre qui designe un autre aliment
            passe inapercu.
            """.trimIndent()

        /**
         * Le pendant anglais, ecrit et non traduit.
         *
         * Les exemples sont ceux de l'ANSES, qui publie ses propres libelles anglais :
         * ils sont deja plus courts que les francais, donc la consigne insiste moins sur
         * l'abandon et davantage sur ce qu'il faut garder.
         */
        val ENGLISH_PROMPT =
            """
            You shorten food labels from the French ANSES CIQUAL table, whose English
            labels you are given.

            Each line you receive carries a code, a tab, then the published label.
            Answer one line per food: the same code, a tab, the short title.
            Nothing else -- no preamble, no numbering, no comment line.

            The short title must:
            - name the food the way someone would say it at the table;
            - fit in ${'$'}{ShortNamesCsv.MAX_LENGTH} characters, and stay shorter than the label;
            - keep what tells this entry apart from another of the same food -- how it is
              cooked, its fat content, whether it is raw, canned or sweetened;
            - drop what tells nothing apart: "unspecified", "average food",
              "all types of", repetitions of the generic name;
            - start with a capital letter.

            Examples:
            Chicken, breast, skinless, oven-cooked, without added fat -> Oven-cooked chicken breast
            Fruit preparation, all types of fruit, no added sugar, prepacked -> Fruit preparation, no sugar
            Yoghurt or dairy speciality, plain, whole milk -> Plain whole-milk yoghurt

            If a label cannot be shortened without lying, omit its line.
            An omission is fixed by hand; a title that names another food goes unnoticed.
            """.trimIndent()

        /** Sans branche `else` : une langue de plus ne compile pas sans sa consigne. */
        val ContentLanguage.systemPrompt: String
            get() = when (this) {
                ContentLanguage.FRENCH -> FRENCH_PROMPT
                ContentLanguage.ENGLISH -> ENGLISH_PROMPT
            }
    }
}

/**
 * La réponse, relue sans croire le modèle sur parole.
 *
 * **Une ligne qui ne porte pas de tabulation est ignorée**, ce qui absorbe un
 * préambule, une ligne vide ou un commentaire ajouté malgré la consigne. Ce qui n'est
 * pas absorbé ici l'est plus loin : [ShortNames] écarte les codes qu'on n'a pas
 * demandés et les titres qui ne raccourcissent rien.
 *
 * Deux tolérances plutôt qu'un échec, pour la même raison qu'en [D72][decisions] : un
 * lot de cinquante titres perdu parce que le modèle a écrit « Voici : » en tête serait
 * une dépense refaite pour rien.
 *
 * Hors de la classe pour être éprouvable sans réseau : c'est la seule partie de cet
 * échange qui décide de quelque chose.
 *
 * [decisions]: docs/11-decisions.md
 */
internal fun parseShortNames(answer: String): Map<String, String> = answer
    .lineSequence()
    .mapNotNull { line ->
        val separator = line.indexOf('\t')
        if (separator <= 0) null else line.take(separator).trim() to line.drop(separator + 1).trim()
    }
    .filter { (code, title) -> code.isNotEmpty() && title.isNotEmpty() }
    .toMap()
