package app.hexavore.integration.reports

import androidx.core.content.FileProvider

/**
 * Le fournisseur de ce module, **et sa propre classe**.
 *
 * `androidx.core.content.FileProvider` est déjà déclaré par `:feature:capture` pour les
 * photos en route vers un modèle. Deux déclarations du même nom de classe sont, pour la
 * fusion des manifestes, le même élément : elle refuse de choisir entre deux autorités
 * et deux listes de chemins, et le build s'arrête là.
 *
 * Une sous-classe vide suffit à en faire deux fournisseurs distincts — chacun avec son
 * autorité, chacun ne donnant accès qu'à son dossier. C'est ce qui garde la règle qui
 * compte : celui-ci ne peut remettre que les fichiers d'un rapport en cours.
 */
internal class ReportFiles : FileProvider()
