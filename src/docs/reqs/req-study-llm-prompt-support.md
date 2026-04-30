# Etude de faisabilité

Proposer une étude de faisabilité et une estimation du coût (temps/resiource)  pour l'implémentation de la feature décrite ci-dessus, dans un document au format markdown dans `src/docs/llm-support-design-study.md`

> [!WARNING] Les diagrammes de classes et de sequences seront générés en utilisant plantuml inline (dans le document lui-même avec "```plantuml" )

> [!WARNING] Des illustrations de l'interface seront proposées en images SVG, ces images SVG seront générées dans  `src/docs/illustrations/.`

## LLM Prompt Support

Afin de pouvoir generer des notes plus rapidement, je souhaite intégrer un nouveau panel `PromptPanel`. Celui permettra l'intégration d'un prompt AI en se connectant à une instance de LLM via une API obéissant au standard MCP.

### Prompt et Session

Ce `PromptPanel` fera la hauteur de la feneêre principale et sera docké par défaut à gauche.

- 2/3 de la hateur pour l'oghistorique de conversation dans la session en cour,
- 1/3 pour le prompt.

Chacune des zones comporte un bandeau en haut contenant le titre de la zone sur la gauche et une mini toolbar sur la droit.

#### Zone Prompt

La zone de prompt permet la saisie d'un texte. un bouton situé en dessous permet dse sousmettre le prompt à l'instance, ce même bouton permet d'interrompre le processing du prompt  si celui est
 en cours.

Dans la mini toolbar du prompt, un bouton permet d'ouvrir un dialogue pour la définition d'un context (en fait un texte en markdwon permettant de décrire un context qui sera systématiquement ajouter en amont du prompt sassie par l'utilisateur).

#### Zone Historique

Il est possible:

- d'éditer un prompt précédent dans la zone de prompt et ainsi relancer la génération d'une réponse,
- d'exporter 1 reponse vers un fichier markdown via un bouton d'export situé sur le bloc de celle-ci,
- d'exporter l'ensemble de la session via un bouton situé en haut, dans la mini toolbar de la zone d'historique.

### Configuration

Ainsi seront fournis via l'écran de confiugration dans l'onglet "LLM", les attributs necessaire à une connexion à une instance: URL, username, token, etc...
