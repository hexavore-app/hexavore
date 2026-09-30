package app.hexavore.di

import app.hexavore.domain.report.CrashReports
import app.hexavore.domain.report.ReportSender
import app.hexavore.domain.usecase.ReportAnalysis
import app.hexavore.domain.usecase.ReportCrash
import app.hexavore.integration.reports.FileCrashReports
import app.hexavore.integration.reports.MailReports
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Les deux rapports par courriel : un plantage, une proposition d'IA incorrecte.
 *
 * **Rien ne part tout seul** ([D138][decisions]) : ces adaptateurs préparent un
 * courriel et l'ouvrent dans l'application de messagerie. Ce qui part, part parce que
 * quelqu'un a appuyé sur « envoyer » en ayant lu ce qu'il envoyait — la contrainte
 * ferme de [01][perimetre] tient, et aucun serveur n'est né.
 *
 * [perimetre]: docs/01-perimetre.md
 * [decisions]: docs/11-decisions.md
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportModule {
    @Provides
    fun reportSender(mail: MailReports): ReportSender = mail

    @Provides
    fun crashReports(files: FileCrashReports): CrashReports = files

    @Provides
    fun reportCrash(crashes: CrashReports, sender: ReportSender): ReportCrash = ReportCrash(crashes, sender)

    @Provides
    fun reportAnalysis(sender: ReportSender): ReportAnalysis = ReportAnalysis(sender)
}
