# Complexity Evaluation — Add Git Support

> 2026-02-25

## Sub-tasks

| #   | Task                                  | Effort | Notes                                                                                                                                                                                                              |
| --- | ------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1   | **`GitService.java`** (new)           | 2–3 h  | Detect `.git/`, run `git status --porcelain`, parse into `Map<String, GitStatus>`, run pull/push with env vars, background refresh thread                                                                          |
| 2   | **ProjectExplorer visual indicators** | 2–3 h  | Custom `TreeCell` with `StackPane` + colored `Circle` overlay; toolbar with pull/push buttons shown conditionally; re-render on status update                                                                      |
| 3   | **Credential subprocess wiring**      | 2–3 h  | **Hardest part** — `GIT_ASKPASS` requires a temp executable shell script; SSH passphrase can't be passed via simple env var without `sshpass` or `ssh-agent`; `GIT_TERMINAL_PROMPT=0` to block interactive prompts |
| 4   | **OptionsDialog "Git" tab**           | 1–2 h  | SSH path + browse + passphrase (masked), username/password fields, token field, save to AppConfig                                                                                                                  |
| 5   | **AppConfig** new fields              | 0.5 h  | 5–6 new fields; security concern: passwords/passphrases stored in plain text in `~/.marknote/config`                                                                                                               |
| 6   | **MarkNote.java** wiring              | 0.5 h  | Init GitService on project open, wire pull/push button callbacks                                                                                                                                                   |
| 7   | **i18n** (6 files)                    | 0.5 h  | Toolbar labels, pull/push feedback messages, credentials tab labels                                                                                                                                                |
| 8   | **Testing & error handling**          | 2 h    | Auth failures, no upstream branch, merge conflicts on pull (no spec for conflict handling)                                                                                                                         |

**Total estimated effort: ~11–15 h**

---

## Key risks

The PlantUML JAR implementation only required a `ProcessBuilder` with no authentication. Git authentication is significantly more complex:

### `GIT_ASKPASS`
Git calls this as an executable to retrieve credentials. A temp shell script must be written at runtime:
```sh
#!/bin/sh
echo "$GIT_PASSWORD"
```
It must be made executable (`chmod +x`) before the git subprocess is launched. On Windows a `.bat` equivalent is needed — cross-platform handling adds friction.

### SSH passphrase
There is no standard environment variable for passing an SSH key passphrase. Options are:
- `sshpass -p` — not always installed, considered insecure
- Key without passphrase — most common dev workflow, simplest to support
- `expect` script wrapping `ssh-agent` — fragile and complex

### Conflict handling on pull
Completely unspecified in the current TODO. A `git pull` that produces merge conflicts leaves the repo in a dirty state with no user guidance. At minimum, the error output must be surfaced in a dialog.

### Credential security
Plain-text storage of passwords and tokens in `~/.marknote/config` is a weak point. Acceptable for a personal tool, but worth documenting.

---

## Recommended simplification for v1

Limit the initial implementation to reduce scope and risk:

1. **SSH only with passphrase-less keys** — eliminates the `GIT_ASKPASS` complexity entirely; covers the most common developer workflow.
2. **Personal access token as HTTP password** (`GIT_ASKPASS` script echoing the token) — covers GitHub/GitLab HTTPS workflows.
3. **Conflict handling**: on pull failure, show the raw `git` stderr output in a modal dialog; no merge resolution UI.

This cuts credential wiring from 2–3 h to ~1 h and defers `sshpass`/passphrase support to a later iteration, bringing the total to **~8–10 h**.
