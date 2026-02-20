# MarkNote

[![Java build](https://github.com/mcgivrer/MarkNote/actions/workflows/java-build.yml/badge.svg)](https://github.com/mcgivrer/MarkNote/actions/workflows/java-build.yml)
![Java](https://img.shields.io/badge/Java-25-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-24-blue)
![License](https://img.shields.io/badge/License-MIT-green)

A lightweight and modern Markdown editor built with JavaFX.

**Author:** Frédéric Delorme (McG) - [contact.snapgames@gmail.com](mailto:contact.snapgames@gmail.com)

**Repository:** [https://github.com/mcgivrer/marknote](https://github.com/mcgivrer/marknote)

---

## Features

- **Markdown Editing** - Full-featured Markdown editor with syntax highlighting
- **Live Preview** - Real-time HTML preview with WebView rendering
- **Project Explorer** - Browse and manage your project files with drag & drop support
- **Multi-document Tabs** - Work on multiple documents simultaneously
- **Theme Support** - Built-in themes (Light, Dark, Solarized, High Contrast) with custom theme creation
- **Image Preview** - Quick preview for images in your project
- **Recent Projects** - Quick access to recently opened projects
- **Welcome Page** - Configurable welcome screen with recent projects
- **Cross-platform** - Works on Linux, macOS, and Windows

## Supported Languages

MarkNote is available in 5 languages:

| Language | Locale |
|----------|--------|
| Français (French) | `fr` |
| English | `en` |
| Deutsch (German) | `de` |
| Español (Spanish) | `es` |
| Italiano (Italian) | `it` |

The application automatically uses your system's locale.

## Requirements

- **Java 25** or higher
- **JavaFX 24** (included in libs folder)

## Building

Clone the repository and run the build script:

```bash
git clone https://github.com/mcgivrer/marknote.git
cd marknote
./build
```

### Build Commands

| Command | Description |
|---------|-------------|
| `./build` | Compile the project and create the JAR |
| `./build run` | Compile and run the application |
| `./build test` | Run unit tests |
| `./build package` | Create a distributable package with embedded JRE |

## Running

After building, run the application:

```bash
./target/build/MarkNote.sh
```

Or use the build script:

```bash
./build run
```

### Running with a specific language

```bash
java -Duser.language=en -Duser.country=US --module-path target/build/libs --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.media,javafx.web -cp "target/build/MarkNote-0.0.1.jar:target/build/libs/*" Main
```

## Packaging

Create a distributable package with an embedded minimal JRE:

```bash
./build package
```

This creates a ZIP archive in `target/` containing:
- The application JAR
- Required libraries
- Embedded minimal JRE (created with jlink)
- Platform-specific launcher script

### Package Output

The package is named: `MarkNote-{version}-{platform}.zip`

Where `{platform}` is:
- `linux` - Linux x64
- `mac` - macOS
- `win` - Windows x64

## Installation

### From Package

1. Download or build the package for your platform
2. Extract the ZIP archive:
   ```bash
   unzip MarkNote-0.0.1-linux.zip
   cd MarkNote-0.0.1-linux
   ```
3. Run the application:
   - **Linux/macOS:** `./MarkNote.sh`
   - **Windows:** `MarkNote.bat`

### From Source

1. Clone and build the project (see Building section)
2. Run directly from the build output:
   ```bash
   ./target/build/MarkNote.sh
   ```

## Project Structure

```
MarkNote/
├── build                    # Build script
├── libs/                    # External libraries
│   ├── common/              # Platform-independent JARs
│   ├── linux/               # Linux-specific JavaFX natives
│   ├── mac/                 # macOS-specific JavaFX natives
│   └── win/                 # Windows-specific JavaFX natives
├── src/main/
│   ├── java/                # Java source files
│   │   ├── Main.java
│   │   ├── MarkNote.java
│   │   ├── config/          # Configuration classes
│   │   ├── ui/              # UI components
│   │   └── utils/           # Utility classes
│   └── resources/
│       ├── css/             # Stylesheets
│       │   ├── markdown-editor.css
│       │   └── themes/      # Theme CSS files
│       ├── i18n/            # Internationalization
│       │   ├── messages.properties
│       │   ├── messages_fr.properties
│       │   ├── messages_en.properties
│       │   ├── messages_de.properties
│       │   ├── messages_es.properties
│       │   └── messages_it.properties
│       └── images/          # Application icons
└── target/                  # Build output
```

## Contributing

Contributions are welcome! Here's how you can help:

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/my-new-feature`
3. **Commit** your changes: `git commit -am 'Add some feature'`
4. **Push** to the branch: `git push origin feature/my-new-feature`
5. **Submit** a Pull Request

### Guidelines

- Follow existing code style and conventions
- Add/update i18n messages for all supported languages when adding UI text
- Test on multiple platforms if possible
- Update documentation as needed

### Adding a New Language

1. Create a new `messages_{locale}.properties` file in `src/main/resources/i18n/`
2. Translate all messages from the default file
3. Update this README to list the new language

## License

MIT License

Copyright (c) 2026 Frédéric Delorme - SnapGames

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.



