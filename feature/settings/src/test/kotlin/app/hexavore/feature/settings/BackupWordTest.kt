package app.hexavore.feature.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Le verrou du bouton qui détruit, et le nom du fichier qu'on emporte.
 *
 * **Taper un mot est le seul verrou qui demande de lire.** L'effacement est le seul
 * geste du projet qu'aucune barre d'annulation ne rattrape, et une double confirmation
 * par boutons s'apprend à traverser en deux frappes ([docs/09][donnees]).
 *
 * Ce qui se juge ici est la **tolérance** : elle est délibérée, et elle a une limite.
 *
 * [donnees]: docs/09-donnees-et-sauvegarde.md
 */
class BackupWordTest {
    @Test
    fun `le mot exact ouvre le bouton`() {
        assertTrue("SUPPRIMER".matches(MOT))
    }

    @Test
    fun `la casse ne compte pas`() {
        // Quelqu'un qui ecrit « supprimer » sur un clavier sans majuscules automatiques
        // a lu la phrase et l'a comprise. Lui refuser le geste ne protege plus rien --
        // cela punit son clavier.
        assertTrue("supprimer".matches(MOT))
        assertTrue("Supprimer".matches(MOT))
    }

    @Test
    fun `les espaces de bord ne comptent pas`() {
        // La correction automatique d'Android ajoute volontiers une espace finale.
        assertTrue(" SUPPRIMER ".matches(MOT))
    }

    @Test
    fun `un autre mot n ouvre rien`() {
        assertFalse("EFFACER".matches(MOT))
        assertFalse("SUPPRIME".matches(MOT))
    }

    @Test
    fun `un mot vide n ouvre rien`() {
        // Le cas qui compte le plus : c'est l'etat du champ a l'ouverture du dialogue,
        // et un bouton actif a ce moment-la rendrait tout le verrou decoratif.
        assertFalse("".matches(MOT))
        assertFalse("   ".matches(MOT))
    }

    @Test
    fun `un mot qui contient le mot n ouvre rien`() {
        // La comparaison est une egalite et non une inclusion : sans cela, une frappe
        // au hasard finirait par ouvrir le bouton.
        assertFalse("JE VEUX SUPPRIMER".matches(MOT))
    }

    @Test
    fun `le nom propose porte le jour`() {
        // La date et non l'horodatage : ce nom sert a reconnaitre un fichier dans une
        // liste, et deux exports du meme jour se distinguent par ce que le systeme
        // ajoute lui-meme.
        assertTrue(backupFileName(LocalDate.of(2026, 8, 24)).contains("2026-08-24"))
    }

    @Test
    fun `le nom propose dit ce que le fichier est`() {
        // L'extension compte : sans elle, les applications de fichiers renomment en
        // « .txt » et la sauvegarde devient illisible sans que rien ne l'ait dit.
        // C'est une archive depuis que les photos voyagent avec le journal ; celui-ci
        // garde son propre nom a l'interieur.
        assertTrue(backupFileName(LocalDate.of(2026, 8, 24)).endsWith(".zip"))
    }

    private companion object {
        const val MOT = "SUPPRIMER"
    }
}
