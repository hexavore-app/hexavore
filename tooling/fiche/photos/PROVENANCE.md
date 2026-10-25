# D'où viennent ces trois photos

Trois images libres de droit, fournies par l'auteur. La première est de Pixabay,
sous [Content License](https://pixabay.com/service/license-summary/), qui autorise
l'usage commercial sans attribution et la redistribution à l'intérieur d'une
œuvre ; les deux autres ont été données comme libres de droit avec elle. Elles ne
sont pas redistribuées seules : elles servent à garnir les captures d'écran de la
fiche Play Store, où elles apparaissent comme la photo qu'un utilisateur aurait
prise de son repas.

| Fichier | Ce qu'on y voit | Repas auquel elle est attachée |
|---|---|---|
| `petit-dejeuner.jpg` | Un œuf au plat sur une tranche de pain de mie, rondelles de tomate | Petit-déjeuner du jour visible |
| `burger.jpg` | Un burger au poulet et au fromage, frites, salade verte et tomate | Déjeuner du jour visible |
| `steak.jpg` | Un steak grillé, jeunes pousses, tomates cerises, un trait de sauce | Dîner du jour visible |

**Les trois repas ont été écrits pour correspondre à ce qu'on voit.** Une photo
de steak sur un plat qui liste du saumon serait un mensonge dans la vitrine, et
la première personne à comparer l'image et la liste le verrait. Les aliments et
les quantités de `demo_data.py` décrivent donc ces trois assiettes-là.

**Le burger et le steak avaient été écartés en septembre**, comme trop proches
de photos de studio pour passer pour celle qu'on prend soi-même au-dessus de son
assiette. L'auteur les a retenus le 17 octobre 2026 pour les captures de la
1.0.0 : la journée visible y gagne un dîner, et trois des quatre repas montrent
leur photo. L'ancienne photo du déjeuner, un croque sur du chou, est retirée.

**Cinq aliments fibreux sur la journée, et pas six.** La bulle de la capture 2
ne tient entière qu'à cinq lignes sur un écran 16:9. Les tomates cerises du
dîner sont donc notées « Tomate », la même fiche que celles du petit-déjeuner,
et ses jeunes pousses « Salade verte », celle du midi ; les amandes ont quitté
le goûter. Le trait de sauce du steak n'est pas noté : CIQUAL n'a pas de jus de
viande, et la sauce la plus proche ajoutait une sixième ligne. Un filet d'huile
d'olive tient lieu d'assaisonnement de la salade. `demo_data.py` compte les
contributeurs aux fibres à chaque génération et le dit.

**CIQUAL n'a pas d'œuf au plat** : ni frit, ni poêlé. Le petit-déjeuner note
donc « Œuf poché » et l'huile de cuisson à côté, ce qu'une personne qui suit son
alimentation fait tous les jours quand la préparation exacte manque à la table.
