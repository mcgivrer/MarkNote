# MarkNote User Guide

Welcome to MarkNote, a lightweight and modern Markdown editor built with JavaFX. This guide will help you get started and make the most of MarkNote's features.

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Main Interface](#main-interface)
4. [Working with Documents](#working-with-documents)
5. [Project Explorer](#project-explorer)
6. [Search & Indexing](#search--indexing)
7. [Tag Cloud](#tag-cloud)
8. [Status Bar](#status-bar)
9. [Live Preview](#live-preview)
10. [Splash Screen & About](#splash-screen--about)
11. [Themes](#themes)
12. [Options & Settings](#options--settings)
13. [Keyboard Shortcuts](#keyboard-shortcuts)
14. [Troubleshooting](#troubleshooting)

---

## Introduction

MarkNote is a cross-platform Markdown editor designed for writers, developers, and anyone who works with Markdown documents. It provides a distraction-free writing environment with real-time preview, project management, and customizable themes.

### Key Features

- **Markdown Editing** - Full-featured editor with syntax highlighting
- **Live Preview** - Real-time HTML rendering as you type
- **Syntax Highlighting** - Code blocks with automatic language detection and theme-coordinated coloring
- **Markdown Tables** - Full GFM table support with styled rendering
- **PlantUML Diagrams** - Render PlantUML diagrams directly in the preview
- **Mermaid Diagrams** - Render Mermaid flowcharts, sequences, and more in the preview
- **Math Equations** - LaTeX/MathML support via KaTeX (`$...$` inline, `$$...$$` block)
- **Project Explorer** - Browse and manage your project files
- **Project Indexing** - Automatic indexing of Markdown files by front matter and filenames
- **Search** - Instant full-text search across indexed documents with live results popup
- **Tag Cloud** - Visual tag cloud showing tag frequency; click to search
- **Status Bar** - Document info, statistics, and indexing progress at the bottom of the window
- **Multi-document Tabs** - Work on multiple files simultaneously
- **Theme Support** - Built-in themes with custom theme creation
- **Splash Screen** - Themed splash screen at startup (configurable)
- **Image Preview** - Quick preview for images with zoom and pan
- **Recent Projects** - Quick access to your recent work
- **Multi-language Support** - Available in 5 languages

---

## Getting Started

### First Launch

When you first launch MarkNote, you'll see the **Splash Screen** displaying the application name, version, author, and copyright. Click anywhere or wait a few seconds to dismiss it.

Then you'll be greeted with the **Welcome page**:

![Welcome Page](illustrations/welcome-page.svg)

The Welcome page shows:
- A list of your recent projects (if any)
- Quick access buttons to open projects

### Opening a Project

1. Click **File → Open project...** in the menu
2. Navigate to your project folder
3. Click **Open**

Your project files will appear in the Project Explorer panel.

### Creating a New Document

1. Click **File → New doc** or press `Ctrl+N`
2. A new untitled document tab will open
3. Start writing your Markdown content

---

## Main Interface

MarkNote's interface is divided into three main areas:

![Main Interface](illustrations/main-interface.svg)

### 1. Project Explorer & Tag Cloud (Left Panel)

The left panel contains two sub-panels:

- **Project Explorer** (top) - Displays your project's file structure in a tree view. Navigate through folders, double-click files to open them, and right-click for context menu options.
- **Tag Cloud** (bottom) - Shows all tags found in your project's Markdown front matter, with font size proportional to frequency. Click a tag to search for it.

### 2. Search Box (Top Bar)

Located to the right of the menu bar, the search box lets you instantly search across all indexed documents. Results appear in a popup as you type.

### 3. Editor (Center Panel)

The main editing area where you write your Markdown. Features include:
- Syntax highlighting for Markdown elements
- Tab-based interface for multiple documents
- Undo/Redo support
- Line numbers (optional)

### 4. Preview Panel (Right Panel)

Shows the rendered HTML output of your Markdown in real-time. Features:
- Navigation buttons (back/forward through history)
- Refresh button
- Clickable links that navigate within your project

### 5. Status Bar (Bottom)

A thin bar at the bottom of the window showing:
- **Document name** and **cursor position** (line:column) on the left
- **Statistics** (document count, line count, word count) in the center
- **Indexing progress bar** on the right (visible only during indexing)

### Toggling Panels

You can show or hide panels using the **View** menu:
- **View → Project explorer** - Toggle the left panel
- **View → Preview panel** - Toggle the right panel

---

## Working with Documents

### Creating Documents

![File Menu](illustrations/file-menu.svg)

**From the menu:**
- **File → New doc** (`Ctrl+N`) - Creates a new untitled document

**From Project Explorer:**
- Right-click on a folder → **New file**
- Enter a filename (include .md extension)

### Opening Documents

**From the menu:**
- **File → Open file...** (`Ctrl+O`) - Opens a file dialog
- **File → Recent files** - Shows recently opened files

**From Project Explorer:**
- Double-click any Markdown file to open it in a new tab

### Saving Documents

- **File → Save** (`Ctrl+S`) - Save the current document
- **File → Save as...** (`Ctrl+Shift+S`) - Save with a new name

When you try to close a modified document without saving, MarkNote will prompt you to save your changes.

### Working with Tabs

- Click on a tab to switch to that document
- Click the **×** button on a tab to close it
- Modified documents show a **•** indicator in the tab

---

## Project Explorer

The Project Explorer helps you manage your project files efficiently.

![Project Explorer](illustrations/project-explorer.svg)

### Navigating Files

- **Single-click** - Select a file or folder
- **Double-click** - Open a file in the editor
- **Expand/Collapse** - Click the arrow icons to navigate folders

### Context Menu

Right-click on files or folders to access:

| Action | Description |
|--------|-------------|
| **New file** | Create a new file in the selected folder |
| **New folder** | Create a new subfolder |
| **Rename...** | Rename the selected item |
| **Delete** | Delete the selected item (with confirmation) |
| **Rebuild index** | Rebuild the project search index (root folder only) |

### Drag and Drop

- **Move files** - Drag files/folders to reorganize your project
- **Copy external files** - Drag files from your file manager into MarkNote

### Supported File Types

| Type | Extensions | Action |
|------|------------|--------|
| Markdown | `.md`, `.markdown` | Opens in editor with preview |
| Text | `.txt`, `.text` | Opens in editor |
| Images | `.png`, `.jpg`, `.gif`, `.svg` | Opens in image preview |
| CSS | `.css` | Opens with CSS syntax highlighting |

---

## Search & Indexing

MarkNote automatically indexes all Markdown files in your project to enable fast searching.

![Search Box](illustrations/search-box.svg)

### How Indexing Works

When you open a project, MarkNote scans all Markdown files and extracts metadata from their YAML front matter:

```yaml
---
title: Getting Started Guide
tags: tutorial, beginner, guide
authors: John Doe
summary: A step-by-step introduction to MarkNote
uuid: 550e8400-e29b-41d4-a716-446655440000
draft: false
---
```

The index is stored as a `.marknote-index.json` file in your project root (hidden from the Project Explorer).

### Incremental Updates

The index is automatically kept up to date:
- **Creating a file** - The new file is immediately indexed
- **Saving a file** - Front matter changes are re-indexed
- **Renaming a file** - The index entry is updated
- **Moving files** - Paths are updated in the index
- **Deleting a file** - The entry is removed from the index
- **Copying files** - New entries are added for the copies

### Using the Search Box

The search box is located in the top-right corner of the menu bar:

1. Click the search field or start typing
2. Results appear instantly in a dropdown popup
3. Each result shows:
   - **Document title** (bold)
   - **Match type** (e.g., "title: ...", "tag: ...", "filename: ...")
   - **File path** (relative to the project root)
4. Click a result or press `Enter` to open the document
5. Press `Escape` to dismiss the results

Search matches against:
- Document title
- Filename
- Tags
- Summary
- Authors
- UUID

### Rebuilding the Index

If the index becomes out of sync, you can rebuild it:

1. Right-click on the **root folder** in the Project Explorer
2. Select **Rebuild index**
3. The index will be regenerated from scratch

> **Note:** The "Rebuild index" option only appears on the root project folder.

---

## Tag Cloud

The Tag Cloud panel provides a visual overview of all tags used across your project.

![Tag Cloud](illustrations/tag-cloud.svg)

### How It Works

The Tag Cloud displays all tags extracted from your documents' YAML front matter (`tags:` field). Each tag is displayed as a clickable label with:

- **Font size proportional to frequency** - Tags used in many documents appear larger (up to 28px), while rare tags appear smaller (down to 11px)
- **Color variation** - Tags are displayed in different colors for visual distinction

### Interacting with Tags

- **Click a tag** to immediately search for all documents tagged with it. The search term is entered in the Search Box and matching results are shown.
- **Hover** over a tag to see it highlighted

### Tag Cloud Location

The Tag Cloud panel appears below the Project Explorer in the left panel. It can be closed using the **×** button in its header.

### Keeping Tags Updated

The Tag Cloud updates automatically whenever the project index changes:
- Opening a project
- Creating, saving, renaming, or deleting documents
- Rebuilding the index

---

## Status Bar

The status bar is displayed at the bottom of the main window and provides at-a-glance information about your current work.

![Status Bar](illustrations/status-bar.svg)

### Sections

| Section | Content |
|---------|----------|
| **Document & Position** | Name of the active document and cursor position (Ln/Col) |
| **Statistics** | Number of indexed documents, lines in the current document, and word count |
| **Indexing Progress** | A progress bar shown while the indexing service is running |

### Background Indexing

When a full index build or rebuild is triggered, the indexing runs in a **background thread** so that your editing is never interrupted. The progress bar shows the indexation progress in real-time. Once complete, the status returns to "Ready".

---

## Live Preview

The Preview panel shows your Markdown rendered as HTML in real-time.

### Navigation

| Button | Description |
|--------|-------------|
| **◀** | Go back to previous state |
| **▶** | Go forward |
| **↻** | Refresh the preview |
| **×** | Close the preview panel |

### Clicking Links

When you click a Markdown link in the preview:
- **Local files** (relative paths) - Open in MarkNote
- **External URLs** - Open in your default browser

### Supported Markdown Features

MarkNote supports standard Markdown syntax plus extensions:

```markdown
# Headings (H1 through H6)

**Bold text** and *italic text*

`Inline code` and code blocks

- Bullet lists
- With multiple items

1. Numbered lists
2. Work too

[Links](https://example.com)

![Images](path/to/image.png)

> Blockquotes

---
Horizontal rules
---

| Tables | Are | Supported |
|--------|-----|-----------|
| Data   | Goes| Here      |
```

### Code Syntax Highlighting

Fenced code blocks are automatically highlighted with language detection:

````markdown
```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, world!");
    }
}
```
````

The syntax highlighting theme automatically adapts to your chosen application theme (e.g., a dark highlight.js theme is used with the Dark app theme).

### PlantUML Diagrams

Embed PlantUML diagrams directly in your Markdown using fenced code blocks:

````markdown
```plantuml
@startuml
Alice -> Bob: Hello
Bob --> Alice: Hi!
@enduml
```
````

Diagrams are rendered as SVG images via the PlantUML server.

### Mermaid Diagrams

Mermaid diagrams are also supported:

````markdown
```mermaid
graph LR
    A[Start] --> B{Decision}
    B -->|Yes| C[OK]
    B -->|No| D[Cancel]
```
````

Mermaid diagrams are rendered client-side in the preview panel. The Mermaid theme adapts to your application theme.

### Math Equations (KaTeX)

MarkNote supports LaTeX math notation via KaTeX:

- **Inline math:** `$E = mc^2$` renders as an inline equation
- **Block math:**

```markdown
$$
\int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}
$$
```

### Image Sizing

MarkNote extends the standard Markdown image syntax to allow specifying image dimensions using `=WIDTHxHEIGHT` at the end of the image declaration:

```markdown
![alt text](path/to/image.png "optional title" =100x20)
```

This renders the image with `width="100"` and `height="20"` attributes.

You can also specify only width or only height:

| Syntax | Result |
|--------|--------|
| `![photo](pic.png =300x200)` | Width 300px, Height 200px |
| `![photo](pic.png "title" =400x)` | Width 400px, height auto |
| `![photo](pic.png =x150)` | Width auto, height 150px |

> **Note:** Without the `=WxH` suffix, images behave as standard Markdown images and scale automatically to fit the preview.

---

## Splash Screen & About

### Splash Screen

When MarkNote starts, a themed splash screen is displayed showing:
- The application name and version
- Author and contact information
- Copyright notice

Click anywhere on the splash screen to dismiss it and continue to the main window.

The splash screen follows the current application theme (Light, Dark, Solarized, etc.). You can disable it in **Help → Options... → Misc. → Show splash screen on startup**.

### About Dialog

Access the same information at any time via **Help → About**. The About dialog displays the same content as the splash screen.

---

## Themes

MarkNote comes with several built-in themes and allows you to create custom themes.

![Themes](illustrations/themes.svg)

### Built-in Themes

| Theme | Description |
|-------|-------------|
| **Light** | Clean white background (default) |
| **Dark** | Dark background, easy on the eyes |
| **Solarized Light** | Warm, low-contrast light theme |
| **Solarized Dark** | Popular dark theme with warm colors |
| **High Contrast** | Maximum contrast for accessibility |

### Changing Themes

1. Go to **Help → Options...**
2. Select the **Themes** tab
3. Click on your desired theme
4. Click **OK** to apply

### Creating Custom Themes

1. In the Themes options tab, click **Create theme...**
2. Enter a name for your theme
3. The theme editor will open with CSS code
4. Modify the CSS to customize colors and styles
5. Save the file (`Ctrl+S`)

Custom themes are stored in `~/.marknote/themes/`.

### Theme CSS Structure

```css
/* Main editor colors */
.code-area {
    -fx-background-color: #1e1e1e;
    -fx-text-fill: #abb2bf;
}

/* Markdown syntax highlighting */
.heading { -fx-fill: #c678dd; }
.bold { -fx-fill: #e06c75; }
.italic { -fx-fill: #98c379; }
.code { -fx-fill: #61afef; }
```

---

## Options & Settings

Access settings via **Help → Options...** or by pressing the shortcut shown in the menu.

### Misc. Tab

| Option | Description |
|--------|-------------|
| **Number of recent files/projects** | How many items to show in Recent menus (1-50) |
| **Create document on startup** | Automatically create a new document when starting |
| **Reopen last project on startup** | Remember and reopen your last project |
| **Show Welcome page on startup** | Display the Welcome tab when starting |
| **Show splash screen on startup** | Display the splash screen when starting (enabled by default) |
| **Language** | Choose your preferred interface language |

### Themes Tab

- View and select from available themes
- Create new custom themes
- Delete custom themes (built-in themes cannot be deleted)

### Language Settings

MarkNote supports the following languages:
- 🇫🇷 Français (French)
- 🇬🇧 English
- 🇩🇪 Deutsch (German)
- 🇪🇸 Español (Spanish)
- 🇮🇹 Italiano (Italian)

To change the language:
1. Go to **Help → Options...**
2. In the **Misc.** tab, select your language
3. The application will restart to apply the change

---

## Keyboard Shortcuts

![Keyboard Shortcuts](illustrations/keyboard-shortcuts.svg)

### File Operations

| Shortcut | Action |
|----------|--------|
| `Ctrl+N` | New document |
| `Ctrl+O` | Open file |
| `Ctrl+S` | Save |
| `Ctrl+Shift+S` | Save as |
| `Ctrl+W` | Close current tab |
| `Ctrl+Q` | Quit application |

### Editing

| Shortcut | Action |
|----------|--------|
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+X` | Cut |
| `Ctrl+C` | Copy |
| `Ctrl+V` | Paste |
| `Ctrl+A` | Select all |

### Navigation

| Shortcut | Action |
|----------|--------|
| `Ctrl+Tab` | Next tab |
| `Ctrl+Shift+Tab` | Previous tab |
| `F5` | Refresh preview |

> **Note:** On macOS, use `Cmd` instead of `Ctrl`.

---

## Troubleshooting

### Common Issues

#### The preview is not updating

1. Click the **Refresh** button (↻) in the preview panel
2. Check that the Preview panel is visible (View → Preview panel)
3. Make sure you're editing a Markdown file (.md)

#### Files are not showing in Project Explorer

1. Make sure you've opened a project (File → Open project...)
2. Check that the Project Explorer is visible (View → Project explorer)
3. Try refreshing by closing and reopening the project

#### Theme changes are not applied

1. Make sure to save your custom theme CSS file
2. If editing the current theme, close and reopen options
3. Restart MarkNote if changes still don't appear

#### Application language didn't change

1. The application needs to restart after changing language
2. Try closing and reopening MarkNote manually
3. Check the language setting in Options → Misc.

### Getting Help

If you encounter issues not covered here:

1. Check the [GitHub repository](https://github.com/mcgivrer/marknote) for known issues
2. Submit a bug report with details about your system and the problem
3. Contact the author at contact.snapgames@gmail.com

---

## About MarkNote

**Version:** 0.0.3
**Author:** Frédéric Delorme  
**Copyright:** © SnapGames 2026  
**License:** MIT  
**Repository:** https://github.com/mcgivrer/marknote

---

*This documentation is part of the MarkNote project. Last updated: February 2026.*
