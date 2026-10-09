package app.hexavore.di

import app.hexavore.domain.diary.DiaryRepository
import app.hexavore.domain.reminder.PostedReminders
import app.hexavore.domain.reminder.ReminderScheduler
import app.hexavore.domain.reminder.ReminderSettings
import app.hexavore.domain.time.Clock
import app.hexavore.domain.usecase.ChooseReminder
import app.hexavore.domain.usecase.ObserveReminders
import app.hexavore.domain.usecase.ShouldRemind
import app.hexavore.integration.reminders.NotificationLook
import app.hexavore.integration.reminders.NotificationTray
import app.hexavore.integration.reminders.WorkManagerReminders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.hexavore.core.designsystem.R as DesignSystem

/**
 * Les rappels : un port, un adaptateur, et les trois cas d'usage qui l'encadrent.
 *
 * **Choisir replanifie, et c'est le cas d'usage qui le tient** ([D134][decisions]).
 * Laisser l'écran écrire le réglage puis appeler le planificateur aurait donné deux
 * gestes à ne pas oublier ; le jour où un second écran règle un rappel, il en oublierait
 * un.
 *
 * [decisions]: docs/11-decisions.md
 */
@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {
    @Provides
    fun reminderScheduler(scheduler: WorkManagerReminders): ReminderScheduler = scheduler

    @Provides
    fun postedReminders(tray: NotificationTray): PostedReminders = tray

    /**
     * Le signe et la teinte des notifications.
     *
     * Ils vivent dans le design system, dont `:integration:reminders` ne depend pas :
     * c'est `:app` qui assemble les deux, comme pour le rejeu du tour (D152).
     */
    @Provides
    fun notificationLook(): NotificationLook = NotificationLook(
        icon = DesignSystem.drawable.ic_notification,
        color = DesignSystem.color.neon_notification,
    )

    @Provides
    fun observeReminders(settings: ReminderSettings): ObserveReminders = ObserveReminders(settings)

    @Provides
    fun chooseReminder(settings: ReminderSettings, scheduler: ReminderScheduler): ChooseReminder =
        ChooseReminder(settings, scheduler)

    @Provides
    fun shouldRemind(diary: DiaryRepository, settings: ReminderSettings, clock: Clock): ShouldRemind =
        ShouldRemind(diary, settings, clock)
}
