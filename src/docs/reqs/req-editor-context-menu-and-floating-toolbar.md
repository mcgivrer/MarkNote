# Editor Context Menu

## Goal

Add a contextual menu (right-click) directly on the `StyleClassedTextArea editor` field inside `DocumentTab`.  
The menu must reflect the common Markdown formatting actions and provide keyboard shortcut equivalents.

![The Contextual Menu and Floating toolbar](../illustrations/editor-context-menu-and-toolbar.svg "The Contextual Menu and Floating toolbar")

## Implementation location

- **Class**: `ui/DocumentTab.java`
- **Target component**: `StyleClassedTextArea editor` (RichTextFX)
- **Attachment**: `editor.setContextMenu(createEditorContextMenu())` — same pattern as `ProjectExplorerPanel.createContextMenu()`.
- **Keyboard shortcuts**: extend the existing `editor.setOnKeyPressed(...)` handler (currently handling `Ctrl+F` and `Ctrl+H`).

---

## Menu structure

```
Copy          Ctrl+C
Cut           Ctrl+X
Paste         Ctrl+V
──────────────────────
Title H1      Ctrl+1
Title H2      Ctrl+2
Title H3      Ctrl+3
Title H4      Ctrl+4
Title H5      Ctrl+5
Title H6      Ctrl+6
──────────────────────
Bold          Ctrl+B
Italic        Ctrl+I
──────────────────────
Insert link   Ctrl+K
Insert image  Ctrl+J
```

---

## Behaviour per entry

### Copy / Cut / Paste

- Delegate directly to `editor.copy()`, `editor.cut()`, `editor.paste()`.
- These are standard JavaFX clipboard operations — no custom logic required.
- **Keyboard shortcuts**: OS-native (`Ctrl+C`, `Ctrl+X`, `Ctrl+V`) are already handled by RichTextFX; the menu items only need to call the API methods.

### Title H1 – H6 (`Ctrl+1` … `Ctrl+6`)

- Act on the **current line** (no selection required).
- Algorithm:
  1. Retrieve the paragraph index at the caret: `editor.getCurrentParagraph()`.
  2. Get the current paragraph text: `editor.getParagraph(idx).getText()`.
  3. Strip any existing leading `#` characters and the optional trailing space.
  4. Prepend the appropriate prefix (`#`, `##`, … `######`) followed by a space.
  5. Replace the paragraph in-place using `editor.replaceText(start, end, newLine)`.
- If the line already has the same heading level, toggle it off (remove the prefix).

### Bold (`Ctrl+B`)

- Requires a **non-empty selection**.
- Wrap the selected text: `**<SELECTION>**`.
- Place the caret at the end of the wrapped text after insertion.
- If the selection is already surrounded by `**`, unwrap it instead (toggle behaviour).
- Item is **disabled** in the context menu when `editor.getSelection().getLength() == 0`.

### Italic (`Ctrl+I`)

- Same toggle logic as Bold, using single asterisks: `*<SELECTION>*`.
- Item is **disabled** when no text is selected.

### Insert link (`Ctrl+K`)

- Requires a **non-empty selection** which becomes the URL part of the link.
- Inserts: `[<CURSOR>](<TEXT_SELECTED>)` where `<CURSOR>` marks where the caret lands after insertion (i.e., between `[` and `]`).
- Concrete steps:
  1. Read `String url = editor.getSelectedText()`.
  2. Replace selection with `"[]("+url+")"`.
  3. Move caret to position `selectionStart + 1` (inside the brackets).
- Item is **disabled** when no text is selected.

> [!WARNING]
> **Keyboard conflict**: `Ctrl+H` is already bound in `DocumentTab` to open the Search & Replace bar (`searchReplaceBar.showSearchAndReplace()`).  
> The original spec proposed `Ctrl+H` for *Insert link* — this would break the search feature.  
> **Decision**: use `Ctrl+K` for *Insert link* (consistent with VS Code, IntelliJ, Typora).  
> The `Ctrl+H` binding for Search & Replace is kept unchanged.

### Insert image (`Ctrl+J`)

- Same logic as *Insert link* but with the image syntax.
- Inserts: `![<CURSOR>](<TEXT_SELECTED>)` — caret lands at position `selectionStart + 2` (inside `![` … `]`).
- Item is **disabled** when no text is selected.

---

## Conditional item enabling

Set the menu's `onShowing` handler to enable/disable items depending on context:

```java
contextMenu.setOnShowing(e -> {
    boolean hasSelection = editor.getSelection().getLength() > 0;
    boldItem.setDisable(!hasSelection);
    italicItem.setDisable(!hasSelection);
    insertLinkItem.setDisable(!hasSelection);
    insertImageItem.setDisable(!hasSelection);
    // Copy/Cut also require a selection
    copyItem.setDisable(!hasSelection);
    cutItem.setDisable(!hasSelection);
});
```

---

## Internationalisation

Add the following keys to every `messages_*.properties` file:

```properties
# Editor context menu
editor.menu.copy=Copy
editor.menu.cut=Cut
editor.menu.paste=Paste
editor.menu.heading=Title H{0}
editor.menu.bold=Bold
editor.menu.italic=Italic
editor.menu.insertLink=Insert link
editor.menu.insertImage=Insert image
```

---

## Floating toolbar (later)

> [!NOTE]
> In a subsequent iteration, a floating toolbar will appear **above the selection** whenever the user selects text in the editor.  
> It will expose the same formatting actions (Bold, Italic, Insert link, Insert image, heading level picker) as icon buttons or a combo-box, without requiring the context menu.  
> Implementation hint: listen to `editor.selectedTextProperty()` changes; when selection becomes non-empty, compute the screen coordinates of the selection start via `editor.getCharacterBoundsOnScreen(start, end)` and position a `Popup` node accordingly.

---

## Implementation plan

### Phase 1 — Context Menu

**Scope**: `ui/DocumentTab.java` only. No new class required.

#### Step 1 — Markdown formatting helpers

Extract the formatting logic into private helper methods **before** building the menu, so they can later be reused by the floating toolbar.

| Method              | Signature                        | Notes                                  |
|---------------------|----------------------------------|----------------------------------------|
| `applyHeading`      | `void applyHeading(int level)`   | Toggle heading on current paragraph    |
| `toggleWrap`        | `void toggleWrap(String marker)` | Toggle `**`, `*`, etc. on selection    |
| `insertLinkSyntax`  | `void insertLinkSyntax()`        | Insert `[](url)` with caret placement  |
| `insertImageSyntax` | `void insertImageSyntax()`       | Insert `![](url)` with caret placement |

#### Step 2 — `createEditorContextMenu()` method

Add a private method `createEditorContextMenu()` in `DocumentTab`, following the exact same pattern as `ProjectExplorerPanel.createContextMenu()`:

```java
private ContextMenu createEditorContextMenu() {
    ResourceBundle msg = getMessages();

    MenuItem copyItem    = new MenuItem(msg.getString("editor.menu.copy"));
    MenuItem cutItem     = new MenuItem(msg.getString("editor.menu.cut"));
    MenuItem pasteItem   = new MenuItem(msg.getString("editor.menu.paste"));

    // H1–H6 items built in a loop
    MenuItem[] headingItems = new MenuItem[6];
    for (int i = 1; i <= 6; i++) {
        final int level = i;
        headingItems[i-1] = new MenuItem(
            MessageFormat.format(msg.getString("editor.menu.heading"), level));
        headingItems[i-1].setOnAction(e -> applyHeading(level));
    }

    MenuItem boldItem        = new MenuItem(msg.getString("editor.menu.bold"));
    MenuItem italicItem      = new MenuItem(msg.getString("editor.menu.italic"));
    MenuItem insertLinkItem  = new MenuItem(msg.getString("editor.menu.insertLink"));
    MenuItem insertImageItem = new MenuItem(msg.getString("editor.menu.insertImage"));

    copyItem.setOnAction(e    -> editor.copy());
    cutItem.setOnAction(e     -> editor.cut());
    pasteItem.setOnAction(e   -> editor.paste());
    boldItem.setOnAction(e    -> toggleWrap("**"));
    italicItem.setOnAction(e  -> toggleWrap("*"));
    insertLinkItem.setOnAction(e  -> insertLinkSyntax());
    insertImageItem.setOnAction(e -> insertImageSyntax());

    ContextMenu menu = new ContextMenu();
    menu.getItems().addAll(
        copyItem, cutItem, pasteItem,
        new SeparatorMenuItem(),
        headingItems[0], headingItems[1], headingItems[2],
        headingItems[3], headingItems[4], headingItems[5],
        new SeparatorMenuItem(),
        boldItem, italicItem,
        new SeparatorMenuItem(),
        insertLinkItem, insertImageItem
    );

    menu.setOnShowing(e -> {
        boolean hasSel = editor.getSelection().getLength() > 0;
        copyItem.setDisable(!hasSel);
        cutItem.setDisable(!hasSel);
        boldItem.setDisable(!hasSel);
        italicItem.setDisable(!hasSel);
        insertLinkItem.setDisable(!hasSel);
        insertImageItem.setDisable(!hasSel);
    });

    return menu;
}
```

#### Step 3 — Attach menu and keyboard shortcuts

In the `DocumentTab` constructor, after the editor is created:

```java
editor.setContextMenu(createEditorContextMenu());
```

Extend the existing `editor.setOnKeyPressed(...)` handler with:

```java
} else if (e.isControlDown() && e.getCode().isDigitKey()) {
    int level = e.getCode().ordinal() - KeyCode.DIGIT1.ordinal() + 1;
    if (level >= 1 && level <= 6) { applyHeading(level); e.consume(); }
} else if (e.isControlDown() && e.getCode() == KeyCode.B) {
    toggleWrap("**"); e.consume();
} else if (e.isControlDown() && e.getCode() == KeyCode.I) {
    toggleWrap("*"); e.consume();
} else if (e.isControlDown() && e.getCode() == KeyCode.K) {
    insertLinkSyntax(); e.consume();
} else if (e.isControlDown() && e.getCode() == KeyCode.J) {
    insertImageSyntax(); e.consume();
}
```

#### Step 4 — i18n keys

Add the 8 keys listed in the [Internationalisation](#internationalisation) section to all 6 `messages_*.properties` files.

#### Step 5 — Unit tests

Add tests to `test/java/ui/DocumentTabContextMenuTest.java` (new file):

| Test                           | Scenario                                                |
|--------------------------------|---------------------------------------------------------|
| `testApplyHeading_noExisting`  | Caret on plain line → `# line`                          |
| `testApplyHeading_toggle`      | Caret on `# line` → `line`                              |
| `testToggleWrap_bold`          | Selection `foo` → `**foo**`                             |
| `testToggleWrap_bold_untoggle` | Selection `**foo**` → `foo`                             |
| `testInsertLinkSyntax`         | Selection `https://x` → `[](https://x)`, caret at pos 1 |
| `testInsertImageSyntax`        | Selection `img.png` → `![](img.png)`, caret at pos 2    |

---

### Phase 2 — Floating Toolbar

**Scope**: new class `ui/EditorFloatingToolbar.java` + minor integration in `DocumentTab`.

#### Step 1 — Create `EditorFloatingToolbar`

New class `ui/EditorFloatingToolbar.java` extending `javafx.stage.Popup`:

- Constructor takes a reference to the `StyleClassedTextArea` and a callback map `Map<String, Runnable> actions` (keyed by `"bold"`, `"italic"`, `"link"`, `"image"`, `"h1"`…`"h6"`).
- Builds a dark pill-shaped `HBox` (`background: #1e293b`, `border-radius: 17px`) containing `Button` nodes:
  - **B** (bold), **I** (italic), 🔗 (link), 🖼 (image), then **H1 H2 H3** and a `MenuButton` for **H4–H6**.
- Each button fires the corresponding `Runnable` from the action map.
- Provides `show(double screenX, double screenY)` and `hide()` methods.
- CSS class `.editor-floating-toolbar` added to the root `HBox` for theming.

#### Step 2 — Integration in `DocumentTab`

After the editor is initialised, instantiate the toolbar with the same action map as the context menu:

```java
Map<String, Runnable> toolbarActions = Map.of(
    "bold",   () -> toggleWrap("**"),
    "italic", () -> toggleWrap("*"),
    "link",   this::insertLinkSyntax,
    "image",  this::insertImageSyntax,
    "h1", () -> applyHeading(1),
    // … h2–h6
);
EditorFloatingToolbar floatingToolbar = new EditorFloatingToolbar(editor, toolbarActions);
```

Listen to selection changes to show/hide the toolbar:

```java
editor.selectionProperty().addListener((obs, oldSel, newSel) -> {
    if (newSel.getLength() > 0) {
        editor.getCharacterBoundsOnScreen(newSel.getStart(), newSel.getEnd())
              .ifPresent(bounds -> floatingToolbar.show(
                  bounds.getMinX() + bounds.getWidth() / 2,
                  bounds.getMinY() - 8   // 8 px above the selection
              ));
    } else {
        floatingToolbar.hide();
    }
});
```

Hide the toolbar also when the editor loses focus:

```java
editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
    if (!isFocused) floatingToolbar.hide();
});
```

#### Step 3 — CSS

Add `.editor-floating-toolbar` rule to `src/main/resources/css/markdown-editor.css`:

```css
.editor-floating-toolbar {
    -fx-background-color: #1e293b;
    -fx-background-radius: 17px;
    -fx-padding: 4px 8px;
    -fx-spacing: 4px;
}

.editor-floating-toolbar .button {
    -fx-background-color: transparent;
    -fx-text-fill: #cbd5e1;
    -fx-font-size: 11px;
    -fx-background-radius: 12px;
    -fx-padding: 3px 7px;
    -fx-cursor: hand;
}

.editor-floating-toolbar .button:hover {
    -fx-background-color: #3b82f6;
    -fx-text-fill: #ffffff;
}
```

#### Step 4 — Unit tests

Add tests to `test/java/ui/EditorFloatingToolbarTest.java` (new file):

| Test                                | Scenario                                            |
|-------------------------------------|-----------------------------------------------------|
| `testToolbarShownOnSelection`       | Non-empty selection → toolbar `isShowing() == true` |
| `testToolbarHiddenOnClearSelection` | Selection cleared → toolbar `isShowing() == false`  |
| `testToolbarHiddenOnFocusLost`      | Editor loses focus → toolbar hidden                 |
| `testBoldButtonFiresAction`         | Click Bold button → action Runnable called          |
