# D'où viennent ces deux photos

Deux images de Pixabay, sous [Content License](https://pixabay.com/service/license-summary/),
qui autorise l'usage commercial sans attribution et la redistribution à
l'intérieur d'une œuvre. Elles ne sont pas redistribuées seules : elles servent à
garnir les captures d'écran de la fiche Play Store, où elles apparaissent comme
la photo qu'un utilisateur aurait prise de son repas.

| Fichier | Ce qu'on y voit | Repas auquel elle est attachée |
|---|---|---|
| `petit-dejeuner.jpg` | Un œuf au plat sur une tranche de pain de mie, rondelles de tomate | Petit-déjeuner du jour visible |
| `dejeuner.jpg` | Un croque toasté coupé en deux, sur du chou blanc émincé | Déjeuner du jour visible |

**Les deux repas ont été réécrits pour correspondre à ce qu'on voit.** Une photo
de steak sur un plat qui liste du saumon serait un mensonge dans la vitrine, et
la première personne à comparer l'image et la liste le verrait. Les aliments et
les quantités de `demo_data.py` décrivent donc ces deux assiettes-là.

**Deux autres images ont été écartées** : une assiette de restaurant sur fond
noir et un burger avec frites. Elles sont plus belles, et c'est le problème :
elles ressemblent à des photos de studio, pas à ce qu'on prend soi-même
au-dessus de son assiette. La fonctionnalité que la capture doit montrer est
« gardez la photo que vous venez de prendre », pas « voici une photo de
catalogue ».

**Le chou est noté nature, sans sauce.** Une mayonnaise avait d'abord été
ajoutée au déjeuner, puis retirée : elle apportait 0,05 g de fibres, ce qui
suffisait à faire une sixième ligne dans la bulle de la capture 2 et à la faire
déborder d'un écran 16:9. Le chou de la photo n'a pas l'air d'une coleslaw
crémeuse, donc le retrait rapproche la note de l'image autant qu'il arrange le
cadrage. `demo_data.py` compte désormais les contributeurs aux fibres à chaque
génération et le dit.

**CIQUAL n'a pas d'œuf au plat** : ni frit, ni poêlé. Le petit-déjeuner note
donc « Œuf poché » et l'huile de cuisson à côté, ce qu'une personne qui suit son
alimentation fait tous les jours quand la préparation exacte manque à la table.
