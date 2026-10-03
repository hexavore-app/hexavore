# Le tour d'écrans, sur toutes les versions d'Android

Une commande, et chaque version ouvre l'application depuis une installation
neuve : l'onboarding, puis les neuf écrans du tour, puis ce que le journal du
système a retenu.

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
tooling/revue-versions/toutes-les-versions.sh
```

Les rapports arrivent dans `build/revue-versions/`, un fichier par version, et
le tableau final dit en trois lignes ce que chacune a répondu.

## Pourquoi ça existe

Un appel de Java 9 compilait, passait toute l'analyse statique, et fermait
l'application sous Android 11 ([D139](../../docs/11-decisions.md)). `:domain`
est du Kotlin pur, donc `NewApi` ne le regarde pas ; la JVM de développement,
elle, connaît la méthode. **Rien ne pouvait le dire sans ouvrir l'écran sur la
version concernée** — et personne ne refait ça à la main sept fois.

## Ce que chaque passage fait

1. Éteint tout émulateur, **et attend qu'il soit vraiment parti**.
2. Démarre l'AVD demandé, et vérifie que c'est bien lui qui a démarré.
3. Fait taire les boîtes de dialogue du système et les animations.
4. **Désinstalle**, puis installe : chaque version voit une première
   installation, qui est ce qu'un utilisateur rencontre.
5. Lance `OnboardingJourneyTest`, puis `AppJourneyTest`.
6. Recopie le journal des plantages et les exceptions fatales.

## Les partis pris, et pourquoi

**L'onboarding d'abord, et dans sa propre classe.** Il n'existe que tant
qu'aucun objectif n'est écrit, et `AppJourneyTest` en écrit un avant chacun de
ses tests. L'ordre des tests JUnit ne se commande pas : les deux ne peuvent pas
cohabiter.

**On vérifie le nom de l'AVD qui a démarré.** Un émulateur met quelques secondes
à lâcher son port après `emu kill`. La version suivante s'y rebranchait alors
sans le savoir, et le rapport déclarait Android 11 vert sur une image Android 12
— une erreur qui ment dans le bon sens, donc la pire.

**On désinstalle entre deux versions**, et non entre deux tests : ce qu'on
cherche est une panne de version, pas une panne d'état.

**Rien n'est jugé de la mise en page ni des chiffres.** Une seule question est
posée, sur chaque version : *est-ce que ça s'ouvre*. Les valeurs sont tenues
ailleurs, par des tests qui n'ont pas besoin d'un téléphone.

## L'arrêter en cours de route

`Ctrl-C` sur le pilote **ne suffit pas** : le passage en cours est un processus
fils, et il continue — avec son émulateur — pendant que le suivant démarre. Deux
balayages se disputent alors le port 5554, et les rapports se mélangent sans que
rien ne le dise.

```bash
pkill -f revue-versions ; pkill -f qemu-system
```

## Ce qu'il faut avoir

Un AVD par version à couvrir, nommé `Hexavore_API<n>` — c'est ce que le script
ramasse quand on ne lui donne aucun argument. `ANDROID_HOME` sinon le chemin par
défaut du SDK. Les images se posent avec :

```bash
sdkmanager "system-images;android-30;google_apis;x86_64"
```
