package app.hexavore.data.backup.di

import android.content.Context
import app.hexavore.data.backup.InternalBackupTarget
import app.hexavore.data.backup.JsonSnapshotCodec
import app.hexavore.data.backup.RoomSnapshotStore
import app.hexavore.data.backup.ZipSnapshotArchive
import app.hexavore.domain.backup.BackupTarget
import app.hexavore.domain.backup.SnapshotArchive
import app.hexavore.domain.backup.SnapshotCodec
import app.hexavore.domain.backup.SnapshotStore
import app.hexavore.domain.concurrency.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * La cible où l'on écrit juste avant d'écraser.
 *
 * Un qualificatif et non un type distinct : c'est le **même** port, utilisé pour autre
 * chose. Le jour où Drive arrive, il se lie sans qualificatif et rien de ceci ne bouge.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SafetyCopy

/**
 * Ce que ce module lie : les trois ports de la sauvegarde.
 *
 * @see docs/09-donnees-et-sauvegarde.md
 */
@Module
@InstallIn(SingletonComponent::class)
internal object BackupModule {
    @Provides
    fun store(store: RoomSnapshotStore): SnapshotStore = store

    @Provides
    fun codec(codec: JsonSnapshotCodec): SnapshotCodec = codec

    /**
     * L'archive : le journal du codec, et les photos à côté.
     *
     * Elle emprunte le codec plutôt que d'écrire un second format, pour la raison
     * qui fait que ce module emprunte aussi les mappeurs : deux traductions de la
     * même chose finissent par diverger.
     */
    @Provides
    fun archive(archive: ZipSnapshotArchive): SnapshotArchive = archive

    /**
     * La copie de sécurité, dans le stockage interne.
     *
     * Elle n'a de sens que là : c'est le fichier qu'on veut retrouver **sur cet
     * appareil-ci** juste après avoir restauré le mauvais, et rien ne justifie de le
     * faire voyager.
     */
    @Provides
    @Singleton
    @SafetyCopy
    fun safetyCopy(@ApplicationContext context: Context, dispatchers: DispatcherProvider): BackupTarget =
        InternalBackupTarget(context = context, dispatchers = dispatchers, name = SAFETY_DIRECTORY)
}

private const val SAFETY_DIRECTORY = "backups"
