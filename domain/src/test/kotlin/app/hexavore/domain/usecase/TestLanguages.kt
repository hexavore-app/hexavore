package app.hexavore.domain.usecase

import app.hexavore.domain.language.ContentLanguage
import app.hexavore.domain.language.ContentLanguages

/**
 * La langue des décors de ce module : le français.
 *
 * **Ecrite une fois et nommee**, plutot que passee en litteral a chaque construction. Les
 * libelles de ces cas sont ceux de la table de l'ANSES en francais -- « Pomme, chair et
 * peau, crue », « des haricots verts » -- et une langue anglaise ici ferait retirer « the »
 * au lieu de « des », donc rendrait des requetes qui ne trouvent rien. Le rapport entre la
 * langue du decor et ce que les cas affirment est tout sauf incidentel.
 *
 * Une lambda et non un faux de `:core:testing` : ce module ne peut pas en dependre, et
 * `ContentLanguages` est une interface fonctionnelle faite pour ca.
 */
internal val FRANCAIS = ContentLanguages { ContentLanguage.FRENCH }
