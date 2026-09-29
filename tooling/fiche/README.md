# La fiche Play Store, fabriquée et non capturée à la main

Huit captures par langue, montées sous leur phrase, plus l'icône et l'image de
présentation. Tout se refait d'une commande, et c'est le but : une fiche se
refait à chaque version qui change un écran, et personne ne reprend seize
captures à la main deux fois.

Les textes de la fiche sont dans [fiche-play-store.md](fiche-play-store.md).

## Ce que la chaîne fait

1. **`demo_data.py`** écrit deux archives de sauvegarde, une par langue, avec le
   journal d'un utilisateur fictif : sept semaines de repas, onze semaines de
   pesées, un objectif qui court, et les deux photos de `photos/` attachées aux
   repas qu'elles montrent. Il imprime les six compteurs de la journée visible :
   c'est le seul endroit où une erreur de portion se voit avant d'avoir refait
   tout un tir.
2. **`bootstrap_db.py`** écrit une base minimale, profil et objectif, qui sert à
   franchir l'onboarding.
3. **`shoot.py`** prépare l'émulateur, sème par l'import de sauvegarde, puis
   atteint et capture les huit écrans.
4. **`compose.py`** monte chaque capture sous sa phrase, et fabrique l'icône et
   l'image de présentation depuis les vecteurs du design system.

## Les partis pris, et pourquoi

**Semer par l'import de sauvegarde, pas en écrivant la base.** Le format du
fichier est documenté ([docs/09](../../docs/09-donnees-et-sauvegarde.md)) et tenu
par des tests ; les encodages de Room ne le sont que par les mappeurs. Passer par
l'import fait écrire la base par l'application elle-même : une capture ne peut
donc pas montrer un état que l'application n'aurait pas pu produire.

`bootstrap_db.py` est la seule exception, et elle est bornée à deux lignes :
l'application ouvre sur l'onboarding tant qu'aucun objectif ne court
([D56](../../docs/11-decisions.md)), et l'onboarding n'a pas de sortie vers les
réglages. Sans ce détour, il faudrait automatiser un sélecteur de date, qui
serait le maillon le plus fragile de toute la chaîne. Ces deux lignes sont de
toute façon écrasées par l'import qui suit.

**Une photo ne décore pas un plat, elle le décrit.** Deux repas de la journée
visible portent une vraie image ; leurs aliments et leurs quantités décrivent ce
qu'on y voit. Une photo de steak sur un plat qui liste du saumon serait un
mensonge dans la vitrine, et la première personne à comparer les deux le
verrait. Voir [photos/PROVENANCE.md](photos/PROVENANCE.md).

**Naviguer par l'arbre d'accessibilité, jamais par des coordonnées.** Chaque
geste vise un `content-desc` ou un texte. Une capture pilotée au pixel se casse
au premier changement de marge, et se casse en silence : elle rend une image,
simplement pas la bonne. Effet de bord voulu : **un tir qui échoue signale une
régression d'accessibilité**, puisqu'on ne peut pas photographier ce qu'un
lecteur d'écran ne sait pas nommer.

**Les chiffres sont vrais.** Les teneurs viennent du `ciqual.db` du dépôt, dans
les deux langues, jamais recopiées. Un chiffre faux sur une capture de fiche est
une promesse fausse.

**Le profil est fictif.** Les captures sont publiques.

**L'écran 16:9 est plus court qu'un téléphone.** La Console exige du 16:9 ou du
9:16 quand les appareils récents sont en 9:20 : l'AVD de tir est donc plus court
que n'importe quel téléphone sur lequel l'application tourne. C'est ce qui oblige
l'écran 2 à un recul calibré, et ce qu'il faut garder en tête avant de conclure
qu'un écran déborde.

## Préparer la machine

**Un AVD en 1080x1920.** Celui d'Android Studio est en 1080x2400, soit du 9:20,
plus allongé que ce que la Console accepte. Copier le `config.ini` d'un AVD
existant en changeant `hw.lcd.height = 1920`, `skin.name = 1080x1920`,
`PlayStore.enabled = false`, et retirer `hw.device.name` et `hw.device.hash2`
(la résolution ne correspond plus à aucun profil d'appareil).

**ImageMagick 7 avec librsvg**, pour convertir les vecteurs de l'icône.
`magick -list format | grep RSVG` doit répondre.

**Roboto**, que l'émulateur porte déjà :

```bash
adb exec-out cat /system/fonts/Roboto-Regular.ttf > /tmp/Roboto-Regular.ttf
```

C'est la police des captures elles-mêmes, et elle est sous licence Apache 2.0.

## Tirer

```bash
python tooling/fiche/demo_data.py --today "$(adb shell date +%Y-%m-%d | tr -d '\r')" --out /tmp/fiche/seed
adb push /tmp/fiche/seed/hexavore-demo-fr.zip /tmp/fiche/seed/hexavore-demo-en.zip /sdcard/Download/
python tooling/fiche/bootstrap_db.py --out /tmp/fiche/seed/hexavore.db
# puis, une fois la base minimale poussée et l'application lancée :
python tooling/fiche/shoot.py --language fr --work /tmp/fiche --reseed
python tooling/fiche/shoot.py --language en --work /tmp/fiche --reseed
python tooling/fiche/compose.py --work /tmp/fiche --font /tmp/Roboto-Regular.ttf
```

`--reseed` refait le semis par l'import et fige la base obtenue. Sans lui, la
base figée est reposée telle quelle : le sélecteur de documents sort de la
boucle, et un tir dure une minute au lieu de trois.

Le résultat atterrit dans `out/`, que le `.gitignore` du projet couvre déjà par
sa règle `out/`. Quinze mégaoctets de PNG n'ont rien à faire dans l'historique
quand la chaîne les reproduit à l'identique.

## Les pièges, vérifiés et non supposés

**Sous Git Bash, `MSYS_NO_PATHCONV=1`.** Sans lui, un chemin distant
`/data/local/tmp` part vers adb en `C:/Program Files/Git/data/local/tmp`, et le
`push` échoue sur un message qui parle du mauvais fichier. `driver.py` le pose
lui-même.

**ImageMagick avale les contre-obliques d'un `-font`.** `C:\Users\...` devient
`C:Users...`, il se plaint d'une police introuvable, **puis continue avec la
police par défaut sans échouer**. `compose.py` passe donc tous les chemins en
barres obliques. C'est le même piège que celui de `keystore.properties`.

**`exec-out` et non `shell` pour sortir un fichier binaire.** `shell` traduit les
fins de ligne : une base de 135 168 octets en ressort à 135 198, et SQLite répond
« database disk image is malformed » sans dire pourquoi.

**`run-as` ne peut pas écrire dans `/data/local/tmp`.** Il prend l'identité de
l'application, qui n'a pas ce droit. Lire se fait par
`adb exec-out run-as <paquet> cat databases/...`.

**Un `swipe` rapide est une lancée**, dont l'inertie dépend du moment où le
système échantillonne le geste : deux appels identiques ne rendent pas le même
décalage. Et au centre de l'écran, l'hexagone intercepte le geste. D'où `drag()`,
lent et près du bord gauche.

**Le fuseau de l'émulateur ne se change pas** sur une image
`google_apis_playstore` : `setprop persist.sys.timezone` est refusé. Les instants
des repas sont donc écrits pour GMT.

## Ce que le vert ne prouve pas

**Que les captures montrent ce qu'un utilisateur verra.** Elles sont prises sur
un écran plus court que les téléphones réels, en thème sombre, avec une barre
d'état en mode démonstration. Ce que l'application fait d'une barre d'état
chargée, d'un thème clair ou d'une grande police reste à regarder ailleurs.

**Que les huit écrans couvrent l'application.** Le scan, l'analyse par photo et
l'onboarding n'y sont pas : les deux premiers ont besoin d'une caméra réelle et
d'une clé d'IA, le troisième disparaît après la première ouverture.

**Qu'un écran soit bien composé.** La chaîne cadre ce qu'on lui demande de
cadrer. Trois défauts ont été trouvés en **regardant** les images, pas en les
fabriquant, et `./gradlew check` était vert avant comme après : restauration
impossible dès qu'un plat citait un favori, journal de poids qui annonçait
l'inverse de sa courbe, calories coupées par les boutons flottants. Ils sont
corrigés, et [D130](../../docs/11-decisions.md) raconte ce que cela dit de la
méthode.

**Qu'une capture montre quelque chose.** Un changement de langue recrée les
activités, et une capture prise pendant la recréation rend un fichier
parfaitement valide, simplement noir. C'est arrivé, et rien ne l'avait signalé.
Chaque déclenchement attend depuis une étiquette que l'écran doit déclarer
(`expect` dans `flows.py`) ; un seuil de pixels n'aurait pas convenu, un fond
presque noir étant légitime ici.
