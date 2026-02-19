# TODO

- [x] Refactorer la classe principale en multiples classes pour separer les panel, la ui et les traitements des fichiers.
- [x] Ajout du Drag&Drop dans le navigateur de fichier,
- [x] redispatch des JAR dans libs a trier par os / commonm.
- [x] Ajouter une action package dans le script de build, cette action creera un zip dans target partir de target/build, le package doit aussi contenir le script de lancement et un JRE minimal pour le projet.
- [x] Ajout du Drag&Drop de fichier depuis l'exterieur vers le l'exorateur de fichier pour copier les fichiers vers le repertoire de destination desire (Cible du drop). Une confirmation est demandee avant la copie. si un seul fichier le message presente le nom du fichier, si plusieurs fichiers, on affiche le nombre de fichiers.
- [ ] Ajouter la previsualisation d'image : si un fichier image est ouvert depuis l'explorateur de projet, un onglet "Preview {nom_fichier_image}" est ouvert montrant l'image cadree dans l'onglet. Dans l'onglet `ImagePreviewTab`, la molette de la souris doit permettre de zoomer/dezoomer, et le clic sur la molette+move souris per;et de se deplacer la vue dans l'image si c'est possible. des ascensceurs horizontal et vertical sont ajoutes si necessaire. un text em surimpression est ajouter precisant le niveau de zoom pendant les actions de zoom et un texte dans un bandau en haut precise la taille de l'image (en pixels) et son type.
- [ ] Ajouter une page Welcome présentant la liste des derniers projets ouverts. (Options Misc. -> Display the "Welcome" page)  the number of proposed project il the list is defined by the number of item in menu "last project(s)..." parameter in Options.
- [ ] Ajouter une syntax Highlighter,
- [ ] Ajouter le support de plantuml et de mermaid,
- [ ] Ajouter un editeur de "Front Matter" pour les docs Markdown.
- [ ] Ajouter un panel "Assistant" permettant la connexion à un LLM via un MCP agent (ajouter un onlget "MCP agent" dans le dialogue "Options" pour configurer url, parameètres et clé d'API).
- [ ] Ajouter la notion de the;e et fournir un set de themes par defaut inspires de gnome avec gestion dans les options.
- [ ] Ajouter des themes pour le syntax highlither avec gestion dans les options. 