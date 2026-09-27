package app.hexavore.tooling.ciqual

import app.hexavore.domain.language.ContentLanguage
import java.io.File

/**
 * La tache `generateShortNames` : les libelles de l'ANSES vers
 * `ciqual/short-names-<langue>.csv`.
 *
 * **Une langue par passe, et elle se nomme.** Raccourcir des libelles anglais avec la
 * consigne francaise produirait des titres francais pour des fiches anglaises, et rien
 * ne l'aurait signale : le controle d'import ne verifie que la longueur.
 *
 * Elle n'est branchee sur aucun cycle de vie de Gradle, comme `importCiqual` : elle
 * coute de l'argent et un compte, et rien de tout cela n'a sa place dans un build.
 * On la lance quand l'ANSES publie, ou quand on veut completer ce qui manque.
 *
 * Le fichier produit est **relu et corrige a la main** ensuite, puis
 * `importCiqual` le verse dans la base. Deux taches et non une : ce qui se paie et
 * ce qui se rejoue gratuitement ne se melangent pas, et une relecture doit pouvoir
 * s'intercaler entre les deux.
 *
 * Sortie en ASCII sans accent : une console Windows n'est pas en UTF-8 par defaut.
 */
fun main(args: Array<String>) {
    require(args.size == ARGUMENT_COUNT) {
        "Usage : generateShortNames <dossier ciqual> <langue> <modele> <cle>"
    }
    val (directory, tag, model) = args.toList()
    val apiKey = args.last()

    val language = ContentLanguage.ofTag(tag)
    requireNotNull(language) {
        "Langue [$tag] inconnue. Attendu : ${ContentLanguage.entries.joinToString { it.tag }}."
    }

    // La cle n'est ni journalisee ni ecrite : elle traverse ce point d'entree et
    // s'arrete au client. Un message d'erreur qui la citerait la ferait entrer dans
    // un journal de build, donc dans un endroit qu'on partage sans y penser.
    require(apiKey.isNotBlank()) {
        "Aucune cle. Relancer avec -PanthropicApiKey=... ; elle n'est ni lue d'un fichier ni conservee."
    }

    val files = CatalogueFiles(directory = File(directory), output = File(directory))
    val table = CiqualArchive(files.archive).use { CiqualReader(it).read() }
    val labels = table.foods.map { LongLabel(code = it.code, name = it.label(language).name) }
    val file = files.shortNames(language)
    val known = ShortNamesCsv.read(file, labels.associate { it.code to it.name })

    val names = ShortNames(AnthropicShortNamer(apiKey = apiKey, model = model, language = language))
    val pending = names.pending(labels, known)
    announce(labels, known, pending, model, language)
    if (pending.isEmpty()) return

    val produced = names.generate(labels, known) { acquired ->
        println("  ${acquired.size - known.size} / ${pending.size} titres")
        // **Apres chaque lot, pas a la fin.** Une passe coupee au trentieme lot doit
        // reprendre au trentieme ; ecrire une seule fois ferait tout repayer.
        ShortNamesCsv.write(file, acquired)
    }

    ShortNamesCsv.write(file, produced)
    report(file, produced, labels)
}

private const val ARGUMENT_COUNT = 4

private fun announce(
    labels: List<LongLabel>,
    known: List<CiqualShortName>,
    pending: List<LongLabel>,
    model: String,
    language: ContentLanguage,
) {
    val worth = labels.count { it.name.length > ShortNamesCsv.WORTH_SHORTENING }
    println("Titres courts en ${language.tag}, modele $model")
    println("  ${labels.size} aliments, dont $worth libelles de plus de ${ShortNamesCsv.WORTH_SHORTENING} caracteres")
    println("  ${known.size} titres deja ecrits, ${pending.size} a demander")
    if (pending.isEmpty()) {
        println("  Rien a faire. Supprimer une ligne du fichier pour la redemander.")
        return
    }
    val batches = (pending.size + ShortNames.BATCH_SIZE - 1) / ShortNames.BATCH_SIZE
    println("  $batches requete(s) de ${ShortNames.BATCH_SIZE} au plus")
}

private fun report(file: File, produced: List<CiqualShortName>, labels: List<LongLabel>) {
    val missing = labels
        .filter { it.name.length > ShortNamesCsv.WORTH_SHORTENING }
        .count { label -> produced.none { it.code == label.code } }

    println("${file.name} ecrit : ${produced.size} titres")
    // Ce qui manque encore n'est pas un echec : un libelle qu'aucun raccourci ne
    // decrit sans mentir doit rester entier. Relancer la tache le redemandera.
    if (missing > 0) println("  $missing libelle(s) long(s) sans titre -- relancer la tache les redemandera")
}
