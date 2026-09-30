package app.hexavore.domain.report

/**
 * Ce qu'un plantage laisse derrière lui.
 *
 * ### Pourquoi au redémarrage et pas sur le moment
 *
 * Une application qui vient de planter n'a plus d'écran : la pile est en train de se
 * dérouler, le processus est condamné, et ouvrir une boîte de dialogue depuis un
 * gestionnaire d'exception non rattrapée ne marche qu'une fois sur deux — quand cela ne
 * provoque pas un second plantage par-dessus le premier.
 *
 * Ce qui se fait sur le moment tient en une ligne : **écrire la trace**. La proposition
 * arrive au lancement suivant, où l'application est entière et où quelqu'un regarde
 * ([D138][decisions]).
 *
 * ### Ce qu'une trace contient, et ce qu'elle ne contient pas
 *
 * Des noms de classes, des numéros de ligne, un modèle de téléphone, une version
 * d'Android. **Aucune donnée de journal** : ni aliment, ni poids, ni clé. Ce n'est pas
 * une précaution qu'on prend, c'est ce qu'est une trace de pile — et le rapport se lit
 * avant d'être envoyé, donc cela se vérifie.
 *
 * [decisions]: docs/11-decisions.md
 */
interface CrashReports {
    /**
     * La trace du dernier plantage, ou `null` — ce qui est le cas normal.
     *
     * **Une seule**, la plus récente : quelqu'un qui plante trois fois de suite n'enverra
     * pas trois courriels, et la troisième trace dit ce que les deux autres disaient.
     */
    suspend fun pending(): String?

    /**
     * Oublie ce qui attend.
     *
     * Appelée que le rapport soit envoyé **ou refusé** : une proposition qu'on décline
     * et qui revient à chaque lancement devient une punition pour un plantage dont on
     * n'est pas l'auteur.
     */
    suspend fun clear()
}
