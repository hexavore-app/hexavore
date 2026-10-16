package app.hexavore.feature.weight

import app.hexavore.domain.usecase.ObserveHeight
import app.hexavore.domain.usecase.ObserveUnitSystem
import javax.inject.Inject

/**
 * Ce que le journal lit du profil : les unités pour afficher, la taille pour juger une
 * pesée avant de l'écrire ([D160][decisions]).
 *
 * **Réunis en un porteur**, comme [TrendDisplay] : le constructeur du modèle d'écran
 * atteignait le seuil de paramètres, et la réponse du projet est de grouper ce qui va
 * ensemble plutôt que de relever le seuil ([docs/10][qualite]). Ces deux-là vont
 * ensemble : ce sont deux lectures du même profil, chacune avec son repli quand il
 * manque.
 *
 * [decisions]: docs/11-decisions.md
 * [qualite]: docs/10-qualite-et-livraison.md
 */
internal class ProfileReadings @Inject constructor(val units: ObserveUnitSystem, val height: ObserveHeight)
