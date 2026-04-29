#!/bin/bash

# Script post-installation pour copier les fichiers de bienvenue
# Ce script est exécuté après l'installation du package Debian

set -e

# Chemin vers le JAR installé (à ajuster selon l'emplacement d'installation)
INSTALL_DIR=/opt/MarkNote
JAR_FILE=$INSTALL_DIR/lib/MarkNote.jar

# Dossier de destination pour les fichiers de bienvenue
TARGET_DIR=$HOME/Documents/MarkNote Welcome

# Créer le dossier de destination s'il n'existe pas
mkdir -p "$TARGET_DIR"

# Extraire les fichiers de bienvenue du JAR
if [ -f "$JAR_FILE" ]; then
    echo "Extraction des fichiers de bienvenue..."
    unzip -q -o "$JAR_FILE" "welcome-project/*" -d "$TARGET_DIR"
    
    # Supprimer le dossier welcome-project temporaire
    if [ -d "$TARGET_DIR/welcome-project" ]; then
        mv "$TARGET_DIR/welcome-project"/* "$TARGET_DIR/"
        rmdir "$TARGET_DIR/welcome-project"
    fi
    
    echo "Fichiers de bienvenue installés dans $TARGET_DIR"
else
    echo "Erreur: JAR non trouvé à $JAR_FILE" >&2
    exit 1
fi

exit 0