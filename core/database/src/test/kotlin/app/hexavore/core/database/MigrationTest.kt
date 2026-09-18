package app.hexavore.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les migrations, éprouvées sur des données et pas seulement sur un schéma.
 *
 * Le mécanisme existait avant d'avoir quoi que ce soit à migrer, et c'était tout
 * son intérêt : la première vraie migration s'y ajoute au lieu de l'inaugurer.
 *
 * Ces tests partent d'une base de version 1 **peuplée**, migrent, et relisent le
 * contenu. Vérifier que la migration ne plante pas ne prouverait rien : une
 * migration qui perd une colonne, réécrit un `NULL` en `0` ou vide une table
 * réussit parfaitement de ce point de vue.
 *
 * Ils tournent sous Robolectric et non sur un appareil : un test de migration qu'il
 * faut brancher un téléphone pour exécuter est un test qu'on n'exécute pas.
 *
 * @see docs/12-plan-de-developpement.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            HexavoreDatabase::class.java,
        )

    @Test
    fun `le schema vivant correspond a celui qui est versionne`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate()
    }

    @Test
    fun `un journal ecrit avant la migration se relit apres`() {
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT display_name, quantity, kcal FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne de journal a disparu", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(150.0, row.getDouble(1), 0.0)
                assertEquals(232.0, row.getDouble(2), 0.0)
            }
        }
    }

    @Test
    fun `une valeur inconnue reste inconnue apres la migration`() {
        // La faute qu'une recopie de table rend facile : remplir les colonnes
        // nullables avec un defaut. Trois mois de journal deviendraient trois mois
        // sans fibres, et rien ne le dirait.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT fiber_g, protein_g FROM food_entry WHERE id = 'e1'").use { row ->
                row.moveToFirst()
                assertTrue("les fibres ont ete remplies par un defaut", row.isNull(0))
                assertEquals(4.8, row.getDouble(1), 0.0)
            }
        }
    }

    @Test
    fun `une ligne d avant le catalogue ne se voit pas attribuer de provenance`() {
        // NULL est exact : ces lignes ont ete tapees a la main, elles ne viennent
        // d'aucune fiche. Leur en inventer une fausserait le seul usage de cette
        // colonne, qui est de dire d'ou vient un chiffre.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT food_id FROM food_entry WHERE id = 'e1'").use { row ->
                row.moveToFirst()
                assertTrue("food_id devrait etre nul", row.isNull(0))
            }
        }
    }

    @Test
    fun `le plat et son lien de suppression survivent`() {
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.execSQL("DELETE FROM dish WHERE id = 'd1'")

            database.query("SELECT COUNT(*) FROM food_entry").use { row ->
                row.moveToFirst()
                assertEquals("la cascade dish -> food_entry a ete perdue", 0, row.getInt(0))
            }
        }
    }

    @Test
    fun `supprimer un aliment ne supprime pas les lignes qui le citaient`() {
        // Le pendant du precedent, et l'invariant du projet : un journal est un
        // registre d'evenements. Supprimer un aliment personnel defait la
        // provenance, il n'ampute pas l'historique.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.seedFood()
            database.execSQL("UPDATE food_entry SET food_id = 'f1' WHERE id = 'e1'")

            database.execSQL("DELETE FROM food WHERE id = 'f1'")

            database.query("SELECT display_name, kcal, food_id FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne a ete supprimee avec l aliment", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(232.0, row.getDouble(1), 0.0)
                assertTrue("le lien de provenance aurait du se defaire", row.isNull(2))
            }
        }
    }

    @Test
    fun `deux aliments personnels sans reference cohabitent`() {
        // L'index unique porte sur (source, source_ref), et deux NULL ne sont jamais
        // egaux en SQLite : c'est ce qui le rend partiel sans clause WHERE. Un seul
        // aliment par code CIQUAL, mais autant d'aliments personnels que voulu.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "CUSTOM", reference = null)
            database.seedFood(id = "f2", source = "CUSTOM", reference = null)

            database.query("SELECT COUNT(*) FROM food").use { row ->
                row.moveToFirst()
                assertEquals(2, row.getInt(0))
            }
        }
    }

    @Test
    fun `un meme code CIQUAL ne peut pas entrer deux fois`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "CIQUAL", reference = "13039")
            val duplicate = runCatching { database.seedFood(id = "f2", source = "CIQUAL", reference = "13039") }

            assertTrue("le doublon aurait du etre refuse", duplicate.isFailure)
        }
    }

    // --- Version 6 -> 7 : un plat porte un nom ---------------------------------

    @Test
    fun `un plat ecrit avant les titres n en recoit aucun`() {
        // Personne n'a nomme ces plats ni choisi leur moment : l'application ne le
        // demandait pas. Les remplir ici affirmerait un choix que personne n'a fait,
        // et le ferait pour des mois de journal d'un coup. Leur moment se deduit de
        // leur heure a l'affichage, ce qui est la bonne lecture dans le cas courant.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT title, moment FROM dish WHERE id = 'd1'").use { row ->
                assertTrue("le plat a disparu", row.moveToFirst())
                assertTrue("un titre a ete invente", row.isNull(0))
                assertTrue("un moment a ete invente", row.isNull(1))
            }
        }
    }

    @Test
    fun `la migration des titres garde les lignes du plat`() {
        // `ALTER TABLE ADD COLUMN` ne recopie pas la table, donc rien ne se perd --
        // mais c'est la propriete qui compte, pas le moyen, et la prochaine migration
        // de cette table pourrait, elle, recopier.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT display_name FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne de journal a disparu", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
            }
        }
    }

    // --- Version 2 -> 3 : profil, poids, objectifs versionnes ------------------

    @Test
    fun `un journal ecrit avant la tranche 4 se relit apres`() {
        // Trois tables neuves et rien qui bouge : c'est la meilleure espece de
        // migration, et c'est cette propriete-la qu'on eprouve.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT display_name, kcal, fiber_g FROM food_entry WHERE id = 'e1'").use { row ->
                assertTrue("la ligne de journal a disparu", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(232.0, row.getDouble(1), 0.0)
                assertTrue("les fibres ont ete remplies par un defaut", row.isNull(2))
            }
        }
    }

    @Test
    fun `la migration n invente ni profil ni objectif`() {
        // Un profil « moyen » produirait un objectif que personne n'a demande, et les
        // journees deja notees se retrouveraient jugees sur une regle qu'elles
        // n'avaient pas. L'absence est la reponse exacte.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            assertEquals("un profil a ete invente", 0, database.count("profile"))
            assertEquals("un objectif a ete invente", 0, database.count("goal"))
            assertEquals("une pesee a ete inventee", 0, database.count("weight_entry"))
        }
    }

    @Test
    fun `deux objectifs actifs sont refuses par la base`() {
        // L'invariant de D04, tenu par un index unique et non par une convention
        // d'ecriture. Deux NULL ne se heurtent jamais en SQLite : c'est `active_key`
        // qui les fait entrer en collision.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = null)
            val doublon = runCatching { database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null) }

            assertTrue("le second objectif actif aurait du etre refuse", doublon.isFailure)
        }
    }

    @Test
    fun `un objectif clos laisse la place a un nouveau`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = null)
            database.execSQL("UPDATE goal SET ended_at = '2026-06-01', active_key = id WHERE ended_at IS NULL")
            database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null)

            assertEquals(2, database.count("goal"))
        }
    }

    @Test
    fun `une journee est comparee a l objectif actif ce jour-la`() {
        // La requete que D04 achete. Le 15 mai releve du premier objectif, le 15
        // juillet du second, et la borne de fin est exclue : le 1er juin appartient
        // au nouveau.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = "2026-06-01", kcal = 2500.0)
            database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null, kcal = 2200.0)

            assertEquals(2500.0, database.goalKcalOn("2026-05-15"), 0.0)
            assertEquals(2200.0, database.goalKcalOn("2026-06-01"), 0.0)
            assertEquals(2200.0, database.goalKcalOn("2026-07-15"), 0.0)
        }
    }

    @Test
    fun `une journee anterieure au premier objectif n en a aucun`() {
        // Et non l'objectif courant applique retroactivement : une journee notee
        // avant qu'un objectif existe n'a rien a quoi se comparer.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedGoal(id = "g1", startedAt = "2026-01-01", endedAt = null)

            database.query(GOAL_ON_SQL, arrayOf("2025-12-31")).use { row ->
                assertEquals("une journee d avant le premier objectif en a recu un", 0, row.count)
            }
        }
    }

    @Test
    fun `une seule pesee par jour, la derniere remplace`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedWeight(id = "w1", date = "2026-08-10", kg = 88.0)
            val doublon = runCatching { database.seedWeight(id = "w2", date = "2026-08-10", kg = 87.5) }

            assertTrue("deux pesees le meme jour auraient du etre refusees", doublon.isFailure)
        }
    }

    @Test
    fun `le profil est une ligne unique`() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedProfile()
            val doublon = runCatching { database.seedProfile() }

            assertTrue("un second profil aurait du etre refuse", doublon.isFailure)
        }
    }

    // --- Version 3 -> 4 : le verrou par compteur devient un mode ---------------

    @Test
    fun `un objectif ecrit avant la version 4 se relit apres, avec ses six chiffres`() {
        // La table est recreee sans `manual_fields` : SQLite ne sait pas supprimer une
        // colonne sous minSdk 26. Une recopie perd tout ce qu'on a oublie de nommer, et
        // ce sont les six chiffres qui comptent -- ils ne doivent pas bouger d'un gramme.
        helper.createDatabase(TEST_DATABASE, 3).use { it.seedGoalVersionThree() }

        migrate().use { database ->
            database.query(
                "SELECT origin, strategy, kcal, protein_g, fiber_g, target_weight_kg FROM goal WHERE id = 'g1'",
            ).use { row ->
                assertTrue("l objectif a disparu dans la recopie", row.moveToFirst())
                assertEquals("CALCULATED", row.getString(0))
                assertEquals("LOSE", row.getString(1))
                assertEquals(2525.0, row.getDouble(2), 0.0)
                assertEquals(144.0, row.getDouble(3), 0.0)
                assertEquals(35.0, row.getDouble(4), 0.0)
                assertEquals(80.0, row.getDouble(5), 0.0)
            }
        }
    }

    @Test
    fun `l objectif clos garde sa date de fin apres la recopie`() {
        // Un objectif clos et un objectif courant : c'est la paire qui fait tomber une
        // recopie qui aurait decale une colonne, puisque `ended_at` et `active_key`
        // sont les deux seules a differer entre eux.
        helper.createDatabase(TEST_DATABASE, 3).use { database ->
            database.seedGoalVersionThree(id = "g1", endedAt = "2026-06-01")
            database.seedGoalVersionThree(id = "g2", endedAt = null)
        }

        migrate().use { database ->
            assertEquals(2, database.count("goal"))
            database.query("SELECT ended_at, active_key FROM goal WHERE id = 'g1'").use { row ->
                row.moveToFirst()
                assertEquals("2026-06-01", row.getString(0))
                assertEquals("g1", row.getString(1))
            }
        }
    }

    @Test
    fun `l index qui interdit deux objectifs actifs survit a la recreation de la table`() {
        // Le piege de cette migration. Les index ne suivent pas une table renommee :
        // les oublier laisse la migration reussir, et l'invariant « au plus un objectif
        // actif » disparait chez ceux qui migrent, chez eux seulement.
        helper.createDatabase(TEST_DATABASE, 3).use { it.seedGoalVersionThree() }

        migrate().use { database ->
            val doublon = runCatching { database.seedGoal(id = "g2", startedAt = "2026-06-01", endedAt = null) }

            assertTrue("le second objectif actif aurait du etre refuse", doublon.isFailure)
        }
    }

    // --- Version 4 -> 5 : les plats favoris ------------------------------------

    @Test
    fun `un journal ecrit avant les favoris se relit apres`() {
        // `dish` est recreee pour recevoir sa cle etrangere -- SQLite ne sait pas en
        // ajouter une apres coup. Une recopie perd tout ce qu'on a oublie de nommer.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.query("SELECT date, source, logged_at, favorite_id FROM dish WHERE id = 'd1'").use { row ->
                assertTrue("le plat a disparu dans la recopie", row.moveToFirst())
                assertEquals("2026-08-08", row.getString(0))
                assertEquals("MANUAL", row.getString(1))
                assertEquals(1000L, row.getLong(2))
                assertTrue("aucun plat ne venait d un favori : la colonne doit rester nulle", row.isNull(3))
            }
        }
    }

    @Test
    fun `supprimer un favori delie les plats qui en venaient, sans les effacer`() {
        // Un journal est un registre d'evenements : le modele qui a servi a composer
        // un repas n'a pas a emporter le repas en disparaissant. C'est la meme regle
        // que pour un aliment personnel supprime.
        helper.createDatabase(TEST_DATABASE, 1).use { it.seedVersionOne() }

        migrate().use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.seedFavorite()
            database.execSQL("UPDATE dish SET favorite_id = 'fav1' WHERE id = 'd1'")

            database.execSQL("DELETE FROM favorite_dish WHERE id = 'fav1'")

            database.query("SELECT favorite_id FROM dish WHERE id = 'd1'").use { row ->
                assertTrue("le plat a ete supprime avec son favori", row.moveToFirst())
                assertTrue("le lien aurait du se defaire", row.isNull(0))
            }
            assertEquals("les composants devaient partir en cascade", 0, database.count("favorite_component"))
        }
    }

    @Test
    fun `deux favoris ne peuvent pas porter le meme nom normalise`() {
        // C'est l'index qui tient la regle, pas la seule discipline d'ecriture :
        // « Petit-dej » et « petit dej » ne se distinguent pas dans une liste.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFavorite(id = "fav1", nameSearch = "petit dej")
            val doublon = runCatching { database.seedFavorite(id = "fav2", nameSearch = "petit dej") }

            assertTrue("le doublon de nom aurait du etre refuse", doublon.isFailure)
        }
    }

    @Test
    fun `supprimer un aliment delie le composant sans vider le favori`() {
        // Les six valeurs enregistrees avec le composant prennent alors le relais :
        // c'est la raison pour laquelle elles sont ecrites meme quand une fiche est
        // citee.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.seedFood(id = "f1")
            database.seedFavorite()
            database.execSQL("UPDATE favorite_component SET food_id = 'f1' WHERE favorite_id = 'fav1'")

            database.execSQL("DELETE FROM food WHERE id = 'f1'")

            database.query("SELECT food_id, display_name, kcal FROM favorite_component WHERE favorite_id = 'fav1'")
                .use { row ->
                    assertTrue("le composant a ete supprime avec l aliment", row.moveToFirst())
                    assertTrue("le lien aurait du se defaire", row.isNull(0))
                    assertEquals("Flocons", row.getString(1))
                    assertEquals(218.0, row.getDouble(2), 0.0)
                }
        }
    }

    // --- Version 5 -> 6 : le cache d'Open Food Facts prend date ----------------

    @Test
    fun `un aliment ecrit avant le cache garde ses valeurs et n en gagne aucune`() {
        // Deux colonnes ajoutees par ALTER TABLE, sans recopie. Ce qu'on verifie est
        // que les fiches existantes restent a NULL : un `0` par defaut sur is_liquid
        // affirmerait « solide » de 3 484 aliments que personne n'a regardes.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "CIQUAL", reference = "13039")

            database.query("SELECT name, kcal_100, is_liquid, fetched_at FROM food WHERE id = 'f1'").use { row ->
                assertTrue("la fiche a disparu", row.moveToFirst())
                assertEquals("Riz basmati cuit", row.getString(0))
                assertEquals(155.0, row.getDouble(1), 0.0)
                assertTrue("« on ne sait pas » n est pas « solide »", row.isNull(2))
                assertTrue("une date de recuperation a ete inventee", row.isNull(3))
            }
        }
    }

    @Test
    fun `l index qui interdit le doublon au double scan survit a la migration`() {
        // Un ALTER TABLE ne touche pas les index -- mais c'est une propriete du chemin
        // choisi, pas du resultat. Une migration future qui recopierait cette table la
        // perdrait sans que la validation de schema le voie (D60), et le double scan
        // d'un meme produit ferait deux fiches.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "OFF", reference = "5449000000996")
            val doublon = runCatching { database.seedFood(id = "f2", source = "OFF", reference = "5449000000996") }

            assertTrue("le doublon au double scan aurait du etre refuse", doublon.isFailure)
        }
    }

    @Test
    fun `un aliment personnel et un produit en cache partagent un code-barres`() {
        // On cree la fiche hors ligne, on rescanne connecte : les deux existent, et
        // l'index unique porte sur le couple (source, source_ref), donc les laisse
        // cohabiter. C'est ce que `byBarcode` departage, en donnant la main a celle
        // que l'utilisateur a saisie.
        helper.createDatabase(TEST_DATABASE, 1).close()

        migrate().use { database ->
            database.seedFood(id = "f1", source = "CUSTOM", reference = "5449000000996")
            database.seedFood(id = "f2", source = "OFF", reference = "5449000000996")

            assertEquals(2, database.count("food"))
        }
    }

    // --- Outillage ------------------------------------------------------------

    /**
     * `validateDroppedTables = true` : une table oubliée dans une migration future
     * doit faire échouer ce test, pas survivre en silence.
     *
     * L'opérateur d'étalement copie le tableau ; sur une chaîne qui compte une
     * migration, l'argument de performance ne s'applique pas, et l'API de Room
     * n'offre pas d'autre voie.
     */
    @Suppress("SpreadOperator")
    private fun migrate(): SupportSQLiteDatabase = helper.runMigrationsAndValidate(
        TEST_DATABASE,
        HexavoreDatabase.VERSION,
        true,
        *HexavoreDatabase.MIGRATIONS.toTypedArray(),
    )

    /** Un plat et sa ligne, écrits comme la version 1 les écrivait. */
    private fun SupportSQLiteDatabase.seedVersionOne() {
        execSQL(
            """
            INSERT INTO dish (id, date, source, logged_at, created_at, updated_at)
            VALUES ('d1', '2026-08-08', 'MANUAL', 1000, 1000, 1000)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO food_entry (
                id, dish_id, display_name, quantity, unit, grams,
                kcal, protein_g, carb_g, sugar_g, fat_g, fiber_g, created_at, updated_at
            ) VALUES (
                'e1', 'd1', 'Riz basmati cuit', 150.0, 'G', 150.0,
                232.0, 4.8, 49.5, 0.1, 0.5, NULL, 1000, 1000
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.seedFood(
        id: String = "f1",
        source: String = "CUSTOM",
        reference: String? = null,
    ) {
        execSQL(
            """
            INSERT INTO food (
                id, source, source_ref, name, name_search, brand,
                kcal_100, protein_100, carb_100, sugar_100, fat_100, fiber_100,
                saturated_fat_100, salt_100, default_serving_g,
                last_used_at, use_count, is_favorite, created_at, updated_at
            ) VALUES (
                ?, ?, ?, 'Riz basmati cuit', 'riz basmati cuit', NULL,
                155.0, 3.2, 33.0, 0.1, 0.3, NULL,
                NULL, NULL, NULL,
                NULL, 0, 0, 1000, 1000
            )
            """.trimIndent(),
            arrayOf(id, source, reference),
        )
    }

    private fun SupportSQLiteDatabase.count(table: String): Int = query("SELECT COUNT(*) FROM $table").use { row ->
        row.moveToFirst()
        row.getInt(0)
    }

    private fun SupportSQLiteDatabase.goalKcalOn(date: String): Double =
        query(GOAL_ON_SQL, arrayOf(date, date)).use { row ->
            assertTrue("aucun objectif pour le $date", row.moveToFirst())
            row.getDouble(0)
        }

    @Suppress("LongParameterList")
    private fun SupportSQLiteDatabase.seedGoal(
        id: String,
        startedAt: String,
        endedAt: String?,
        kcal: Double = 2500.0,
    ) {
        execSQL(
            """
            INSERT INTO goal (
                id, started_at, ended_at, active_key, origin, strategy,
                target_weight_kg, target_date,
                kcal, protein_g, carb_g, sugar_g, fat_g, fiber_g, created_at
            ) VALUES (?, ?, ?, ?, 'CALCULATED', 'LOSE', 80.0, '2027-02-08', ?, 144.0, 312.0, 63.0, 70.0, 35.0, 1000)
            """.trimIndent(),
            // `active_key` vaut 1 tant que l'objectif court, l'identifiant une fois
            // clos : c'est ce qui fait entrer deux objectifs actifs en collision.
            arrayOf(id, startedAt, endedAt, if (endedAt == null) "1" else id, kcal),
        )
    }

    /**
     * Un objectif tel que la **version 3** l'écrivait, `manual_fields` compris.
     *
     * Deux compteurs y sont déclarés fixés à la main. C'est le seul état que la colonne
     * ait jamais pu prendre en base, et c'est celui sur lequel la migration 3 → 4 doit
     * être éprouvée : une base vide traverserait n'importe quelle recopie.
     */
    private fun SupportSQLiteDatabase.seedGoalVersionThree(id: String = "g1", endedAt: String? = null) {
        execSQL(
            """
            INSERT INTO goal (
                id, started_at, ended_at, active_key, origin, strategy,
                target_weight_kg, target_date,
                kcal, protein_g, carb_g, sugar_g, fat_g, fiber_g, manual_fields, created_at
            ) VALUES (
                ?, '2026-01-01', ?, ?, 'CALCULATED', 'LOSE', 80.0, '2027-02-08',
                2525.0, 144.0, 312.0, 63.0, 70.0, 35.0, 'PROTEIN,FAT', 1000
            )
            """.trimIndent(),
            arrayOf(id, endedAt, if (endedAt == null) "1" else id),
        )
    }

    /** Un plat favori d'une ligne, tel que l'étoile en écrit un. */
    private fun SupportSQLiteDatabase.seedFavorite(id: String = "fav1", nameSearch: String = "petit dej") {
        execSQL(
            """
            INSERT INTO favorite_dish (id, name, name_search, use_count, created_at)
            VALUES (?, 'Petit-déj', ?, 0, 1000)
            """.trimIndent(),
            arrayOf(id, nameSearch),
        )
        execSQL(
            """
            INSERT INTO favorite_component (
                favorite_id, position, food_id, display_name, quantity, unit, grams,
                kcal, protein_g, carb_g, sugar_g, fat_g, fiber_g
            ) VALUES (?, 0, NULL, 'Flocons', 60.0, 'g', 60.0, 218.0, 8.1, 36.0, 0.6, 4.2, NULL)
            """.trimIndent(),
            arrayOf(id),
        )
    }

    private fun SupportSQLiteDatabase.seedWeight(id: String, date: String, kg: Double) {
        execSQL(
            "INSERT INTO weight_entry (id, date, weight_kg, created_at) VALUES (?, ?, ?, 1000)",
            arrayOf(id, date, kg),
        )
    }

    private fun SupportSQLiteDatabase.seedProfile() {
        execSQL(
            """
            INSERT INTO profile (id, birth_date, sex, height_cm, activity_level, unit_system, created_at, updated_at)
            VALUES ('singleton', '1991-03-04', 'MALE', 182.0, 'MODERATE', 'METRIC', 1000, 1000)
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"

        const val GOAL_ON_SQL =
            """
            SELECT kcal FROM goal
            WHERE started_at <= ? AND (ended_at IS NULL OR ended_at > ?)
            ORDER BY started_at DESC LIMIT 1
            """
    }
}

/**
 * Version d'Android simulée par Robolectric.
 *
 * Choisie parmi celles que la version de Robolectric embarque, et non alignée sur
 * `compileSdk` : ce test porte sur SQLite et sur le schéma, pas sur une API dont la
 * version importerait.
 */
internal const val ROBOLECTRIC_SDK = 33
