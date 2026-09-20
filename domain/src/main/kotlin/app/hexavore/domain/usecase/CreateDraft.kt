package app.hexavore.domain.usecase

import app.hexavore.domain.diary.DraftLine
import app.hexavore.domain.diary.DraftLineId
import app.hexavore.domain.diary.EntryDraft
import app.hexavore.domain.diary.EntrySource
import app.hexavore.domain.diary.MealMoment
import app.hexavore.domain.diary.SelectedDay
import app.hexavore.domain.food.Food
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Fabrique un brouillon vierge et ses lignes.
 *
 * Deux dépendances non déterministes — l'horloge et le générateur d'identifiants —
 * qui ne servent qu'à ça. Les rassembler ici évite qu'un ViewModel les porte pour
 * en faire deux appels, et donne à la journée par défaut un seul endroit où être
 * décidée : celle de l'horloge, jamais `LocalDate.now()`.
 *
 * [source] est un paramètre et non une constante : c'est ce qui permettra à la
 * recherche, au scan et à l'IA de produire le même genre de brouillon sans que rien
 * ne change ici.
 */
class CreateDraft(private val clock: Clock, private val ids: IdGenerator, private val selected: SelectedDay) {
    /**
     * Le jour sur lequel un brouillon s'écrit : celui qu'on regarde, sinon aujourd'hui.
     *
     * **C'est ce qui permet de rattraper un repas oublié.** L'accueil porte une date,
     * et le bouton d'ajout écrit sur elle — sans quoi un plat noté depuis un jour passé
     * atterrirait aujourd'hui, en silence et au mauvais endroit.
     *
     * Un seul endroit décide encore, comme avant : la question a juste deux réponses
     * possibles au lieu d'une.
     */
    private fun date(): LocalDate = selected.current() ?: clock.today()

    /**
     * Le moment auquel ce brouillon se rattache : celui de l'heure qu'il est.
     *
     * **L'heure courante et non celle du jour regardé**, qui n'en a pas : un jour passé
     * est une date, pas un instant. Quelqu'un qui rattrape le dîner d'hier à 9 h du
     * matin voit donc « Petit-déjeuner » proposé, et les quatre pastilles de l'écran
     * sont là pour ça.
     */
    private fun moment(): MealMoment = MealMoment.at(LocalTime.ofInstant(clock.now(), clock.zone()))

    /** Un brouillon d'une seule ligne vide, daté du jour regardé. */
    operator fun invoke(source: EntrySource): EntryDraft = EntryDraft(
        date = date(),
        source = source,
        lines = listOf(line()),
        moment = moment(),
    )

    /**
     * Un brouillon d'une ligne, préremplie depuis une fiche d'aliment.
     *
     * Le pendant du précédent, et la démonstration que la promesse tenait : la
     * recherche produit le même genre de brouillon que la saisie manuelle, et rien
     * en aval ne les distingue. Seule la source diffère, et elle n'est qu'une
     * pastille.
     */
    operator fun invoke(source: EntrySource, food: Food): EntryDraft = EntryDraft(
        date = date(),
        source = source,
        lines = listOf(DraftLine.of(DraftLineId(ids.next()), food)),
        moment = moment(),
    )

    /**
     * Un brouillon de plusieurs lignes, ce que produit une reconnaissance.
     *
     * **Une liste vide donne quand même une ligne**, et c'est ici que l'invariant se
     * tient : un brouillon sans aucune ligne n'aurait rien à afficher ni à supprimer,
     * et l'écran de validation n'a pas d'état pour ça. Le cas ne devrait pas se
     * présenter — une analyse sans ligne exploitable est une erreur, pas une
     * réussite — mais le tenir ici coûte une expression et évite d'y compter.
     */
    operator fun invoke(source: EntrySource, lines: List<DraftLine>): EntryDraft = EntryDraft(
        date = date(),
        source = source,
        lines = lines.ifEmpty { listOf(line()) },
        moment = moment(),
    )

    /** Une ligne vierge de plus. Sert de repli quand aucune fiche n'est disponible. */
    fun line(): DraftLine = DraftLine.blank(DraftLineId(ids.next()))

    /** Une ligne de plus, préremplie depuis une fiche — ce que produit « Ajouter un aliment ». */
    fun line(food: Food): DraftLine = DraftLine.of(DraftLineId(ids.next()), food)
}
