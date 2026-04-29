@echo off

:: Script post-installation pour Windows
:: Copie les fichiers de bienvenue dans le dossier Documents de l'utilisateur

setlocal enabledelayedexpansion

:: Chemin vers le JAR installé (à ajuster selon l'emplacement d'installation)
set "INSTALL_DIR=C:\Program Files\MarkNote"
set "JAR_FILE=%INSTALL_DIR%\lib\MarkNote.jar"

:: Dossier de destination pour les fichiers de bienvenue
set "TARGET_DIR=%USERPROFILE%\Documents\MarkNote Welcome"

:: Créer le dossier de destination s'il n'existe pas
if not exist "!TARGET_DIR!" (
    mkdir "!TARGET_DIR!"
)

:: Vérifier si le JAR existe
if exist "!JAR_FILE!" (
    echo Extraction des fichiers de bienvenue...
    
    :: Extraire les fichiers de bienvenue du JAR
    jar -xf "!JAR_FILE!" "welcome-project/" -C "!TARGET_DIR!"
    
    :: Déplacer les fichiers du sous-dossier welcome-project
    if exist "!TARGET_DIR!\welcome-project" (
        move /Y "!TARGET_DIR!\welcome-project\*" "!TARGET_DIR!\" >nul
        rmdir "!TARGET_DIR!\welcome-project"
    )
    
    echo Fichiers de bienvenue installés dans !TARGET_DIR!
) else (
    echo Erreur: JAR non trouvé à !JAR_FILE!
    exit /b 1
)

endlocal
exit /b 0