package app.hexavore.core.testing

import app.hexavore.domain.ai.EstimationOutcome
import app.hexavore.domain.ai.NutritionEstimator
import app.hexavore.domain.food.FoodSearch
import app.hexavore.domain.usecase.CreateDraft
import app.hexavore.domain.usecase.ResolveFoodLabel
import app.hexavore.domain.usecase.ResolveRecognition
import java.time.LocalDate

/**
 * La résolution d'une proposition, pour les écrans qui analysent.
 *
 * Depuis [D176][decisions], l'analyse résout ce que le modèle propose avant de le
 * déposer : l'écran d'IA et la barre du bas ont donc besoin d'une résolution pour
 * construire leur cas d'usage.
 *
 * **Sans repli par défaut.** Ce que ces écrans éprouvent — l'attente, l'annulation,
 * l'échec, ce qui part — ne parle pas de l'étape 4, et un estimateur qui répondrait
 * remplirait des lignes qu'aucun de leurs cas ne regarde. Celui qui veut voir l'attente
 * de l'estimation passe le sien.
 *
 * Le catalogue est vide par défaut : un libellé n'y rejoint rien, donc **toute ligne part
 * à l'étape 4**, et la ligne garde son nom et sa quantité.
 *
 * [decisions]: docs/11-decisions.md
 */
fun resolutionForTests(
    catalogue: FoodSearch = InMemoryFoodCatalog(),
    estimate: NutritionEstimator = NutritionEstimator { EstimationOutcome.Estimated(emptyList()) },
): ResolveRecognition {
    val clock = FixedClock.atNoon(LocalDate.of(2026, 10, 25))
    return ResolveRecognition(
        resolve = ResolveFoodLabel(catalogue, FixedLanguage()),
        create = CreateDraft(clock, SequentialIdGenerator(), InMemorySelectedDay(clock.today())),
        estimate = estimate,
        languages = FixedLanguage(),
    )
}
