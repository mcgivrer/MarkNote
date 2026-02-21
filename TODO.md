# MarkNote - TODO

>2026-02-19

- [x] Refactorer la classe principale en multiples classes pour separer les panel, la ui et les traitements des fichiers.
- [x] Ajout du Drag&Drop dans le navigateur de fichier,
- [x] redispatch des JAR dans libs a trier par os / commonm.
- [x] Ajouter une action package dans le script de build, cette action creera un zip dans target partir de target/build, le package doit aussi contenir le script de lancement et un JRE minimal pour le projet.
- [x] Ajout du Drag&Drop de fichier depuis l'exterieur vers le l'exorateur de fichier pour copier les fichiers vers le repertoire de destination desire (Cible du drop). Une confirmation est demandee avant la copie. si un seul fichier le message presente le nom du fichier, si plusieurs fichiers, on affiche le nombre de fichiers.
- [x] Ajouter la previsualisation d'image : si un fichier image est ouvert depuis l'explorateur de projet, un onglet "Preview {nom_fichier_image}" est ouvert montrant l'image cadree dans l'onglet. Dans l'onglet `ImagePreviewTab`, la molette de la souris doit permettre de zoomer/dezoomer, et le clic sur la molette+move souris per;et de se deplacer la vue dans l'image si c'est possible. des ascensceurs horizontal et vertical sont ajoutes si necessaire. un text em surimpression est ajouter precisant le niveau de zoom pendant les actions de zoom et un texte dans un bandau en haut precise la taille de l'image (en pixels) et son type.
- [x] Ajouter un onglet Welcome présentant la liste des derniers projets ouverts. (Options Misc. -> Display the "Welcome" page)  the number of proposed project il the list is defined by the number of item in menu "last project(s)..." parameter in Options.
- [x] Ajouter une syntax Highlighter,
- [x] Ajouter la notion de theme pour toute l'application et fournir un set de themes par defaut inspires de gnome-edit avec la gestion des themes dans les options sous un nouvel onglet "Themes". Une liste de theme est affiches, et il est possible d'en creer de nouveau : le theme de base est copie et ouvert en tant que document CSS (avec coloration syntaxique). les themes customs seront sauves dans un repertoir ~/.marknote/themes. Biensur, tous les themes apparaiteront dans la liste.
- [x] Ajouter un SpashScreen avec Nom de l'application, version, auteur, contact et copyright ( command dans "about")

>2026-02-20

## Support external markdown extension

- [x] Ajouter le support de plantuml et de mermaid,
- [x] Ajouter le support de MathML,
- [x] Ajouter dans le support du markdown une specificité pour les images:
dans  `![alt text](./pic/pic1_50.png "title" =100x20)`
le `=100x20` permet de fixer les attribtus Width et Height de la balise image. il possible de definir unitquement `=200x` ou même `=x120` pour définir uniquement width ou height.

## Front Matter

- [x] Ajouter un editeur de "Front Matter" pour les docs Markdown (voir plus bas).

Ajouter le support des attributes de "Front Matter" dans le fichier markdown, ainsi on peu ajouter des attributs:
- `title` pour le titre de l'article (il devra être utilisé dans l'explorateur de projet si celui-ci existe)
- `author` un auteur ou une liste d'auteurs
- `created_at` la date de création de la note au `formaat YYYY-MM-DD (hh:mm)`   l'heure étant optionnelle,
- `tags` une liste de tag `[tag2,tag2,tag3]`
- `summary` un résumé de l'article/de la note (optionel)
- `draft` status de la note (si elle est publiée ou non: publiée si draft=false)

  > **IMPORTANT** Tous ces élément pourront être utilisé plus tard dans un moteur de recherche de note.

>2026-02-21

## UUID and links

- [x] Ajouter le support de lien vers d'autres document via "Front Matter" avec l'attribut `links` qui contient un seul ou une liste de lien vers d'autres documents; e.x: `links: [4567-6789-8903-6784]`

  > **NOTE1** Les liens pointent vers un UUID

  > **NOTE2** Cet uuid est généré à la création d'un document : c'est un nouvel attribute Front Matter" : `uuid`.

  > **NOTE3** Le lien sera créé par drag-and-drop depuis l'explorateur de projet vers la zone "Front Matter" en haut du DocumentTab cible. Si le champs uuid n'existe poas ldans le document à lier, il faut le créer et l'ajouter; et renseigner le lien dans le document cible.



## Indexing to local project database

- [x] Index all files (primarily by front matter attributes and filenames). The index is stored as a JSON file in the project's root folder and is hidden from the Project Explorer panel.
- [x] Add a search box at the top of the UI that queries the index and displays matching documents in a popup list, showing the matching excerpt below each document title. Selecting a result opens the document.
- [x] Add a Tag Cloud panel below the Project Explorer, where each tag's font size is proportional to its number of occurrences.
- [x] Allow resetting the index from a context menu entry on the root folder in the Project Explorer.
- [x] Add a status bar at the bottom of the main window with three sections:
  - **Document & position** — name and cursor position (line:column) of the current document,
  - **Statistics** — number of indexed documents, lines, and words in the current document,
  - **Indexing progress** — a progress bar showing advancement while the indexing service is running.
    > **IMPORTANT** The indexing service must run in a separate thread so as not to interrupt the user's note-taking experience.

## Add Visual Network Diagram 

- [x] Add a new Network Diagram in a new VisualLinkPanel which can, be dsiplayed in the same side as Tags. 
This link diagram is representing all the links betweek document in a project. each line is link, each node is a doc. use doc icon for nodes and black line for links.
  - if link is a link to a tag, add tag name as node on the corresponding line
  - if link is a simple link between docs, draw a simple line.
  - the diagram must try to keep all nodes distances equals in a dynamic way. 
  > **NOTE1** As IndexService, the visual Network diagram is dynamically updated.
  
  > **NOTE2** Use the same component to open/clkose panel like in "Front Matter".


## Optional

- [ ] Ajouter le support de template de pages
- [ ] Ajouter un panel "Assistant" permettant la connexion à un LLM via un MCP agent (ajouter un onlget "MCP agent" dans le dialogue "Options" pour configurer url, parameètres et clé d'API).
- [ ] Ajouter des themes pour le syntax highligther avec gestion dans les options. 
