---
title: "MarkNote User Guide"
date: 2026-03-10
version: "0.1.5"
author: "Frédéric Delorme"
description: "Official user guide for MarkNote, a lightweight Markdown editor built with JavaFX."
summary: "Welcome to MarkNote, a lightweight and modern Markdown editor built with JavaFX. This guide will help you get started and make the most of MarkNote's features."
tags: [marknote, markdown, user-guide, documentation]
lang: en
status: draft
---

# MarkNote User Guide

Version 0.1.5

Welcome to MarkNote, a lightweight and modern Markdown editor built with JavaFX. This guide will help you get started and make the most of MarkNote's features.

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Main Interface](#main-interface)
4. [Working with Documents](#working-with-documents)
5. [Search & Replace in Editor](#search--replace-in-editor)
6. [Editor Context Menu & Floating Toolbar](#editor-context-menu--floating-toolbar)
7. [Front Matter Panel](#front-matter-panel)
8. [Project Explorer](#project-explorer)
9. [Git Support](#git-support)
10. [Search & Indexing](#search--indexing)
11. [Tag Cloud](#tag-cloud)
12. [Network Diagram](#network-diagram)
13. [Status Bar](#status-bar)
14. [Live Preview](#live-preview)
15. [LLM Chat](#llm-chat)
16. [Splash Screen & About](#splash-screen--about)
17. [Themes](#themes)
18. [Options & Settings](#options--settings)
19. [Keyboard Shortcuts](#keyboard-shortcuts)
20. [Troubleshooting](#troubleshooting)

---

## Introduction

MarkNote is a cross-platform Markdown editor designed for writers, developers, and anyone who works with Markdown documents. It provides a distraction-free writing environment with real-time preview, project management, and customizable themes.

### Key Features

- **Markdown Editing** - Full-featured editor with syntax highlighting (headings, bold, italic, strikethrough, code, blockquotes, lists, links, images, horizontal rules)
- **Live Preview** - Real-time HTML rendering as you type
- **Syntax Highlighting** - Code blocks with automatic language detection and theme-coordinated coloring
- **Code Block Copy Button** - One-click copy for code blocks in preview
- **Markdown Tables** - Full GFM table support with styled rendering
- **Task Lists** - GitHub-style checkboxes (`[ ]` / `[x]`) rendered in preview
- **GitHub Alerts** - Styled blockquotes for `[!NOTE]`, `[!TIP]`, `[!IMPORTANT]`, `[!WARNING]`, `[!CAUTION]`
- **PlantUML Diagrams** - Render PlantUML diagrams directly in the preview; switch between the **online PlantUML server** (default) or a **local `plantuml.jar`** configured in Options → Tools; local rendering is asynchronous (per-block background threads) and shows a ⚙ spinning gear icon in the status bar during generation; **in-memory SVG cache** avoids regenerating unchanged diagrams
- **Mermaid Diagrams** - Render Mermaid flowcharts, sequences, and more in the preview (theme auto-matches app theme)
- **Math Equations** - LaTeX/MathML support via KaTeX (`$...$` inline, `$$...$$` block)
- **Front Matter Panel** - Collapsible panel above the editor showing and editing YAML front matter metadata, with UUID-based document linking via drag & drop
- **Project Explorer** - Browse and manage your project files, with front matter titles displayed for `.md` files
- **Project Indexing** - Automatic incremental indexing of Markdown files by front matter and filenames
- **Search** - Instant full-text search across indexed documents with live results popup (up to 20 results)
- **Search & Replace in Editor** - In-editor overlay bar (`Ctrl+F` / `Ctrl+H`) with Regex, Full Word, and Match Case toggles, occurrence navigation and bulk replace
- **LLM Chat** - Optional dockable assistant panel with streaming responses, conversation history, system context, export, and insertion of generated content into the active document
- **Tag Cloud** - Visual tag cloud showing tag frequency; click to search
- **Network Diagram** - Interactive force-directed graph of document links and shared tags, with tooltips and current document highlighting
- **Status Bar** - Document info, statistics, and indexing progress at the bottom of the window
- **Multi-document Tabs** - Work on multiple files simultaneously, with drag-to-reorder tabs
- **Panel Detachment** - Detach side panels to independent tabs, and restore them back to their docked position
- **Theme Support** - Built-in themes with custom theme creation and a full CSS theme editor
- **Splash Screen** - Themed splash screen at startup (configurable)
- **Image Preview** - Quick preview for images with zoom/pan and format/size info overlay
- **Recent Projects** - Quick access to your recent work (files and projects, with Clear History)
- **Project Session Restore** - Open documents are automatically saved when you close a project and restored when you reopen it; the session is stored in a `.marknote` file at the project root
- **Scroll Synchronization** - The editor and preview panels scroll in sync so that the rendered output always reflects the text at the cursor position
- **Drag & Drop** - Drop files into the editor to insert Markdown links or images
- **Reading Mode** - Distraction-free fullscreen reading with the preview panel filling the screen; the Project Explorer floats as a compact overlay with a minimize toggle; all other panels are hidden and fully restored on exit (`Ctrl+Shift+P`)
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

### 1. Project Explorer, Tag Cloud & Network Diagram (Left Panel)

The left panel contains three sub-panels arranged vertically in a resizable split:

- **Project Explorer** (top) - Displays your project's file structure in a tree view. Navigate through folders, double-click files to open them, and right-click for context menu options.
- **Tag Cloud** (middle) - Shows all tags found in your project's Markdown front matter, with font size proportional to frequency. Click a tag to search for it.
- **Network Diagram** (bottom) - An interactive force-directed graph showing the relationships between documents (via links) and shared tags. See [Network Diagram](#network-diagram) for details.

### 2. Search Box (Top Bar)

Located to the right of the menu bar, the search box lets you instantly search across all indexed documents. Results appear in a popup as you type.

### 2b. Edit Menu

The **Edit** menu provides in-document text operations:

| Item | Shortcut | Description |
|------|----------|-------------|
| **Search...** | `Ctrl+F` | Open the Search bar overlay (search field + options) |
| **Search and Replace...** | `Ctrl+H` | Open the Search & Replace overlay (both fields) |

### 3. Editor (Center Panel)

The main editing area where you write your Markdown. Features include:

- Syntax highlighting for Markdown elements (headings, bold, italic, strikethrough, code, blockquotes, lists, links, images, horizontal rules)
- Tab-based interface for multiple documents
- Drag-to-reorder tabs within the tab bar
- Undo/Redo support
- Line numbers (optional)
- Tab names truncated to 15 characters with ellipsis (full name in tooltip)
- Modified documents prefixed with `*` in the tab title

### 3a. Front Matter Panel (Above Editor)

Above the editor text area, a **collapsible Front Matter panel** displays and lets you edit the YAML front matter of the current document. See [Front Matter Panel](#front-matter-panel) for details.

![Front Matter Panel](illustrations/front-matter-panel.svg)

### 4. Preview Panel (Right Panel)

Shows the rendered HTML output of your Markdown in real-time. Features:

- Navigation buttons (back/forward through history)
- Refresh button
- Clickable links that navigate within your project

### 4b. LLM Chat Panel (Right Dock)

When enabled in **Help → Options... → LLM**, MarkNote adds an **LLM Chat** panel on the right side of the workspace.

It provides:

- A conversation view with your prompts and streamed assistant responses
- A prompt input area with a **System Context** button, submit button, and cancel button while a request is running
- Quick actions to copy, export, edit, or insert generated content into the active document

See [LLM Chat](#llm-chat) for setup and usage details.

### 5. Status Bar (Bottom)

A thin bar at the bottom of the window showing:

- **Document name** and **cursor position** (line:column) on the left
- **Statistics** (document count, line count, word count) in the center
- **PlantUML local-jar indicator** (right of center, visible only when local PlantUML mode is active):
  - **⚙ spinning gear** — animated during diagram rendering via the local jar
  - **● PlantUML: local jar** — static badge confirming local mode is on
- **Indexing progress bar** on the right (visible only during indexing)

### Toggling Panels

You can show or hide panels using the **View** menu or by clicking the **×** close button on each panel header:

![View Menu](illustrations/view-menu.svg)

| Shortcut | Action |
|----------|--------|
| **View → Project explorer** (`Ctrl+E`) | Toggle the left Project Explorer |
| **View → Preview panel** (`Ctrl+P`) | Toggle the right Preview pane |
| **View → Tag Cloud** (`Ctrl+T`) | Toggle the Tag Cloud sub-panel |
| **View → Network Diagram** (`Ctrl+L`) | Toggle the Network Diagram sub-panel |
| **View → LLM Chat** (`Ctrl+M`) | Toggle the LLM Chat panel when the feature is enabled |
| **View → Enter Reading Mode** (`Ctrl+Shift+P`) | Enter distraction-free fullscreen reading mode |
| **View → Show Welcome** | Open the Welcome tab |

Each panel can also be closed by clicking the **×** button in its header. Re-opening it from the View menu restores it to the layout.

### Detaching Panels to Tabs

Left-side panels (**Project Explorer**, **Tag Cloud**, **Network Diagram**) plus the **Preview** and **LLM Chat** panels can be **detached** from their docked position and converted into independent tabs in the main editor area. This gives you more flexibility to organize your workspace.

**To detach a panel:**

1. Click the **⇱ detach button** (window icon) in the panel's header bar
2. The panel disappears from its docked position and opens as a new tab in the editor tab bar
3. The View menu checkbox for that panel is automatically unchecked

**To restore a detached panel:**

1. Click the **⇲ restore button** (dock icon) in the tab's header, OR
2. Right-click on the tab and select **Restore to Panel**, OR
3. Re-enable the panel from the **View** menu (this will close the tab and restore the panel to its original position)

![Panel Detach](illustrations/panel-detach.svg)

> **Tip:** Detaching panels is useful when you want to maximize the editor area while still keeping certain panels accessible as tabs.

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

- **File → Open file...** (`Ctrl+O`) - Opens a file dialog with extension filters:
  - **Markdown** (`*.md`, `*.markdown`)
  - **Text** (`*.txt`)
  - **All files** (`*.*`)
- **File → Open project...** - Opens a directory chooser to select a project folder
- **File → Recent** - Shows recently opened files and projects

**From Project Explorer:**

- Double-click any Markdown file to open it in a new tab

### Recent Files & Projects

The **File → Recent** submenu is organized into two sections:

- **Files** — recently opened files, displayed as `name (parent directory)`
- **Projects** — recently opened project directories
- **Clear History** — clears all recent entries

If a recent file or directory no longer exists on disk, an error dialog is shown and the entry is automatically removed.

### Saving Documents

- **File → Save** (`Ctrl+S`) - Save the current document
- **File → Save as...** (`Ctrl+Shift+S`) - Save with a new name

When you try to close a modified document without saving, MarkNote will prompt you to save your changes.

### Project Session Restore

MarkNote automatically remembers which documents you had open when you close a project and reopens them the next time you open the same project.

- **How it works:** When you close the app or switch projects, the list of open files is saved to a `.marknote` file at the root of yourproject directory (one relative path per line, under a `[open_files]` section).
- **On next open:** Files listed in `.marknote` are restored in the same order; files that no longer exist on disk are silently skipped.
- **Transparent:** The `.marknote` file is hidden in the Project Explorer, just like `.marknote-index.json`.

> **Note:** Session restore is independent from the **Reopen last project on startup** option, which controls whether the project folder itself is reopened, not which documents were open inside it.

---

### Working with Tabs

- Click on a tab to switch to that document
- Click the **×** button on a tab to close it
- Press `Ctrl+W` to close the active tab
- Press `Ctrl+Shift+W` to close **all** tabs at once
- **Right-click** on any tab for additional close actions:
  - **Close All Tabs** — closes every open tab
  - **Close All But This** — keeps only the tab you right-clicked
  - **Close Tabs to the Left** — closes all tabs to the left of the current one
- **Drag tabs** to reorder them within the tab bar
- Modified documents show a **\*** prefix in the tab title
- Tab names longer than 15 characters are truncated with an ellipsis (`…`); hover for the full name

### Drag & Drop into the Editor

You can drag files from the Project Explorer directly into the editor to insert Markdown links:

![Drag & Drop Links](illustrations/drag-drop-editor.svg)

- **Image files** (`.png`, `.jpg`, `.gif`, `.svg`) → inserts `![filename](relative/path)`
- **Markdown files** (`.md`) → inserts `[front matter title](relative/path)` (uses the front matter title if available, otherwise the filename)

The link is inserted at the exact drop position in the text.

---

## Search & Replace in Editor

MarkNote includes a **Search & Replace overlay bar** that floats over the editor without interrupting your layout. It appears just below the Front Matter panel and only takes up the space it needs.

### Opening the Bar

| Method | Result |
|--------|--------|
| `Ctrl+F` or **Edit → Search...** | Opens the bar with the **search field only** |
| `Ctrl+H` or **Edit → Search and Replace...** | Opens the bar with **both search and replace fields** |
| `Escape` or **✕ button** | Closes the bar and removes all highlights |

### Search Field & Options

The search row contains:

| Control | Description |
|---------|-------------|
| **Search field** | Type your query here; results are highlighted as you type |
| `.*` toggle | Enable **Regular Expression** mode |
| `\b` toggle | Enable **Full Word** matching |
| `Aa` toggle | Enable **Match Case** (case-sensitive search) |
| `▲` button | Navigate to the **previous** occurrence (`Shift+Enter` also works) |
| `▼` button | Navigate to the **next** occurrence (`Enter` also works) |
| **Counter** | Shows the current position and total count (e.g., `2 / 7`) or `No results` |
| `✕` button | Close the bar |

### Replace Field

Visible only when opened via `Ctrl+H` or **Edit → Search and Replace...**:

| Control | Description |
|---------|-------------|
| **Replace field** | The replacement text (plain text, no regex syntax required) |
| **Replace** button | Replace the **currently highlighted** occurrence and move to the next |
| **Replace all** button | Replace **all** occurrences at once |

### Occurrence Highlighting

- All occurrences are highlighted in **yellow** (`search-highlight`)
- The **currently selected** occurrence is highlighted in **orange** (`search-highlight-current`) and the editor scrolls to it
- When the bar is closed, all highlights are removed and normal syntax highlighting is restored

### Regex Mode

When the `.*` toggle is active:

- The query is interpreted as a Java regular expression
- If the pattern is invalid, the search field turns **red** and a `⚠` warning is shown
- Capture groups can be used in the Replace field (e.g., `$1`)

---

## Editor Context Menu & Floating Toolbar

MarkNote provides two complementary ways to apply Markdown formatting without manually typing syntax: a **right-click context menu** and a **floating toolbar** that appears above any text selection.

### Editor Context Menu

Right-click anywhere in the editor to open the context menu:

| Item | Shortcut | Condition |
|------|----------|-----------|
| **Copy** | `Ctrl+C` | Requires selection |
| **Cut** | `Ctrl+X` | Requires selection |
| **Paste** | `Ctrl+V` | Always available |
| *(separator)* | | |
| **Title H1** | `Ctrl+1` | Acts on current line |
| **Title H2** | `Ctrl+2` | Acts on current line |
| **Title H3** | `Ctrl+3` | Acts on current line |
| **Title H4** | `Ctrl+4` | Acts on current line |
| **Title H5** | `Ctrl+5` | Acts on current line |
| **Title H6** | `Ctrl+6` | Acts on current line |
| *(separator)* | | |
| **Bold** | `Ctrl+B` | Requires selection |
| **Italic** | `Ctrl+I` | Requires selection |
| *(separator)* | | |
| **Insert link** | `Ctrl+K` | Requires selection (becomes the URL) |
| **Insert image** | `Ctrl+J` | Requires selection (becomes the path) |
| **Insert code block** | `Ctrl+E` | Requires selection |

#### Heading toggle

Applying a heading level (`Ctrl+1`…`Ctrl+6`) acts on the **current line** (no selection needed). If the line already has the same heading level, the prefix is removed (toggle off).

#### Bold / Italic toggle

Bold (`Ctrl+B`) and Italic (`Ctrl+I`) **wrap** the selected text with `**` or `*`. If the selection is already wrapped, the markers are removed instead.

#### Insert link / image

- **Insert link** (`Ctrl+K`): wraps the selection as the URL → `[](selection)`, caret lands inside `[`.
- **Insert image** (`Ctrl+J`): same but with image syntax → `![](selection)`, caret lands inside `![`.
- **Insert code block** (`Ctrl+E`): wraps the selection in a fenced code block.

### Floating Formatting Toolbar

Whenever you **select text** in the editor, a compact floating toolbar appears just above the selection:

| Button | Action |
|--------|--------|
| **B** | Bold (`**…**`) |
| **I** | Italic (`*…*`) |
| **Lien** | Insert link |
| **Img** | Insert image |
| **</>** | Insert code block |
| **H1** | Apply Heading 1 |
| **H2** | Apply Heading 2 |
| **H3** | Apply Heading 3 |
| **H4▾** | Dropdown for H4, H5, H6 |

The toolbar **auto-hides** as soon as the selection is cleared, or when the editor loses focus. Clicking any button applies the formatting and hides the toolbar.

> **Tip:** The floating toolbar and the context menu expose the same formatting actions — use whichever fits your workflow.

---

## Front Matter Panel

The Front Matter panel is a **collapsible pane** located above the editor text area. It provides a visual interface for editing YAML front matter metadata without manually writing YAML.

![Front Matter Panel](illustrations/front-matter-panel.svg)

### Supported Fields

| Field | Description |
|-------|-------------|
| **Title** | Document title |
| **Tags** | Comma-separated tags for categorization |
| **Authors** | Document author(s) |
| **Summary** | Brief description of the document |
| **UUID** | Unique identifier (auto-generated if absent) |
| **Created At** | Creation date (`YYYY-MM-DD` or `YYYY-MM-DD HH:mm`, auto-set for new documents) |
| **Draft** | Checkbox indicating if the document is a draft |
| **Custom fields** | Any extra YAML keys are preserved and displayed in *italics* |

### Auto-Generated Fields

- **UUID**: When you open a Markdown file with front matter but no UUID, one is automatically generated and added.
- **Created At**: New documents automatically get today's date.

### Document Linking via Drag & Drop

You can create **UUID-based links** between documents by dragging `.md` files onto the Front Matter panel:

![Front Matter Links](illustrations/front-matter-links.svg)

1. Drag a `.md` file from the Project Explorer onto the Front Matter panel
2. A blue dashed border appears as visual feedback during drag-over
3. The dropped file's UUID is extracted (or auto-generated if the file doesn't have one)
4. The link appears in the collapsible **"Links"** sub-section with a badge showing the count (e.g., "Links (3)")

### Managing Links

- Each link shows as a **clickable hyperlink** displaying the UUID (with a tooltip showing the target document's title)
- Click a link to **open the linked document** in a new tab
- Click the **✕ button** next to a link to remove it
- Links are rendered in the **preview** as styled link badges using the `marknote-link:` protocol

### Expansion Behavior

- When a document has front matter, the panel is **expanded by default**
- For new documents without front matter, the panel is **collapsed**
- You can configure the default expansion in **Help → Options... → Misc. → Front matter expanded by default**

---

## Project Explorer

The Project Explorer helps you manage your project files efficiently.

![Project Explorer](illustrations/project-explorer.svg)

### Navigating Files

- **Single-click** - Select a file or folder (multi-selection supported)
- **Double-click** - Open a file in the editor
- **Expand/Collapse** - Click the arrow icons to navigate folders

> **Note:** Markdown files display their **front matter title** instead of the filename in the tree. Hover for a tooltip showing `"filename — title"`. Files and folders starting with `.` are hidden.

### File Organization

- **Directories appear first**, followed by files
- Both are sorted **alphabetically** (case-insensitive)
- File/folder **icons** are displayed for visual distinction

### Context Menu

Right-click on files or folders to access:

| Action | Description |
|--------|-------------|
| **New file** | Create a new file in the selected folder |
| **New folder** | Create a new subfolder |
| **Rename...** | Rename the selected item |
| **Delete** | Delete the selected item (with confirmation) |
| **Reset index** | Rebuild the project search index (root folder only) |

### Drag and Drop

- **Move files (internal)** - Drag files/folders within the explorer to reorganize your project. The destination folder highlights in **light blue** (MOVE mode). A confirmation dialog shows the file names being moved.
- **Copy external files** - Drag files from your file manager into MarkNote. The destination folder highlights in **light green** (COPY mode). A confirmation dialog shows the file names being copied.
- **Multi-file selection** - Select multiple files/folders and drag them together.

> **Note:** For 4 or more files, the confirmation dialog shows the count instead of individual names.

### Supported File Types

| Type | Extensions | Action |
|------|------------|--------|
| Markdown | `.md`, `.markdown` | Opens in editor with preview |
| Text | `.txt`, `.text` | Opens in editor |
| Images | `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.webp`, `.svg` | Opens in image preview |
| CSS | `.css` | Opens with CSS syntax highlighting |

> **Tip:** If the project is a Git repository, each file also shows a small **colored status dot** — see [Git Support](#git-support) for details.

### Image Preview

When you open an image file, it is displayed in a dedicated **Image Preview tab**:

![Image Preview](illustrations/image-preview.svg)

- **Info banner** at the top shows: file format (uppercase) and dimensions (e.g., `PNG | 800 × 600 px`)
- **Zoom** with the scroll wheel (range: 10% – 1000%, factor 1.1× per step)
- **Zoom level overlay** appears centered (e.g., `"150%"`) and fades out after 2 seconds
- **Pan** the image by dragging when zoomed in
- Supported formats: `png`, `jpg`, `jpeg`, `gif`, `bmp`, `webp`, `svg`

---

## Git Support

MarkNote integrates with Git when a project folder contains a `.git/` subdirectory. No manual activation is required — git support is enabled automatically when you open such a project.

### Git Status Indicators

Each **file** (not folder) in the Project Explorer tree shows a small colored dot indicating its Git status:

| Dot colour | Status | Meaning |
|------------|--------|---------|
| 🟢 Green | `CLEAN` | Tracked, no local changes |
| 🟡 Orange | `MODIFIED` | Tracked and modified (or deleted) in the working tree |
| 🔵 Blue | `STAGED` | Added to the index, not yet committed |
| 🔴 Red | `UNTRACKED` | Not managed by Git |

The dots are refreshed automatically after every file operation (create, rename, delete, move, copy). They are also refreshed after each Sync operation.

### Explorer Toolbar

When a project is open, a toolbar appears at the top of the Project Explorer panel. It contains up to two buttons:

- **↻ Index** — Always visible when a project is loaded. Refreshes the file tree (picking up files added outside of MarkNote) and rebuilds the search index from scratch.
- **⇅ Sync** — Only visible when the project is a Git repository (hidden otherwise).

#### Sync

Clicking **Sync** performs three sequential operations in a background thread:

1. **Commit local changes** (if any modified/untracked files exist)
   - Runs `git add -A` to stage all changes
   - Creates an automatic commit with a structured message:

     ```
     [MarkNote sync] 2026-02-25 14:32:05 @ my-laptop

     Modified:
       - docs/note1.md
       - docs/chapter2.md
     ```

   - The message includes the current date/time, the machine's hostname, and the list of affected files
   - This step is **skipped** if there are no local changes

2. **Pull** — runs `git pull --rebase` to fetch and integrate remote changes without creating a merge commit

3. **Push** — runs `git push` to send all local commits to the remote

Once complete, a **result dialog** appears showing the combined output of all operations. If any step fails, the error output from git is displayed in the same dialog.

### Authentication

Configure credentials in **Help → Options… → Git tab**.

| Method | When to use |
|--------|-------------|
| **SSH key (passphrase-less)** | SSH remote URLs (`git@github.com:...`). Point to your private key file (e.g. `~/.ssh/id_ed25519`). The key must have **no passphrase** (V1 limitation). |
| **Personal access token** | HTTPS remote URLs (`https://github.com/...`). Enter your GitHub / GitLab token and the associated username (typically `token` for GitHub, `oauth2` for GitLab). |

> **Note:** Credentials are stored in plain text in `~/.marknote/config`. For shared machines, prefer SSH keys with file-system-level permissions rather than tokens.

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
2. Results appear instantly in a dropdown popup (up to **20 results**)
3. Each result shows:
   - **Document title** (bold, 13px)
   - **Match context** (gray, 11px — e.g., "Tag: java", "Title: Getting Started")
   - **File path** (italic gray, 10px, relative to the project root)
4. Click a result or press `Enter` (selects first result) to open the document
5. Press `Down Arrow` to navigate the results list
6. Press `Escape` to dismiss the results and clear the field

Search matches against:

- Document title
- Filename
- Tags
- Summary
- Authors
- UUID

### Index Persistence

The index is stored as a `.marknote-index.json` file at the project root. It is **loaded automatically** when you reopen a project, avoiding re-indexing. The index contains: file paths, filenames, UUIDs, titles, authors, tags, summaries, creation dates, draft status, links, and tag counts.

### Rebuilding the Index

If the index becomes out of sync (e.g. files added or removed outside of MarkNote), you can rebuild it in two ways:

**Using the toolbar button (recommended):**
Click the **↻ Index** button at the top of the Project Explorer. This refreshes the file tree and regenerates the index from scratch.

**Using the context menu:**

1. Right-click on the **root folder** in the Project Explorer
2. Select **Rebuild index**
3. The index will be regenerated from scratch

> **Note:** The context menu "Rebuild index" option only appears on the root project folder.

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

## Network Diagram

The Network Diagram panel provides an interactive visualization of the relationships between your documents.

![Network Diagram](illustrations/network-diagram.svg)

### How It Works

The Network Diagram uses a **force-directed layout** algorithm to arrange your documents as a graph:

- **Document nodes** (📄 icon) represent each Markdown file in your project
- **Tag nodes** (blue circles with `#`) represent shared tags
- **Solid edges** connect documents that reference each other via `links:` in their front matter
- **Dashed edges** connect documents to the tags they share
- **Current document** is highlighted with an **orange border** and cream-colored fill
- **Isolated nodes** (no connections) are automatically hidden from the diagram
- **Document groups** — disconnected clusters of documents are circled with pastel-colored halos for easy identification

The physics simulation runs until the layout stabilizes (~60 frames below velocity threshold), then the view **automatically zooms to fit** all nodes with a 40px margin.

### Document Groups

When your project contains multiple independent clusters of documents (no links or shared tags between them), each cluster is displayed inside a **pastel-colored circle**. This helps you visualize which documents form isolated groups.

- **Click a group circle** — Zoom to fit the group in view
- **Double-click a group circle** — Name the group; a dialog prompts for a name which is then displayed as a label and persisted in the index

### Detaching the Diagram

You can detach the Network Diagram into its own tab for a larger view:

1. Click the **Detach** button (↗) in the panel header
2. The diagram opens in a new tab alongside your documents
3. When you close the tab, the diagram returns to the side panel (configurable in Options)

### Automatic Label Hiding

To keep the diagram readable at high zoom-out levels, document labels are automatically hidden when:

- Zoom level is below 50% **and** there are more than 20 documents
- Zoom level is below 30% **and** there are more than 10 documents

This prevents visual clutter and improves performance with large projects.

### Navigation & Interaction

| Action | Description |
|--------|-------------|
| **Click a document node** | Open that document in the editor |
| **Click a tag node** | Show a search popup listing all documents with that tag |
| **Click an edge** | Open the nearest connected document node |
| **Drag a node** | Move a node to rearrange the layout (pins during drag, unpins on release) |
| **Middle-click + drag** | Pan the view |
| **Scroll wheel** | Zoom in/out (range: 0.1× – 8.0×, factor 1.15× per step, centered on cursor; works on all platforms including Linux) |
| **Ctrl + Click** | Reset zoom and recenter (zoom-to-fit) |

### Tooltips

- **Document node hover**: shows title (bold), author, and creation date
- **Tag node hover**: shows `#tagname` and the number of connected documents

### Tag Search Popup

When you click a tag node in the diagram, a popup appears showing all documents that contain that tag. Each result displays:

- **Document title** (bold)
- **Match context** (e.g., "tag: java")
- **File path** (relative to the project root)

Click a result to open the document directly. You can also navigate the popup with the keyboard (`Enter` to select, `Escape` to close).

### Front Matter Links

To create links between documents, add a `links:` field in your YAML front matter with **UUID references**:

```yaml
---
title: My Document
tags: java, tutorial
links:
  - 550e8400-e29b-41d4-a716-446655440000
  - 7c9e6679-7425-40de-944b-e07fc1f90ae7
---
```

These links appear as solid lines connecting the two documents in the diagram. You can also create links by **dragging `.md` files onto the Front Matter panel** (see [Front Matter Panel](#front-matter-panel)).

### Keeping the Diagram Updated

The Network Diagram updates automatically whenever:

- A project is opened
- Files are created, saved, renamed, moved, or deleted
- The index is rebuilt

---

## Status Bar

The status bar is displayed at the bottom of the main window and provides at-a-glance information about your current work.

![Status Bar](illustrations/status-bar.svg)

### Sections

| Section | Content |
|---------|----------|
| **Document & Position** | Name of the active document and cursor position (Ln/Col) |
| **Statistics** | Number of indexed documents, lines in the current document, and word count |
| **PlantUML indicator** | Spinning ⚙ gear during local-jar rendering + "● PlantUML: local jar" badge (only visible when local mode is active in Options → Tools) |
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

### Scroll Synchronization

The editor and the preview panel scroll in sync automatically. As you scroll through the editor text, the preview adjusts so that the visible rendered output matches the text around the cursor. This makes it easy to keep your focus in the same place while checking how the Markdown renders.

> **Tip:** If the previews gets out of sync (e.g., after a large paste), click the **↻ Refresh** button to reset the layout.

### Clicking Links

When you click a Markdown link in the preview:

- **Local Markdown files** (`.md`, `.markdown`, relative paths) - Open in a new MarkNote tab
- **UUID-based links** (`marknote-link:uuid`) - Resolved by searching all project files for the matching UUID, then opened in a tab
- **External URLs** - Open in your default browser

### Collapsible Front Matter Block

When a document has YAML front matter, the preview renders it as a **styled, collapsible `<details>/<summary>` block** at the top:

![Preview Front Matter](illustrations/preview-front-matter.svg)

- **Title** displayed as a heading
- **Draft badge** ("✎ Draft" in red) if `draft: true`
- **UUID** in monospace
- **Author** and **Date**
- **Tags** as styled color badges
- **Summary** in italics
- **Linked documents** as clickable link badges (resolved from UUID to title)

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

![Local images](path/to/image.png)
![External images](https://example.com/image.png)

> Blockquotes

> [!NOTE]
> GitHub-style alerts

---
Horizontal rules
---

- [ ] Unchecked task
- [x] Completed task

| Tables | Are | Supported |
|--------|-----|-----------|
| Data   | Goes| Here      |
```

### Images

MarkNote supports both local and external images in the preview:

- **Local images:** Relative paths from your project directory
- **External images:** HTTP/HTTPS URLs from the internet

```markdown
![Local screenshot](./images/screenshot.png)
![Web image](https://example.com/image.png)
```

> **Note:** External images require an internet connection. If the image URL is unreachable, a broken image placeholder will be displayed.

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

The syntax highlighting theme automatically adapts to your chosen application theme:

| App Theme | highlight.js Style | Code Background | Code Foreground |
|---|---|---|---|
| Light | `github` | `#f6f8fa` | `#24292e` |
| Dark | `github-dark` | `#282c34` | `#e6e6e6` |
| Solarized Light | `stackoverflow-light` | `#fdf6e3` | `#657b83` |
| Solarized Dark | `stackoverflow-dark` | `#002b36` | `#93a1a1` |
| High Contrast | `a11y-dark` | `#1a1a1a` | `#f8f8f2` |

Custom themes are detected via a **"Based on:" comment** in the CSS header, with a heuristic fallback (dark background → dark syntax theme).

### Code Block Copy Button

Every code block in the preview displays a **"Copy" button** on hover (top-right corner). Clicking it copies the code to the clipboard, and the button briefly shows **"✓ Copied"** with a green background for 1.5 seconds.

### Task Lists (Checkboxes)

MarkNote supports GitHub-style task lists (checkboxes) in the preview:

```markdown
- [ ] This is an unchecked task
- [x] This is a completed task
- [X] Uppercase X also works
```

In the preview, these render as:

- ☐ This is an unchecked task
- ☑ This is a completed task
- ☑ Uppercase X also works

> **Note:** Checkboxes in the preview are read-only (disabled). To change the state, edit the Markdown source directly.

### GitHub Alerts

MarkNote supports GitHub-style alerts (also known as admonitions) for highlighting important information in blockquotes:

```markdown
> [!NOTE]
> Useful information that users should know.

> [!TIP]
> Helpful advice for doing things better or more easily.

> [!IMPORTANT]
> Key information users need to know.

> [!WARNING]
> Urgent info that needs immediate user attention.

> [!CAUTION]
> Advises about risks or negative outcomes.
```

These render as styled boxes with colored borders and icons:

| Alert Type | Color | Use Case |
|------------|-------|----------|
| **Note** | Blue | General information |
| **Tip** | Green | Helpful hints and best practices |
| **Important** | Purple | Critical information |
| **Warning** | Yellow | Potential issues or caveats |
| **Caution** | Red | Dangerous actions or irreversible operations |

> **Tip:** GitHub Alerts work great for documentation, tutorials, and user guides where you need to draw attention to specific information.

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

> **Note:** If a PlantUML code block doesn't start with `@start`, it is **automatically wrapped** with `@startuml` / `@enduml`.

#### Rendering Mode

By default, diagrams are rendered by the **official PlantUML online server** (`https://www.plantuml.com/plantuml/svg/`). If an internet connection is unavailable or you prefer privacy, you can configure a **local `plantuml.jar`** instead.

| Mode | How it works | Setup needed |
|------|-------------|---------------|
| **Online server** (default) | Encodes the diagram source and fetches an SVG from plantuml.com | None |
| **Local jar** | Runs `java -jar plantuml.jar -pipe -tsvg` in a background process per diagram block; injects the SVG directly into the page when ready | Configure in **Options → Tools** |

When local rendering is active:

- Each diagram is replaced temporarily by a *"⏳ Rendering diagram…"* placeholder
- Background threads render each block independently
- The **⚙ spinning gear** icon in the status bar is visible during rendering
- On completion the placeholders are replaced inline with the SVG (no page reload)
- If the local jar fails, the diagram falls back silently to the online server
- **SVG Cache:** Generated SVG images are cached in memory using a SHA-256 hash of the diagram source; unchanged diagrams are served instantly from cache without invoking the jar, significantly improving preview responsiveness during editing

See [Options → Tools Tab](#tools-tab) to configure the local jar.

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

Mermaid diagrams are rendered client-side in the preview panel. The Mermaid theme **automatically matches** your application theme: dark themes use the Mermaid `"dark"` theme, while light themes use `"default"`.

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

## Reading Mode

Reading Mode provides a distraction-free, fullscreen environment for reading and reviewing your documents without any editing UI in the way.

### Entering Reading Mode

| Method | Description |
|--------|-------------|
| **View → Enter Reading Mode** | Menu item in the View menu |
| `Ctrl+Shift+P` | Keyboard shortcut |

When reading mode is activated:

- The application goes **fullscreen**
- All side panels (Project Explorer, Tag Cloud, Network Diagram, LLM Chat) are **hidden**
- The **editor tab bar** is removed from the layout
- The **Preview panel** expands to fill the entire screen
- The **menu bar** is replaced by a thin bar containing only an **Exit Reading Mode** button
- The **Project Explorer** reappears as a compact **floating overlay** panel pinned to the top-left corner of the screen

### The Floating Project Explorer

In reading mode, the Project Explorer stays accessible as a floating panel so you can navigate between files without leaving the reader:

| Control | Description |
|---------|-------------|
| **▾ minimize button** | Collapses the panel to just its title bar, clearing the reading area |
| **▴ maximize button** | Restores the full panel after minimizing |

The **close (×)** and **detach (⇱)** buttons are hidden in reading mode — the panel is always visible and cannot be closed or detached while reading.

### Exiting Reading Mode

| Method | Description |
|--------|-------------|
| **Exit Reading Mode** button | Click the button in the top-right bar |
| `Escape` / exit fullscreen | Exiting fullscreen (F11 on Linux/Windows, `Cmd+Ctrl+F` on macOS) also exits reading mode |

When reading mode exits:

- All panels that were visible before are **restored** to their original docked positions
- The **split divider positions** (panel widths, editor/preview ratio) are fully restored to what they were before entering reading mode
- The editor tab bar and the menu bar return

---

## LLM Chat

MarkNote can connect to a local or remote Large Language Model and provide an integrated chat workflow alongside your notes.

![LLM Chat Panel](illustrations/llm-chat-panel.svg)

### What the Panel Does

The **LLM Chat** panel is designed for note drafting, reformulation, summarization, and content generation without leaving the editor.

It supports:

- **Streaming responses** while the model is generating text
- **Conversation history** for the current session
- **System context** to steer the assistant's behavior
- **Message actions** to copy, export, edit, or insert content into the active document
- **Session export** and **session insertion** into the active document
- **Cancellation** of the current request
- **Welcome message** — the panel displays a configuration summary as the first message each time a new session starts (endpoint, model, timeout, API type, system context status)
- **Markdown rendering** — assistant responses are displayed as rendered Markdown (headings, code blocks, bold/italic, tables…), making structured answers easy to read
- **Document context selection** — a bar above the prompt field lists all open documents as toggle buttons; selected documents are included as context when you send a prompt

### Supported Backends

MarkNote currently supports:

- **Ollama** endpoints such as `http://localhost:11434`
- **OpenAI-compatible chat endpoints** using the `/v1/chat/completions` format

The application automatically adapts the request format based on the configured endpoint URL.

### Enabling the Feature

1. Open **Help → Options...**
2. Go to the **LLM** tab
3. Check **Enable LLM panel**
4. Configure your endpoint, model, and optional API key
5. Click **OK**

If the feature is enabled, the panel becomes available in the interface and in **View → LLM Chat**.

### Configuring the Connection

The LLM tab lets you define:

| Option | Description |
|--------|-------------|
| **Enable LLM panel** | Shows or hides the LLM Chat panel in the main UI |
| **API Endpoint URL** | Base URL of your LLM service |
| **API Key** | Optional bearer token; usually not required for local Ollama |
| **Model** | Model identifier sent with each chat request |
| **Refresh Models** | Queries the server for available models (Ollama-compatible endpoint) |
| **Timeout** | Maximum request duration in seconds |
| **Default System Context** | Default instructions automatically prepended to each conversation |

Use **Test Connection** to validate the current settings before saving them.

### Sending a Prompt

1. Open the panel from **View → LLM Chat** if it is not already visible
2. Type your request in the prompt area
3. Press the **Send** button or `Ctrl+Enter`
4. Read the streamed answer as it appears in the conversation view

While the request is running:

- A spinner is displayed in the input area
- The prompt field is temporarily disabled
- The **Cancel** button replaces the Send button

### Document Context Selection

Above the prompt text area, a **document context bar** shows a compact toggle button for each open document tab. You can include one or more open documents as additional context for the model.

**How to use it:**

1. The bar appears automatically when at least one document tab is open
2. Click a document button to **toggle it on** (selected) — the button appears highlighted
3. Selected documents' full text is appended to your prompt before it is sent to the model
4. Click again to **deselect** a document and exclude it from the context
5. The selection is preserved across prompts in the same session and updated when tabs are opened or closed

> **Tip:** Use this feature to ask the LLM to summarize, compare, or cross-reference multiple notes without having to copy and paste their content manually.

### System Context

The **System Context** button in the input area opens a dialog where you can define instructions for the assistant, for example:

- Tone and style rules
- Output format constraints
- Writing goals for the current project

This context is saved in your LLM configuration and is automatically included in future requests.

### Working with Messages

Each conversation entry offers quick actions:

| Action | Description |
|--------|-------------|
| **Copy** | Copy the message content to the clipboard |
| **Export** | Save the message as a Markdown file |
| **Insert into document** | Insert the message content into the active document |
| **Edit** | Available on user prompts; reloads the prompt into the input area and removes later messages so you can regenerate from that point |

### Session Actions

The panel header also provides actions for the full conversation:

| Action | Description |
|--------|-------------|
| **Export Session** | Save the full conversation as a Markdown file |
| **Insert session into document** | Insert the entire conversation into the active document |
| **Clear Session** | Remove all messages from the current chat |

When a full session is inserted into a document, user prompts are prefixed with `>` so the exchange remains readable in Markdown.

### Typical Workflow

1. Open a Markdown note
2. Ask the assistant to summarize, rewrite, or expand your content
3. Review the streamed answer in the LLM Chat panel
4. Insert the whole answer or selected messages into the document
5. Continue editing directly in MarkNote

---

## Splash Screen & About

### Splash Screen

When MarkNote starts, a themed splash screen is displayed showing:

- The **application logo** (centered at the top)
- The application name and version
- Author and contact information
- Copyright notice

Click anywhere on the splash screen to dismiss it and continue to the main window.

The splash screen follows the current application theme (Light, Dark, Solarized, etc.). You can disable it in **Help → Options... → Misc. → Show splash screen on startup**.

### About Dialog

Access the same information at any time via **Help → About**. The About dialog displays the same content as the splash screen in a modal window with a **Close** button.

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
3. A copy of the currently selected theme is created (name sanitized to lowercase alphanumeric + hyphens)
4. The CSS file includes a header comment: `/* Custom Theme: name \n * Based on: basedOn */`
5. The **CSS theme editor** opens automatically (closes the options dialog)
6. Modify the CSS to customize colors and styles
7. Save the file (`Ctrl+S`) — if editing the current theme, the app theme **refreshes automatically**

Custom themes are stored in `~/.marknote/themes/`.

### Deleting Custom Themes

- In the Themes tab, select a custom theme and click **Delete Theme**
- Built-in themes cannot be deleted
- A confirmation dialog is shown before deletion

### Theme List Formatting

In the Themes options tab:

- **Italic** names = built-in themes
- **Bold** names = custom themes
- **Double-click** a custom theme to open it in the CSS editor

### CSS Theme Editor

The CSS theme editor provides a full editing experience with **syntax highlighting** for:

- Comments, strings, hex colors
- Numbers (with CSS units)
- Pseudo-classes, selectors, properties
- Braces and punctuation

Modification indicator (**\*** prefix) and save/close confirmation work the same as document tabs.

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
| **Reopen last project on startup** | Remember and reopen your last project (shows a confirmation dialog with the project name) |
| **Restore open documents on startup** | When reopening a project, reopen the documents that were open in the previous session (session stored in `.marknote` at the project root) |
| **Show Welcome page on startup** | Display the Welcome tab when starting |
| **Show splash screen on startup** | Display the splash screen when starting (enabled by default) |
| **Front matter expanded by default** | Whether the Front Matter panel is expanded when opening documents (default: true) |
| **Reattach diagram panel when tab closes** | When enabled, the Network Diagram returns to the side panel after closing its detached tab (default: true) |
| **Language** | Choose your preferred interface language (`system` follows OS locale) |

> **Note:** Changing the language **saves the configuration immediately** and **restarts the application**.

### Themes Tab

- View and select from available themes (italic = built-in, bold = custom)
- Create new custom themes based on existing ones
- Delete custom themes (built-in themes cannot be deleted)
- Double-click a custom theme to open it in the CSS editor

### Tools Tab

The **Tools** tab lets you configure external tools used by MarkNote.

#### PlantUML Local Jar

| Option | Description |
|--------|-------------|
| **Use local PlantUML jar** | Checkbox — when checked, MarkNote uses your local `plantuml.jar` instead of the online server for rendering diagrams in the preview |
| **PlantUML jar path** | Full path to your `plantuml.jar` file. Use the **Browse…** button to open a file selector filtered to `*.jar` |

**Steps to configure:**

1. Download `plantuml.jar` from [https://plantuml.com/download](https://plantuml.com/download)
2. Open **Help → Options…**
3. Select the **Tools** tab
4. Click **Browse…** and select your `plantuml.jar`
5. Check **Use local PlantUML jar**
6. Click **OK**

Once enabled:

- The status bar shows **● PlantUML: local jar** on the right side
- A **⚙ spinning gear** appears next to it while diagrams are being rendered
- The preview refreshes automatically to apply the new setting

> **Note:** Java must be on your system `PATH` since the jar is executed as `java -jar plantuml.jar`.

### LLM Tab

The **LLM** tab configures the integrated **LLM Chat** panel.

| Option | Description |
|--------|-------------|
| **Enable LLM panel** | Enables the feature and adds the panel to the main layout and View menu |
| **API Endpoint URL** | Base URL for your Ollama or OpenAI-compatible service |
| **API Key** | Optional bearer token used for authenticated services |
| **Model** | Model name sent with each request |
| **Refresh Models** | Fetches available models from an Ollama-compatible server |
| **Timeout (seconds)** | Request timeout used by the HTTP client |
| **Default System Context** | Global assistant instructions automatically prepended to each request |
| **Test Connection** | Sends a short test request to verify that the endpoint is reachable |

> **Note:** If you disable the panel here, **View → LLM Chat** is no longer available until the feature is re-enabled.

### Git Tab

The **Git** tab configures credentials used when the **Sync** operation communicates with a remote repository (push/pull).

#### SSH Authentication

| Option | Description |
|--------|-------------|
| **SSH key path** | Full path to your private SSH key (e.g. `~/.ssh/id_ed25519` or `~/.ssh/id_rsa`). Use **Browse…** to select the file. The key must have **no passphrase** (V1 limitation). |

#### HTTPS / Token Authentication

| Option | Description |
|--------|-------------|
| **Username** | The username passed to git. Typically `token` for GitHub or `oauth2` for GitLab |
| **Personal access token** | Your personal access token from GitHub / GitLab settings. Stored in `~/.marknote/config` |

> **Note:** Only one method is used per Sync. SSH is used when an SSH key path is configured; HTTPS token credentials are used otherwise. Both fields may be left empty if the remote requires no authentication (e.g., public repositories via SSH with your system key).

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
| `Ctrl+Shift+W` | Close all tabs |
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
| `Ctrl+F` | Open Search bar (search only) |
| `Ctrl+H` | Open Search & Replace bar |
| `Ctrl+Enter` | Send the current prompt from the LLM Chat input |

### Markdown Formatting (Editor)

| Shortcut | Action | Requires selection |
|----------|--------|-------------------|
| `Ctrl+B` | Bold (`**…**`) — toggle | Yes |
| `Ctrl+I` | Italic (`*…*`) — toggle | Yes |
| `Ctrl+K` | Insert link `[](selection)` | Yes |
| `Ctrl+J` | Insert image `![](selection)` | Yes |
| `Ctrl+E` | Insert fenced code block | Yes |
| `Ctrl+1` | Apply / toggle Heading H1 | No (current line) |
| `Ctrl+2` | Apply / toggle Heading H2 | No (current line) |
| `Ctrl+3` | Apply / toggle Heading H3 | No (current line) |
| `Ctrl+4` | Apply / toggle Heading H4 | No (current line) |
| `Ctrl+5` | Apply / toggle Heading H5 | No (current line) |
| `Ctrl+6` | Apply / toggle Heading H6 | No (current line) |

### Navigation

| Shortcut | Action |
|----------|--------|
| `Ctrl+Tab` | Next tab |
| `Ctrl+Shift+Tab` | Previous tab |
| `F5` | Refresh preview |

### View

| Shortcut | Action |
|----------|--------|
| `Ctrl+E` | Toggle Project Explorer |
| `Ctrl+P` | Toggle Preview panel |
| `Ctrl+T` | Toggle Tag Cloud |
| `Ctrl+L` | Toggle Network Diagram |
| `Ctrl+M` | Toggle LLM Chat |
| `Ctrl+Shift+P` | Enter Reading Mode |

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

#### LLM Chat does not respond

1. Open **Help → Options... → LLM** and verify the endpoint URL and model name
2. Use **Test Connection** to confirm the service is reachable
3. Check whether your backend requires an API key
4. If you are using Ollama locally, make sure the Ollama service is running
5. Increase the timeout if your model is slow to start or answer

### Getting Help

If you encounter issues not covered here:

1. Check the [GitHub repository](https://github.com/mcgivrer/marknote) for known issues
2. Submit a bug report with details about your system and the problem
3. Contact the author at <contact.snapgames@gmail.com>

---

## About MarkNote

**Version:** 0.1.5
**Author:** Frédéric Delorme  
**Copyright:** © SnapGames 2026  
**License:** MIT  
**Repository:** <https://github.com/mcgivrer/marknote>

---

*This documentation is part of the MarkNote project. Last updated: May 2026.*
