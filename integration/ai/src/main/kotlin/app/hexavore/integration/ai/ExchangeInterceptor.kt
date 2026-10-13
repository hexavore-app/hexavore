package app.hexavore.integration.ai

import app.hexavore.domain.ai.AiExchange
import app.hexavore.domain.ai.AiExchangeLog
import app.hexavore.domain.time.Clock
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * Ce qui est parti, ce qui est revenu — quand on a demandé à le voir.
 *
 * **Éteint, il ne coûte rien.** La première ligne rend la main : pas de lecture de
 * corps, pas de copie, pas d'allocation. C'est ce qui permet de le laisser dans la
 * chaîne en permanence plutôt que de reconstruire le client au changement de réglage.
 *
 * **Aucune clé n'y passe.** Les en-têtes ne sont pas enregistrés du tout — ni masqués,
 * ni filtrés : simplement absents. C'est plus sûr qu'une liste d'en-têtes secrets à
 * tenir à jour ([RedactionInterceptor] en tient une, et elle a déjà dû être complétée
 * d'avance pour les fournisseurs à venir). Ce qui intéresse la mise au point est le
 * corps, et un corps ne porte pas de clé.
 *
 * **Les images sont élidées.** Une photo voyage en base64 : quelques centaines de
 * milliers de caractères qui rempliraient la mémoire et noieraient le JSON qu'on
 * cherche à lire. Toute longue suite de caractères base64 est remplacée par sa
 * longueur.
 *
 * **Ce qui reste tient en entier**, et c'est ce qui a changé : la borne valait huit
 * mille caractères, c'est-à-dire moins qu'un seul tour d'analyse approfondie. Un
 * signalement partait donc avec une consigne coupée en son milieu, et sans rien dire de
 * ce qui manquait ([D155][decisions]).
 *
 * **Le corps de la réponse est lu par [Response.peekBody]**, qui en prend une copie
 * sans consommer le flux : le lire autrement le viderait, et l'appelant recevrait une
 * réponse vide — un défaut de journalisation qui casse ce qu'il observe.
 *
 * [decisions]: docs/11-decisions.md
 *
 * @see docs/05-ia.md § Sécurité des clés
 */
internal class ExchangeInterceptor(private val log: AiExchangeLog, private val clock: Clock) : Interceptor {
    /**
     * **Il note toujours.** Il sortait ici quand le mode de mise au point etait eteint,
     * si bien qu'un signalement ne joignait jamais l'echange qu'il promet de porter.
     * C'est le journal qui decide desormais de ce qu'il **garde** -- un echange, ou
     * vingt --, parce que c'est une question de retention et non d'interception (D153).
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sent = request.body?.let { body ->
            Buffer().also { runCatching { body.writeTo(it) } }.readUtf8()
        }.orEmpty()

        val response = chain.proceed(request)
        val received = runCatching { response.peekBody(PEEK_LIMIT).string() }.getOrElse { "(illisible)" }

        log.record(
            AiExchange(
                at = clock.now(),
                endpoint = request.url.newBuilder().query(null).build().toString(),
                status = response.code,
                request = sent.readable(),
                response = received.readable(),
            ),
        )
        return response
    }
}

/**
 * Celui qui ne retient rien.
 *
 * Le pendant de `NetworkLog.Silent`, et pour la même raison : un montage qui n'observe
 * pas doit le **dire**, plutôt que d'omettre un paramètre. Une valeur par défaut aurait
 * laissé passer un câblage oublié en production, où l'absence de journal ressemble
 * exactement à un mode debug qui ne s'allume pas.
 */
internal val SilentExchanges = Interceptor { chain -> chain.proceed(chain.request()) }

/**
 * Un corps lisible : sans les images, et borné.
 *
 * L'ordre compte. Élider d'abord, tronquer ensuite : l'inverse couperait au milieu du
 * base64 d'une photo et laisserait quatre mille caractères de bruit à la place du JSON
 * qu'on voulait voir.
 *
 * **La coupe se fait au milieu, et elle dit ce qu'elle emporte.** ~~Elle prenait les
 * premiers caractères et ajoutait « (tronqué) »~~ : la fin d'un corps est précisément
 * ce qu'on vient y chercher — les outils déclarés et la configuration pour une requête,
 * la raison d'arrêt et le compte de jetons pour une réponse. Les garder tous les deux
 * coûte une ligne ; dire combien de caractères manquent en coûte zéro, et évite de
 * confondre « il manque trois lignes » avec « il manque la moitié » ([D155][decisions]).
 *
 * [decisions]: docs/11-decisions.md
 */
private fun String.readable(): String {
    val elided = BASE64_RUN.replace(this) { "…${it.value.length} caractères élidés…" }
    if (elided.length <= BODY_LIMIT) return elided

    val coupes = elided.length - BODY_HEAD - BODY_TAIL
    return elided.take(BODY_HEAD) + "\n…($coupes caractères coupés ici)…\n" + elided.takeLast(BODY_TAIL)
}

/**
 * Une longue suite de caractères base64 : une image, et rien d'autre.
 *
 * Deux cents comme seuil : aucun libellé d'aliment, aucun identifiant de modèle,
 * aucune clé CIQUAL n'atteint cette longueur, et une vignette la dépasse de trois
 * ordres de grandeur.
 */
private val BASE64_RUN = Regex("[A-Za-z0-9+/]{200,}={0,2}")

/**
 * De quoi tenir un échange entier, mode approfondi compris.
 *
 * **Huit mille caractères ne suffisaient pas, et c'est un signalement qui l'a dit.**
 * Une analyse approfondie renvoie au fournisseur la conversation **complète** à chaque
 * tour : la consigne système, les outils déclarés, et tous les résultats de recherche
 * déjà rendus — six libellés valent une trentaine de fiches avec leurs six teneurs
 * chacune. Le corps dépasse la dizaine de milliers de caractères dès le deuxième tour,
 * et l'échange joint au signalement s'arrêtait au milieu de la consigne. Le rapport
 * promettait ce qu'il faut pour comprendre, et livrait un quart.
 *
 * Soixante-quatre mille, donc, et la mémoire ne s'en émeut pas : le journal ne garde
 * **un** échange tant que la mise au point est éteinte (`REPORTABLE_HISTORY`), vingt une
 * fois allumée — et les images sont élidées bien avant d'y entrer, ce qui est la seule
 * chose qui pesait vraiment.
 */
private const val BODY_LIMIT = 64_000

/**
 * Ce qu'on garde de la fin, quand il faut couper.
 *
 * Un quart : le début porte la conversation dans l'ordre où elle s'est tenue, et c'est
 * ce qu'on relit le plus. La fin ne porte qu'une poignée de champs — mais ceux qui
 * disent **comment ça s'est terminé**, et aucun autre endroit ne les porte.
 */
private const val BODY_TAIL = BODY_LIMIT / 4

private const val BODY_HEAD = BODY_LIMIT - BODY_TAIL

/**
 * Ce qu'on copie d'une réponse avant de la borner.
 *
 * **Assez large pour que la coupe nommée soit la seule.** `peekBody` compte en octets
 * et coupe en silence : une réponse rognée là arriverait dans le journal sans aucune
 * marque — le même défaut que [readable] vient de corriger, en pire, puisque rien ne le
 * dirait. Trois octets par `Char`, c'est le pire cas d'UTF-8 ; au-delà, un caractère
 * occupe deux `Char` et la borne tient toujours.
 */
private const val PEEK_LIMIT = 3L * BODY_LIMIT
