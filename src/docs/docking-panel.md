# Docking Panel mechanism

Lors du deplacement des `BasePanel`, les positions et état (côté docké ou pas) doivent être sauvegardés en quittant le logiciel et  rechargés au démarrage depuis le fichier de configuration.

> [!NOTE] 
> Le menu "Affichage" doit également tenir compte des états sauvés.

> [!WARNING] 
> Si aucun état n'est sauvé, par défaut les `BasePanel` sont dockés à Gauche, sauf le `PromptPanel` docké à droite, et `ConsolePanel` docké en bas.
