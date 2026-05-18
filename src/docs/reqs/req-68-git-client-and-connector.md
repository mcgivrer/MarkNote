# Git client and connector

## Context

The Project Explorer already displays per-file Git status indicators (green = tracked, orange = modified, red = untracked). The next step is to expose the full Git workflow directly inside MarkNote — initialising repositories, staging files, committing, and syncing with a remote — without requiring the user to leave the editor.

Git operations are delegated to **JGit** (Eclipse JGit library), embedded as a Maven dependency. JGit is a pure-Java implementation of the Git core, which avoids any dependency on a system-installed `git` binary and gives full programmatic control over every operation.

All Git logic is encapsulated in a dedicated **`GitService`** class (see [Architecture](#architecture) below), keeping the UI layer thin.

## Illustration

![Project Explorer — Git UI](../illustrations/project-explorer-git.svg)

The illustration shows:

- **Toolbar** (top): three new git buttons (↓ Pull, ↑ Push, ✓ Commit) prepended before the existing ⇅ Sync and ↻ Index buttons; a dark branch badge (⎇ main) on the right shows the currently checked-out branch. The new buttons are only rendered when the open project folder is a Git repository.
- **File tree**: coloured status dots on the right edge of each row — green (tracked/staged), orange (modified), red (untracked).
- **Right-click context menu** (right panel): available on any file in the tree; `+ git add` is highlighted as the primary action for untracked files.
- **Root folder callout** (bottom): shows the additional entries that appear only when right-clicking the project root and Git has not been initialised yet.

## Proposal

### 1. Git repository initialisation

When the user opens a project folder that does **not** contain a `.git/` sub-directory, the Project Explorer toolbar shows a single `⚙ Init Git…` button instead of the Pull/Push/Commit group. Clicking it:

1. Calls `GitService.init(projectPath)` which runs `git init` via JGit, creating `.git/` and setting the default branch to `main`.
2. Immediately proceeds to identity setup (see §2).
3. Once identity is confirmed, proposes to stage and commit all existing project files (see §3).

Alternatively, right-clicking the **root folder** in the tree always exposes `⚙ Initialize Git repository…` at the top of the context menu when no `.git/` is present.

### 2. Git user identity setup

After initialisation (or when performing a first commit on an existing repo that has no identity configured), `GitService` checks — in order — the local repo config (`<repo>/.git/config`) and the global config (`~/.gitconfig`) for `user.name` and `user.email`.

If either value is missing, a small modal dialog prompts the user to provide both. The values are written to the **local** repo config only (`git config --local user.name …`), leaving the global config untouched unless the user explicitly checks an *"Apply globally"* option.

![Git Identity dialog](../illustrations/git-identity-dialog.svg)

### 3. Initial add + commit

Immediately after a successful `git init` + identity setup, a confirmation dialog lists all non-ignored files in the project directory and asks:

> *"Add all files and create an initial commit?"*

- **Confirm**: `GitService.addAll()` stages every file, then `GitService.commit("Initial commit")` creates the first commit.
- **Cancel**: the repo is left empty; the user can stage files manually later via the context menu.

### 4. Remote repository setup

The first time the user clicks **↑ Push** (or selects `↑ Push` from the context menu) when no remote is configured, an **Add Remote** dialog appears:

| Field                | Description                                                                                  |
|----------------------|----------------------------------------------------------------------------------------------|
| Remote URL           | HTTPS or SSH URL (e.g. `https://github.com/user/repo.git` or `git@github.com:user/repo.git`) |
| Authentication       | Selector: **None / Basic / Token / SSH key**                                                 |
| Username             | Visible for Basic and Token auth                                                             |
| Password / Token     | Visible for Basic and Token auth; masked input                                               |
| SSH private key path | Visible for SSH auth; file-picker                                                            |
| SSH passphrase       | Visible for SSH auth; optional, masked                                                       |

Credentials are stored in `~/.marknote/config` (plain-text, owner-readable only, `chmod 600`). The remote is registered as `origin` in the local repo config. After the remote is saved the push is retried automatically.

> [!IMPORTANT] No support for branching or merging is proposed for now. At repo initialisation the default branch `main` is used, and all sync operations target the currently checked-out branch.

## UI changes

### Toolbar buttons (new)

Three buttons are prepended to the existing toolbar and are only visible when the open project is a Git repository:

| Button     | Shortcut | Action                                                                  |
|------------|----------|-------------------------------------------------------------------------|
| `↓ Pull`   | —        | Fetch + fast-forward merge from `origin/<branch>`                       |
| `↑ Push`   | —        | Push local commits to `origin/<branch>`; prompts Add Remote if none set |
| `✓ Commit` | —        | Opens the Commit dialog                                                 |

A read-only **branch badge** (dark pill, e.g. `⎇ main`) appears at the right end of the toolbar, showing the current branch name. It is refreshed whenever a commit, pull, or push completes.

### Context menu — files

Right-clicking any file in the tree appends a **Git** section (preceded by a separator) to the existing file context menu:

| Entry                 | Condition                     | Action                                            |
|-----------------------|-------------------------------|---------------------------------------------------|
| `+ git add`           | File is untracked or modified | Stages the file (`git add <path>`)                |
| `✓ Commit…`           | Always                        | Opens Commit dialog with this file pre-selected   |
| `↓ Pull`              | Repo has a remote             | Pull from origin                                  |
| `↑ Push`              | Repo has a remote             | Push to origin                                    |
| `↺ Fetch`             | Repo has a remote             | Fetch from origin (no merge)                      |
| `✗ Remove from index` | File is tracked               | Unstages / removes from index (`git rm --cached`) |

### Context menu — root folder

Right-clicking the **root folder** node shows the same Git section as for files, plus two entries at the top when the project is not yet a Git repository:

| Entry                          | Condition                              |
|--------------------------------|----------------------------------------|
| `⚙ Initialize Git repository…` | No `.git/` present                     |
| `⊕ Add Remote…`                | Repo exists but has no `origin` remote |

### File status indicators

Status dots are rendered as small coloured circles on the right edge of each tree row (as currently):

| Colour | Meaning                              |
|--------|--------------------------------------|
| Green  | Tracked and up to date (clean)       |
| Orange | Tracked but locally modified (dirty) |
| Red    | Untracked (not in the index)         |

The dots are refreshed asynchronously after every Git operation and after every file save.

## Supported operations

### `git fetch`

Downloads new objects and refs from `origin` without modifying the working tree or the current branch. Used to inspect remote changes before deciding to pull.

### `git pull`

Equivalent to `git fetch` followed by `git merge --ff-only`. If the fast-forward fails (diverged history), the operation is aborted and the user is notified with a clear message; no merge commit is created automatically.

### `git add`

Stages one file (from the context menu) or all files (from the Commit dialog's *Stage all* button). Uses JGit's `AddCommand`.

### `git commit`

Opens the **Commit dialog** (see [Dialogs](#dialogs)). The user writes a commit message, reviews staged files, and confirms. The commit is local only; a subsequent Push is required to publish it.

### `git push`

Pushes all local commits on the current branch to `origin/<branch>`. If the remote has diverged, the push is rejected and the user is prompted to pull first.

## Dialogs

### Commit dialog

A modal dialog containing:

- **Commit message** — multi-line text area (min 3 rows); the first line is used as the commit title.
- **Staged files** — a read-only list of files currently in the index (`git diff --cached --name-only`), each with its status letter (A = added, M = modified, D = deleted).
- **Stage all** button — runs `git add .` and refreshes the staged list.
- **Confirm** / **Cancel** buttons.

The dialog validates that the message is non-empty and that at least one file is staged before enabling Confirm.

### Add Remote / Credentials dialog

A single-page modal with the fields listed in §4 (Proposal). The auth type selector shows/hides the relevant credential fields dynamically. A **Test Connection** button optionally calls `git ls-remote <url>` to verify connectivity before saving.

![Add Remote / Credentials dialog — Token auth (left) vs SSH key auth (right)](../illustrations/git-add-remote-dialog.svg)

## Architecture

### `GitService` class

A singleton service (instantiated by `MarkNote` at startup, injected where needed) that wraps JGit's `Git` API:

```java
public class GitService {
    public boolean isGitRepo(Path projectPath);
    public void init(Path projectPath) throws GitAPIException;
    public GitStatus status() throws GitAPIException;          // per-file status map
    public void add(Path file) throws GitAPIException;
    public void addAll() throws GitAPIException;
    public void commit(String message) throws GitAPIException;
    public void fetch() throws GitAPIException;
    public void pull() throws GitAPIException;
    public void push(CredentialsProvider creds) throws GitAPIException;
    public void addRemote(String url) throws GitAPIException;
    public String currentBranch() throws IOException;
}
```

The service fires JavaFX property-change events after each mutating operation so that the Project Explorer and status badge can update themselves on the UI thread.

### `RemoteConnector` interface

An optional extension point for platform-specific integrations (GitHub, Gitea, GitLab REST APIs):

```java
public interface RemoteConnector {
    String platform();                     // "github", "gitea", "gitlab"
    List<RemoteRepo> listRepositories();
    void createRepository(String name, boolean isPrivate);
}
```

Implementing this interface later will enable features like browsing remote repositories, creating a repo from within MarkNote, or retrieving CI status — without touching `GitService`.

### Diagramme UML

```mermaid
classDiagram
    class GitService {
        -Git jgit
        -Path projectPath
        -ObjectProperty~GitStatus~ statusProperty
        +isGitRepo(Path) boolean
        +init(Path) void
        +status() GitStatus
        +add(Path) void
        +addAll() void
        +commit(String message) void
        +fetch() void
        +pull() void
        +push(CredentialsProvider) void
        +addRemote(String url) void
        +currentBranch() String
        +statusProperty() ObjectProperty~GitStatus~
    }

    class RemoteConnector {
        <<interface>>
        +platform() String
        +listRepositories() List~RemoteRepo~
        +createRepository(String, boolean) void
    }

    class GitHubConnector {
        -String token
        +platform() String
        +listRepositories() List~RemoteRepo~
        +createRepository(String, boolean) void
    }

    class GitLabConnector {
        -String token
        -String instanceUrl
        +platform() String
        +listRepositories() List~RemoteRepo~
        +createRepository(String, boolean) void
    }

    class GiteaConnector {
        -String token
        -String instanceUrl
        +platform() String
        +listRepositories() List~RemoteRepo~
        +createRepository(String, boolean) void
    }

    class GitStatus {
        +Map~Path, FileStatus~ files
        +boolean hasUncommitted
        +boolean hasUnpushed
    }

    class RemoteRepo {
        +String name
        +String cloneUrl
        +boolean isPrivate
    }

    GitService --> GitStatus : produces
    GitService o-- RemoteConnector : optional
    RemoteConnector <|.. GitHubConnector : implements
    RemoteConnector <|.. GitLabConnector : implements
    RemoteConnector <|.. GiteaConnector : implements
    RemoteConnector --> RemoteRepo : returns
```

## Options — Git configuration tab

A dedicated **Git** tab is added to the Options dialog (`Help → Options… → Git`). It contains two sections.

### Toolbar mode

A radio-button selector controls which Git controls are displayed in the Project Explorer toolbar:

| Mode         | Description                                                                                                                                                                                                                                                                               |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Standard** | Only the existing `⇅ Sync` and `↻ Index` buttons are shown. The branch badge is displayed but is read-only (label only, no interaction). This is the default mode; it is appropriate for users who do not need direct Git interaction from within MarkNote.                               |
| **Advanced** | All Git buttons are active: `↓ Pull`, `↑ Push`, `✓ Commit`, `↻ Index`, `⇅ Sync`, and the `⎇ <branch>` badge. Selecting the branch badge opens a read-only dropdown listing existing local branches; switching branches is intentionally not supported (no branching operations in scope). |

> [!IMPORTANT] IMPORTANT
> Even in **Standard** mode the per-file Git status dots (green / orange / red) and the root-folder context-menu entries (Init, Add Remote…) remain visible and functional. The mode only governs the toolbar buttons.

The selected mode is persisted in `~/.marknote/config` under the key `git.toolbarMode` with values `standard` (default) or `advanced`.

### Remote credentials

This section regroups the credentials fields described in §4 (Proposal / Remote repository setup):

- Saved remote URL
- Authentication type selector (None / Basic / Token / SSH key)
- Username, Password/Token, SSH key path, SSH passphrase (shown according to auth type)
- **Test Connection** button

Credentials are stored in `~/.marknote/config` with `chmod 600`.

## Dependencies

Add **JGit** to `pom.xml`:

```xml
<dependency>
    <groupId>org.eclipse.jgit</groupId>
    <artifactId>org.eclipse.jgit</artifactId>
    <version>7.1.0.202411261347-r</version>
</dependency>
```

No additional native binaries are required. JGit bundles all transitive dependencies and runs on any platform supported by MarkNote.

## ToDo

1. Stage 1 - GitService

    - [x] Use plan mode to prepare the implementation,
    - [x] wait for plan approval before proceeding,
    - [x] write a dedicated specification in `src/docs/git-client-and-connector-implementation.md`,
    - [x] add JGit dependency to `pom.xml`,
    - [x] implement `GitService` with all operations listed above,
    - [x] update Project Explorer toolbar (Pull / Push / Commit buttons + branch badge),
    - [x] add Git section to file and root-folder context menus,
    - [x] implement Commit dialog,
    - [x] implement Add Remote / Credentials dialog,
    - [x] wire status dot refresh to post-operation events,
    - [x] add Git tab in Options dialog (toolbar mode: standard / advanced; remote credentials).

2. Stage 2 - git RemoteConnector

    **Goal**: Implement connectors for interacting with GitHub, GitLab, and Gitea REST APIs to list repositories and create new remote repositories directly from MarkNote.

    **Architecture decisions**:
    - Use existing `java.net.http.*` HttpClient (already used in UpdateChecker)
    - Parse JSON responses with `org.json.simple` (already in dependencies for LLM config)
    - Token-based authentication only (credentials already stored in AppConfig)
    - Return empty lists on API errors, log via LogService
    - Static factory for platform detection from remote URLs

    **Phase 1: Core Infrastructure** (parallel steps)

    - [ ] Create `RemoteConnector` interface in `src/main/java/utils/RemoteConnector.java`
        - Define methods: `platform()`, `listRepositories()`, `createRepository(String name, boolean isPrivate)`
        - Define `RemoteRepo` record: `name`, `cloneUrl`, `description`, `isPrivate`, `defaultBranch`
        - Add JavaDoc describing contract and authentication expectations

    - [ ] Create `RemoteConnectorFactory` utility in `src/main/java/utils/RemoteConnectorFactory.java`
        - Static method `create(String remoteUrl, String token)` → returns appropriate connector or null
        - URL parsing logic to detect platform (github.com, gitlab.com, custom domains)
        - Support for custom GitLab and Gitea instances

    **Phase 2: Platform Implementations** (parallel steps)

    - [ ] Implement `GitHubConnector` in `src/main/java/utils/GitHubConnector.java`
        - Constructor: `GitHubConnector(String token)`
        - API base: `https://api.github.com`
        - `listRepositories()`: GET `/user/repos?type=owner&per_page=100`
        - `createRepository()`: POST `/user/repos` with JSON body `{"name": "...", "private": true/false}`
        - Auth header: `Authorization: token <token>`

    - [ ] Implement `GitLabConnector` in `src/main/java/utils/GitLabConnector.java`
        - Constructor: `GitLabConnector(String instanceUrl, String token)`
        - Support custom instances (default: `https://gitlab.com`)
        - API base: `<instanceUrl>/api/v4`
        - `listRepositories()`: GET `/projects?owned=true&per_page=100`
        - `createRepository()`: POST `/projects` with JSON body `{"name": "...", "visibility": "private"/"public"}`
        - Auth header: `PRIVATE-TOKEN: <token>`

    - [ ] Implement `GiteaConnector` in `src/main/java/utils/GiteaConnector.java`
        - Constructor: `GiteaConnector(String instanceUrl, String token)`
        - Support custom instances only (no default, user provides URL)
        - API base: `<instanceUrl>/api/v1`
        - `listRepositories()`: GET `/user/repos`
        - `createRepository()`: POST `/user/repos` with JSON body `{"name": "...", "private": true/false}`
        - Auth header: `Authorization: token <token>`

    **Phase 3: Testing & Error Handling**

    - [ ] Create unit tests in `src/test/java/utils/`
        - `RemoteConnectorFactoryTest`: URL parsing, platform detection
        - Mock HTTP responses for connector tests
        - Test authentication header generation
        - Test JSON parsing for repository lists

    - [ ] Add error handling and logging
        - Wrap HTTP exceptions with user-friendly messages
        - Log API errors via `LogService` with source "RemoteConnector"
        - Handle common errors: 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Rate Limited
        - Return empty lists on error (consistent with GitService async patterns)

    **Verification steps**:
    - Manual test: Create connector instances with valid tokens, verify `listRepositories()` returns expected repos
    - Manual test: Call `createRepository("test-repo", true)` on each platform, verify repo created
    - Unit tests: `mvn test -Dtest=RemoteConnectorFactoryTest`
    - Integration check: Factory correctly identifies platforms from various URL formats

    **Design decisions**:
    - **JSON library**: Use `org.json.simple` (already in classpath)
    - **Custom domains**: Auto-detect gitlab.com, require explicit URL for self-hosted instances
    - **Error handling**: Return empty lists + log errors (no UI exceptions)
    - **Factory pattern**: Static utility (no caching, connectors are lightweight)
    - **Scope**: Repository operations only; no issues, PRs, CI status (deferred to future)

3. stage 3 - UI integration
    - [ ] integrate the RemoteConnector usage into git dialogs and UI.

> [!IMPORTANT] GitService class
> The Git client is a big feature; create it as a dedicated `GitService` class and keep all JGit calls inside it. The UI layer must never import JGit directly.

> [!NOTE] Git connector implementation
> The `RemoteConnector` interface is intentionally left unimplemented in the first iteration. It opens the door to future GitHub / Gitea / GitLab integrations without requiring any rework of `GitService`.
