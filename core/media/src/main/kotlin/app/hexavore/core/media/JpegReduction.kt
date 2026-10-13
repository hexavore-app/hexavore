package app.hexavore.core.media

/**
 * Ce que devient une image avant de partir : 1024 px sur le côté long, JPEG 80.
 *
 * **Les deux chiffres viennent de [docs/05][ia] § Coût**, et ils sont la principale
 * réduction de dépense du mode photo : environ 150 Ko et 250 à 400 jetons selon le
 * fournisseur, contre plusieurs mégaoctets pour la photo d'origine. C'est l'utilisateur
 * qui paie ces jetons, et une photo de repas n'a pas besoin de plus pour être lue.
 *
 * [ia]: docs/05-ia.md
 */
const val LONG_SIDE_PX = 1024

/** JPEG 80 : au-delà, on paie des octets qu'aucun modèle ne regarde. */
const val JPEG_QUALITY = 80

/**
 * Le facteur d'échantillonnage à donner au décodeur.
 *
 * **Décoder puis redimensionner ferait passer par un bitmap plein format** — une photo
 * de 12 mégapixels tient dans 48 Mo une fois décodée, et c'est le chemin le plus court
 * vers une `OutOfMemoryError` sur un téléphone qui a autre chose à faire. Le décodeur
 * sait sauter des pixels à la lecture ; encore faut-il lui dire combien.
 *
 * Une puissance de deux, parce que le décodeur d'Android arrondit à la puissance de
 * deux inférieure et qu'un chiffre qu'il corrige en silence est un chiffre qu'on ne
 * contrôle pas. Et **jamais au-delà** de la cible : le facteur retenu est le plus
 * grand qui laisse encore le côté long au-dessus de [LONG_SIDE_PX], pour que la mise à
 * l'échelle finale réduise au lieu d'agrandir.
 */
fun sampleSizeFor(width: Int, height: Int, target: Int = LONG_SIDE_PX): Int {
    var sample = 1
    var longSide = maxOf(width, height)
    while (longSide / 2 >= target) {
        longSide /= 2
        sample *= 2
    }
    return sample
}

/**
 * La taille finale, côté long ramené à [target] et **proportions gardées**.
 *
 * Une image déjà plus petite que la cible n'est pas agrandie : elle n'y gagnerait
 * aucun détail et coûterait des octets pour des pixels inventés.
 *
 * Le côté court est arrondi et **jamais nul** : une image de 2000 × 3 pixels — un scan
 * de ticket, un panorama dégénéré — donnerait zéro par arrondi, et un bitmap de
 * hauteur nulle jette.
 */
fun scaledSizeFor(width: Int, height: Int, target: Int = LONG_SIDE_PX): Pair<Int, Int> {
    val longSide = maxOf(width, height)
    if (longSide <= target || longSide == 0) return width to height

    val ratio = target.toDouble() / longSide
    return maxOf(1, Math.round(width * ratio).toInt()) to maxOf(1, Math.round(height * ratio).toInt())
}
