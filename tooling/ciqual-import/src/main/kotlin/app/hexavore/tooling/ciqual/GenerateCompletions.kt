package app.hexavore.tooling.ciqual

import app.hexavore.domain.nutrition.Macro
import java.io.File

/**
 * La tache `generateCompletions` : les trous de l'ANSES vers `ciqual/completions.csv`.
 *
 * Distincte de `generateShortNames`, et ce n'est pas une symetrie de facade : un titre
 * court est un affichage, une teneur completee est un chiffre invente qui entrera dans
 * un journal alimentaire. Les deux n'appellent ni la meme prudence ni la meme
 * relecture, et chacune doit pouvoir etre lancee sans l'autre.
 *
 * Sortie en ASCII sans accent : une console Windows n'est pas en UTF-8 par defaut.
 */
fun main(args: Array<String>) {
    require(args.size == ARGUMENT_COUNT) {
        "Usage : generateCompletions <dossier ciqual> <modele> <cle>"
    }
    val (directory, model) = args.toList()
    val apiKey = args.last()

    // La cle n'est ni journalisee ni ecrite : elle traverse ce point d'entree et
    // s'arrete au client.
    require(apiKey.isNotBlank()) {
        "Aucune cle. Relancer avec -PanthropicApiKey=... ; elle n'est ni lue d'un fichier ni conservee."
    }

    val files = CatalogueFiles(directory = File(directory), output = File(directory))
    val table = CiqualArchive(files.archive).use { CiqualReader(it).read() }
    failOnUnrecognised(table.unrecognised.size)

    val file = files.completions
    val known = CompletionsCsv.read(file, table.foods.associateBy { it.code })

    val completions = Completions(AnthropicCompleter(apiKey = apiKey, model = model))
    val pending = completions.pending(table.foods, known)
    val kept = completions.kept(table.foods, known)
    announce(table.foods, known, kept, pending, model, completions)

    // Le nettoyage a lieu meme quand il n'y a rien a demander : c'est lui qui realise
    // « la completion est effacee quand la source livre sa mesure ».
    if (pending.isEmpty()) {
        if (kept.size != known.size) CompletionsCsv.write(file, kept)
        return
    }

    val produced = completions.generate(table.foods, known) { acquired ->
        println("  ${acquired.size - kept.size} / ${pending.size} teneurs")
        // Apres chaque lot : une passe coupee au dixieme reprend au dixieme.
        CompletionsCsv.write(file, acquired)
    }

    CompletionsCsv.write(file, produced)
    report(file, produced, pending)
}

private const val ARGUMENT_COUNT = 3

/**
 * Une ecriture CIQUAL inconnue arrete aussi cette tache.
 *
 * Elle lit la meme table que l'import, et une teneur mal interpretee ferait demander
 * une estimation pour un trou qui n'en est pas un -- ou taire un trou reel.
 */
private fun failOnUnrecognised(count: Int) {
    check(count == 0) {
        "$count teneur(s) CIQUAL d'une ecriture inconnue. Lancer importCiqual pour les voir nommees."
    }
}

private fun announce(
    foods: List<CiqualFood>,
    known: List<CiqualCompletion>,
    kept: List<CiqualCompletion>,
    pending: List<Gap>,
    model: String,
    completions: Completions,
) {
    val gaps = completions.gaps(foods)
    val fiches = gaps.map { it.code }.distinct().size

    println("Teneurs completees, modele $model")
    println("  ${foods.size} aliments, dont $fiches avec au moins un trou -- ${gaps.size} teneurs manquantes")
    println("  ${kept.size} teneurs deja ecrites, ${pending.size} a demander")
    reportStale(known, kept)
    if (pending.isEmpty()) {
        println("  Rien a demander. Supprimer une ligne du fichier pour la redemander.")
        return
    }
    val batches = (pending.size + Completions.BATCH_SIZE - 1) / Completions.BATCH_SIZE
    println("  $batches requete(s) de ${Completions.BATCH_SIZE} au plus")
    reportGapsByMacro(pending)
}

/**
 * Les completions que l'ANSES a rendues inutiles, retirees du fichier.
 *
 * On le dit plutot que de le taire : une ligne qui disparait d'un fichier versionne
 * doit s'expliquer, sans quoi le diff se lit comme une perte.
 */
private fun reportStale(known: List<CiqualCompletion>, kept: List<CiqualCompletion>) {
    val stale = known.size - kept.size
    if (stale > 0) println("  $stale completion(s) retiree(s) : l'ANSES publie desormais ces teneurs")
}

/** Ce qui manque, par compteur : c'est ce qui dit si la table a un trou systematique. */
private fun reportGapsByMacro(pending: List<Gap>) {
    val byMacro = pending.groupingBy { it.macro }.eachCount()
    println("  a demander, par compteur :")
    Macro.entries.forEach { println("    ${it.name.padEnd(MACRO_COLUMN_WIDTH)} ${byMacro[it] ?: 0}") }
}

private fun report(file: File, produced: List<CiqualCompletion>, pending: List<Gap>) {
    val demandees = pending.mapTo(mutableSetOf()) { it.code to it.macro }
    val obtenues = produced.count { (it.code to it.macro) in demandees }

    println("${file.name} ecrit : ${produced.size} teneurs")
    // Ce qui manque encore n'est pas un echec : un aliment dont personne ne peut
    // deviner la teneur doit rester sans. Relancer la tache le redemandera.
    val manquantes = pending.size - obtenues
    if (manquantes > 0) println("  $manquantes teneur(s) sans estimation -- relancer la tache les redemandera")
}

private const val MACRO_COLUMN_WIDTH = 10
