# MarkNote — Packaging & Installation

> **Version** : 0.0.6  
> **Date** : 2026-02-23  
> **Auteur** : Frédéric Delorme

---

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Prérequis](#prérequis)
3. [Commande de packaging](#commande-de-packaging)
4. [Contenu du package](#contenu-du-package)
5. [Installeurs par OS](#installeurs-par-os)
   - [Linux](#linux)
   - [Windows](#windows)
   - [macOS](#macos)
6. [Désinstallation](#désinstallation)
7. [Arborescence du package](#arborescence-du-package)

---

## Vue d'ensemble

Le script `build` fournit une action `package` qui génère une archive ZIP autonome contenant :

- Le JAR de l'application (`MarkNote-<version>.jar`)
- Les bibliothèques JavaFX et dépendances (`libs/`)
- Un JRE minimal embarqué généré via `jlink` (`jre/`)
- Un script de lancement adapté à l'OS (`MarkNote.sh` ou `MarkNote.bat`)
- Les icônes de l'application en plusieurs formats (`icons/`)
- Un **installeur spécifique à l'OS** (`install.sh` ou `install.bat`)

L'archive est nommée selon le pattern : `MarkNote-<version>-<os>.zip` (ex : `MarkNote-0.0.6-linux.zip`).

> **Note** : Le packaging détecte automatiquement l'OS courant (`linux`, `win`, `mac`) et génère le package correspondant. Les bibliothèques JavaFX natives incluses sont celles de la plateforme de compilation.

---

## Prérequis

| Outil    | Version | Usage                                   |
|----------|---------|-----------------------------------------|
| `bash`   | 4+      | Exécution du script de build            |
| `java`   | 25      | Compilation et exécution                |
| `jlink`  | 25      | Génération du JRE minimal               |
| `zip`    | —       | Création de l'archive de distribution   |
| `git`    | —       | Récupération du commit ID pour le build |

---

## Commande de packaging

```bash
./build package
```

Cette commande effectue d'abord la compilation complète du projet, puis enchaîne les étapes de packaging :

1. **Compilation** — Compile les sources Java avec les modules JavaFX
2. **Création du JAR** — Génère `MarkNote-<version>.jar` avec le manifeste
3. **Distribution** — Prépare le répertoire `target/dist/`
4. **JRE minimal** — `jlink` crée un JRE allégé (~40 Mo) avec uniquement les modules nécessaires :
   - `java.base`, `java.desktop`, `java.logging`, `java.xml`
   - `java.net.http`, `java.scripting`, `jdk.xml.dom`
   - `java.sql`, `java.naming`, `java.prefs`
   - `jdk.unsupported`, `jdk.jsobject`
5. **Launcher** — Génère le script de lancement utilisant le JRE embarqué
6. **Icônes** — Copie les icônes SVG et PNG (16, 32, 64, 128 px)
7. **Installeur** — Génère le script d'installation spécifique à l'OS
8. **Archive ZIP** — Crée le package final dans `target/`

Le package résultant se trouve dans :

```
target/MarkNote-<version>-<os>.zip
```

---

## Contenu du package

Après décompression du ZIP, le répertoire contient :

| Fichier / Dossier              | Description                                    |
|-------------------------------|------------------------------------------------|
| `MarkNote-<version>.jar`     | Application principale                          |
| `libs/`                       | Bibliothèques JavaFX et dépendances (flexmark…) |
| `jre/`                        | JRE minimal embarqué (pas besoin de Java installé) |
| `MarkNote.sh` ou `.bat`      | Script de lancement                              |
| `icons/`                      | Icônes de l'application (SVG + PNG multi-tailles) |
| `install.sh` ou `install.bat`| Installeur spécifique à l'OS                     |

---

## Installeurs par OS

### Linux

**Fichier** : `install.sh`

#### Installation

```bash
unzip MarkNote-0.0.6-linux.zip
cd MarkNote-0.0.6-linux/
./install.sh
```

#### Ce que fait l'installeur

| Action | Détail |
|--------|--------|
| **Copie de l'application** | `~/.local/share/MarkNote/` |
| **Lien symbolique** | `~/.local/bin/marknote` → script de lancement |
| **Icônes système** | Installées dans `~/.local/share/icons/hicolor/` aux tailles 16, 32, 64, 128 px (PNG) et scalable (SVG) |
| **Entrée applications** | Fichier `.desktop` dans `~/.local/share/applications/` |
| **Raccourci bureau** | Copie du `.desktop` sur `~/Desktop/` avec marquage "trusted" (GNOME) |
| **Cache icônes** | Mise à jour via `gtk-update-icon-cache` si disponible |
| **Base desktop** | Mise à jour via `update-desktop-database` si disponible |

#### Fichier `.desktop` généré

```ini
[Desktop Entry]
Type=Application
Name=MarkNote
Comment=Markdown Note Editor
Exec=~/.local/share/MarkNote/MarkNote.sh %F
Icon=~/.local/share/MarkNote/icons/marknote.svg
Terminal=false
Categories=Utility;TextEditor;
MimeType=text/markdown;text/x-markdown;text/plain;
StartupWMClass=MarkNote
```

#### Lancement après installation

```bash
# Via le raccourci bureau ou le menu Applications
# Ou en ligne de commande (si ~/.local/bin est dans le PATH) :
marknote
```

> **Note** : Aucun droit administrateur n'est requis. Tout est installé dans le répertoire utilisateur.

---

### Windows

**Fichier** : `install.bat`

#### Installation

1. Décompresser `MarkNote-0.0.6-win.zip`
2. Ouvrir le dossier extrait
3. Double-cliquer sur `install.bat`

#### Ce que fait l'installeur

| Action | Détail |
|--------|--------|
| **Copie de l'application** | `%LOCALAPPDATA%\MarkNote\` (typiquement `C:\Users\<user>\AppData\Local\MarkNote\`) |
| **Raccourci Bureau** | Fichier `.lnk` créé via PowerShell (`WScript.Shell`) avec l'icône `marknote-128.png` |
| **Raccourci Menu Démarrer** | Fichier `.lnk` dans `%APPDATA%\Microsoft\Windows\Start Menu\Programs\` |

#### Détails techniques

- L'installation utilise `%LOCALAPPDATA%` : **aucun droit administrateur** n'est nécessaire
- Les raccourcis sont créés via PowerShell et l'objet COM `WScript.Shell`
- L'icône utilisée est `marknote-128.png` (format PNG supporté par Windows pour les raccourcis)

#### Lancement après installation

- Double-cliquer sur le raccourci **MarkNote** sur le Bureau
- Ou depuis le Menu Démarrer → **MarkNote**

---

### macOS

**Fichier** : `install.sh`

#### Installation

```bash
unzip MarkNote-0.0.6-mac.zip
cd MarkNote-0.0.6-mac/
./install.sh
```

#### Ce que fait l'installeur

| Action | Détail |
|--------|--------|
| **Bundle `.app`** | Crée `~/Applications/MarkNote.app/` avec la structure standard macOS |
| **Ressources** | JAR, libs et JRE copiés dans `Contents/Resources/` |
| **Icône** | Conversion du PNG 128px en `.icns` via `sips` + `iconutil` (avec fallback PNG) |
| **Info.plist** | Fichier de métadonnées du bundle (nom, version, identifiant, types de fichiers) |
| **Launcher** | Exécutable shell dans `Contents/MacOS/MarkNote` |

#### Structure du bundle `.app`

```
~/Applications/MarkNote.app/
├── Contents/
│   ├── Info.plist
│   ├── MacOS/
│   │   └── MarkNote          # Launcher exécutable
│   └── Resources/
│       ├── MarkNote-0.0.6.jar
│       ├── libs/              # JavaFX + dépendances
│       ├── jre/               # JRE minimal
│       └── marknote.icns      # Icône application
```

#### Info.plist

Le fichier `Info.plist` déclare :

| Clé | Valeur |
|-----|--------|
| `CFBundleIdentifier` | `com.snapgames.marknote` |
| `CFBundleExecutable` | `MarkNote` |
| `CFBundleIconFile` | `marknote` |
| `NSHighResolutionCapable` | `true` |
| `CFBundleDocumentTypes` | `.md`, `.markdown`, `.txt` |

#### Lancement après installation

- Depuis le **Finder** : `~/Applications/MarkNote.app`
- Depuis **Spotlight** : taper "MarkNote"
- Depuis le **Terminal** : `open ~/Applications/MarkNote.app`

> **Note** : L'application n'est pas signée (pas de certificat Apple Developer). Au premier lancement, macOS pourra demander une confirmation via Préférences Système → Sécurité.

---

## Désinstallation

### Linux

```bash
rm -rf ~/.local/share/MarkNote
rm -f ~/.local/bin/marknote
rm -f ~/.local/share/applications/marknote.desktop
rm -f ~/Desktop/marknote.desktop
```

### Windows

Supprimer les éléments suivants :

```
%LOCALAPPDATA%\MarkNote\
Desktop\MarkNote.lnk
%APPDATA%\Microsoft\Windows\Start Menu\Programs\MarkNote.lnk
```

### macOS

```bash
rm -rf ~/Applications/MarkNote.app
```

---

## Arborescence du package

```
MarkNote-0.0.6-linux/
├── MarkNote-0.0.6.jar          # Application
├── MarkNote.sh                 # Launcher
├── install.sh                  # Installeur Linux
├── icons/
│   ├── marknote.svg            # Icône vectorielle
│   ├── marknote-128.png        # 128×128
│   ├── marknote-64.png         # 64×64
│   ├── marknote-32.png         # 32×32
│   └── marknote-16.png         # 16×16
├── libs/
│   ├── javafx-base-25.jar
│   ├── javafx-controls-25.jar
│   ├── javafx-graphics-25.jar
│   ├── javafx-web-25.jar
│   ├── flexmark-*.jar
│   └── ...
└── jre/
    ├── bin/
    │   └── java
    ├── lib/
    └── ...
```
