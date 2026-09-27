package app.hexavore.core.common.di

import app.hexavore.core.common.ai.RecentExchanges
import app.hexavore.core.common.concurrency.DefaultDispatcherProvider
import app.hexavore.core.common.diary.CurrentSelectedDay
import app.hexavore.core.common.identity.UuidGenerator
import app.hexavore.core.common.language.SystemLocales
import app.hexavore.core.common.time.SystemClock
import app.hexavore.domain.ai.AiExchangeLog
import app.hexavore.domain.concurrency.DispatcherProvider
import app.hexavore.domain.diary.SelectedDay
import app.hexavore.domain.identity.IdGenerator
import app.hexavore.domain.language.SystemLanguages
import app.hexavore.domain.time.Clock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Liaison des ports de plateforme à leurs implémentations.
 *
 * Ce module vit dans `:core:common` et non dans `:app` : chaque module Gradle
 * expose ses propres liaisons, et `:app` ne fait qu'assembler. Un test qui veut
 * une horloge figée remplace ce module entier plutôt que d'aller chercher une
 * ligne au milieu du graphe applicatif.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun clock(implementation: SystemClock): Clock

    /**
     * Le jour regardé, en mémoire et sans stockage.
     *
     * Il n'appartient à aucun domaine de données : c'est un état d'application,
     * au même titre que l'horloge.
     */
    @Binds
    abstract fun selectedDay(implementation: CurrentSelectedDay): SelectedDay

    /**
     * Les derniers échanges avec un fournisseur d'IA, en mémoire.
     *
     * Même raison que le jour regardé : un état d'application sans rangement. Ici
     * l'absence de stockage n'est pas une commodité mais la décision — un journal de
     * mise au point qui survit à la session est un journal que personne n'efface.
     */
    @Binds
    @Singleton
    abstract fun exchangeLog(implementation: RecentExchanges): AiExchangeLog

    /**
     * Les langues du système, au même titre que l'horloge et le fuseau.
     *
     * Une lecture de plateforme sans règle : la règle — que faire d'une liste où
     * l'application ne reconnaît rien — vit dans le domaine et s'éprouve sur la JVM.
     */
    @Binds
    @Singleton
    abstract fun systemLanguages(implementation: SystemLocales): SystemLanguages

    @Binds
    @Singleton
    abstract fun dispatcherProvider(implementation: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun idGenerator(implementation: UuidGenerator): IdGenerator
}
