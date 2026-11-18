# SmartPlay

Plugin Spigot qui transforme une partie de Minecraft en séance de révision. Une question apparaît dans une enclume : le joueur saisit sa réponse et clique sur le résultat. Pendant la question, ses déplacements, interactions et dégâts sont bloqués et les créatures proches sont mises en pause.

## Installation

1. Utiliser un serveur **Spigot 1.19.4 avec Java 17**. Cette version conserve l'API et AnvilGUI du projet initial ; les versions Minecraft plus récentes et Folia ne sont pas prises en charge sans adaptation et test.
2. Compiler avec `./mvnw clean verify` (Linux/macOS) ou `.\mvnw.cmd clean verify` (PowerShell). Le wrapper télécharge Maven au premier lancement ; un JDK et un accès Internet sont nécessaires.
3. Copier **`target/SmartPlay-1.1.0.jar`** dans `plugins/`, puis redémarrer le serveur. Ne pas utiliser le fichier `original-*`.
4. Modifier `plugins/SmartPlay/questions.json` et `config.yml`, puis lancer `/sp reload` depuis le jeu ou la console.
5. Tester une question avec `/sp ask`.

Trois exemples sont installés au premier démarrage. Les fichiers existants ne sont jamais remplacés automatiquement.

**Par défaut, une erreur, la fermeture de l'enclume ou le dépassement du délai tue le joueur.** Pour un mode sans pénalité mortelle, définir `kill-on-wrong-answer: false` dans `config.yml`, puis `/sp reload`.

## Commandes et permissions

| Commande | Fonction | Permission par défaut |
| --- | --- | --- |
| `/sp` ou `/smartplay help` | Afficher l'aide | Tous |
| `/sp ask` ou `/askquestion` | Lancer un compte à rebours personnel | `smartplay.play` : tous |
| `/sp stats` | Consulter les bonnes réponses, erreurs et questions à revoir | `smartplay.play` : tous |
| `/sp reload` ou `/smreload` | Recharger les questions et la configuration | `smartplay.reload` : opérateurs |
| `/sp start` | Activer le cycle automatique | `smartplay.admin` : opérateurs |
| `/sp stop` | Arrêter le cycle et libérer toutes les sessions | `smartplay.admin` : opérateurs |
| `/sp status` | Afficher l'état, les questions et les sessions | `smartplay.admin` : opérateurs |

Les commandes d'administration fonctionnent aussi depuis la console. L'autocomplétion respecte les permissions. `smartplay.bypass`, désactivée par défaut même pour les opérateurs, exclut un joueur des questions manuelles et automatiques. Les joueurs morts, hors ligne ou en mode spectateur sont exclus.

`/sp stop` laisse les questions manuelles disponibles. `/sp start` et `/sp stop` agissent jusqu'au prochain rechargement ou redémarrage ; `automatic.enabled` définit le comportement permanent.

## Questions

Enregistrer le fichier en **UTF-8**, avec une liste JSON non vide :

```json
[
  {
    "question": "Quelle est la capitale de la France ?",
    "answer": "Paris"
  },
  {
    "question": "Quel gaz les plantes absorbent-elles pendant la photosynthèse ?",
    "answer": "dioxyde de carbone",
    "answers": ["CO2", "CO₂", "le dioxyde de carbone"]
  }
]
```

- `answer` reste compatible avec les anciens fichiers ; `answers` ajoute des formulations acceptées et peut aussi être utilisé seul.
- La première réponse est affichée lors de la correction. Fournir des réponses courtes adaptées au champ de renommage de Minecraft.
- La comparaison porte sur **la réponse entière** : saisir un mot présent dans la correction ne suffit plus.
- La casse, les espaces répétés et les espaces en début/fin sont ignorés. Les accents sont facultatifs si `ignore-accents: true`. La ponctuation reste significative : ajouter des variantes dans `answers` si nécessaire.
- Une question vide, une réponse vide, un type incorrect ou une question en double entraîne le refus du fichier entier. Les anciennes questions restent utilisables ; la console indique l'erreur.
- Les sessions déjà ouvertes conservent leur question au rechargement.

Une question ne se répète pas immédiatement s'il existe une alternative. Lorsqu'un joueur a des erreurs à revoir parmi les questions disponibles, la sélection pioche une fois sur deux dans ce groupe. Une bonne réponse retire la question des révisions. **Statistiques et révisions sont propres à la connexion en cours** et sont effacées à la déconnexion ou au redémarrage.

## Configuration

| Clé | Défaut | Limites / effet |
| --- | --- | --- |
| `automatic.enabled` | `true` | Cycle automatique au démarrage et au rechargement |
| `automatic.interval-seconds` | `300` | 10 à 86 400 s ; première vague après cet intervalle |
| `countdown-seconds` | `10` | 0 à 300 s |
| `answer-timeout-seconds` | `60` | 0 désactive la limite ; maximum 3 600 s |
| `recovery-seconds` | `3` | 0 à 30 s de protection après réponse sans mort |
| `freeze-radius` | `10` | 0 à 64 blocs dans chaque axe ; 0 désactive le gel |
| `ignore-accents` | `true` | Tolérer les accents manquants |
| `kill-on-wrong-answer` | `true` | Appliquer la pénalité mortelle |
| `messages.*` | Français | Textes, titre, préfixe et couleurs `&` personnalisables |

Les valeurs numériques hors limites sont bornées. Au rechargement, un YAML invalide ou des types incorrects pour les réglages numériques/booléens sont refusés en conservant les anciennes données. Les créatures déjà dépourvues d'IA gardent cet état après la question. Si plusieurs joueurs partagent une créature, son IA reste suspendue jusqu'à la fin de toutes les sessions concernées. Le gel concerne les créatures présentes à l'ouverture ; il ne suspend pas le monde entier.

Une déconnexion, une mort, une téléportation, un changement de monde ou de mode de jeu annule proprement la session. L'arrêt du plugin restaure les créatures et annule ses tâches. Aucun appel Bukkit n'est effectué depuis un traitement asynchrone.

## Développement et validation

```powershell
.\mvnw.cmd clean verify
```

Le build compile, exécute les tests JUnit et produit un JAR autonome avec Gson et AnvilGUI isolés pour limiter les conflits entre plugins. Les tests couvrent la validation des données, les variantes et la normalisation des réponses, ainsi que la restauration de l'IA et les gels partagés.

Avant une utilisation sur un serveur public, vérifier en jeu : bonne et mauvaise réponse, fermeture de l'enclume, expiration, déconnexion pendant le compte à rebours, deux joueurs autour du même mob, mob initialement sans IA, `/sp stop`, rechargement d'un JSON invalide et arrêt du serveur pendant une question. Les tests automatisés ne remplacent pas cette vérification de l'enclume native de Minecraft.
