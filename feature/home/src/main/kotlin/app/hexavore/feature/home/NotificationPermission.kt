package app.hexavore.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.hexavore.domain.reminder.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * La permission de notifier, demandée **une fois**, à la première ouverture.
 *
 * ### Pourquoi l'accueil et pas seulement l'onboarding
 *
 * L'onboarding la demande déjà, au dernier geste des cinq questions ([D134][decisions]).
 * Mais il ne se rejoue pas : quelqu'un qui utilisait Hexavore **avant** que les rappels
 * existent ne le reverra jamais, et ses quatre rappels resteraient donc muets sans que
 * rien ne le dise ([D135][decisions]).
 *
 * L'accueil rattrape ce cas, et lui seul : une fois la demande faite — ici ou dans
 * l'onboarding — elle ne se repose plus.
 *
 * ### Ce qu'elle ne fait pas
 *
 * **Elle ne bloque rien et n'explique rien.** Une boîte d'explication avant la boîte du
 * système ferait deux questions pour une, à l'ouverture, chez quelqu'un qui venait
 * regarder sa journée. Le refus n'a aucune conséquence visible : les réglages le disent
 * quand on y passe, et c'est là que la question se repose vraiment.
 *
 * En dessous d'Android 13, il n'y a rien à demander — mais la demande est quand même
 * **notée comme faite**, pour qu'une mise à jour du téléphone ne la fasse pas surgir un
 * beau matin sur une application installée depuis deux ans.
 *
 * [decisions]: docs/11-decisions.md
 */
@Composable
internal fun FirstRunNotificationRequest(viewModel: NotificationPermissionViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        viewModel.onAsked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Sait si la question reste à poser.
 *
 * `false` au départ plutôt qu'un état « on ne sait pas encore » : la lecture prend
 * quelques millisecondes, et une boîte système qui surgit après coup est moins gênante
 * qu'un écran qui attend une préférence pour s'afficher.
 */
@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(private val settings: ReminderSettings) : ViewModel() {
    private val toAsk = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = toAsk.asStateFlow()

    init {
        viewModelScope.launch { toAsk.value = !settings.permissionAsked() }
    }

    /**
     * Notée **avant** d'ouvrir la boîte, et non après la réponse.
     *
     * Quelqu'un qui balaie la boîte sans répondre n'a rien accordé, mais la question lui
     * a bien été posée : la reposer au lancement suivant serait la reposer à chaque
     * lancement.
     */
    fun onAsked() {
        toAsk.value = false
        viewModelScope.launch { settings.markPermissionAsked() }
    }
}
