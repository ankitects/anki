# Anki®

[![Build status](https://badge.buildkite.com/c9edf020a4aec976f9835e54751cc5409d843adbb66d043bd3.svg?branch=main)](https://buildkite.com/ankitects/anki-ci)

This repo contains the source code for the computer version of [Anki](https://apps.ankiweb.net).

**Anki** is a powerful, intelligent flashcard program that makes remembering things easy.

## 📖 About Anki

Anki is a spaced repetition flashcard program. It helps you memorize information efficiently by presenting cards at optimal intervals based on your performance. Anki uses an algorithm to schedule reviews, ensuring you see cards right before you're about to forget them.

### Key Features

- **Spaced Repetition**: Scientifically-proven algorithm for efficient learning
- **Rich Media Support**: Add images, audio, video, and LaTeX to your cards
- **Cross-Platform**: Available on Windows, macOS, Linux, iOS, and Android
- **Synchronization**: Keep your cards in sync across all your devices
- **Extensible**: Powerful add-on system for customization
- **Open Source**: Free and open-source software

## 🚀 Getting Started

### For Users

If you're looking to use Anki, the easiest way is to download the latest release from the [official website](https://apps.ankiweb.net).

**Want to try the latest features?** Check out [Anki betas](https://betas.ankiweb.net/) for pre-release versions.

### For Developers

This is a complex project with multiple components:

- **Backend**: Rust (`rslib/`) - Core scheduling, database, and sync logic
- **Python Library**: (`pylib/`) - Python bindings and add-on API
- **Frontend**: TypeScript/Svelte (`ts/`) - Modern web-based UI
- **Qt Interface**: Python/Qt (`qt/`) - Desktop application wrapper

#### Quick Start

1. **Prerequisites**:
   - [Rust](https://rustup.rs/) (version specified in `rust-toolchain.toml`)
   - [N2 or Ninja](https://github.com/ninja-build/ninja) build system
   - Python 3.9+ (3.9 recommended)
   - Platform-specific tools (see platform docs below)

2. **Clone the repository**:
   ```bash
   git clone https://github.com/ankitects/anki.git
   cd anki
   ```

3. **Install build tools**:
   ```bash
   # Install N2 (recommended) or Ninja
   bash tools/install-n2
   ```

4. **Platform-specific setup**:
   - [Windows](./docs/windows.md)
   - [macOS](./docs/mac.md)
   - [Linux](./docs/linux.md)

5. **Build and run**:
   ```bash
   # Build the project
   tools/build

   # Run Anki
   ./run
   ```
   
   (On Windows, use `.\run` instead)

For detailed development instructions, see the [Development Guide](./docs/development.md).

#### Pre-built Python Wheels

If you're developing add-ons or need Python bindings without building from source, you can install pre-built wheels from PyPI:

```bash
pip install --pre aqt
```

See [betas.ankiweb.net](https://betas.ankiweb.net/#via-pypipip) for more details.

## 🤝 Contributing

We welcome contributions! Whether you're fixing bugs, adding features, or improving documentation, your help makes Anki better for everyone.

### How to Contribute

1. **Read the guidelines**: Check out our [Contribution Guidelines](./docs/contributing.md)
2. **Find an issue**: Look for issues labeled `good first issue` or browse open issues
3. **Fork and branch**: Create a feature branch from `main`
4. **Make changes**: Write clean, tested code following our style guidelines
5. **Submit a PR**: Open a pull request with a clear description of your changes

### Development Resources

- [Development Guide](./docs/development.md) - Building and running Anki
- [Architecture Documentation](./docs/architecture.md) - Understanding the codebase
- [Contributing Guide](./docs/contributing.md) - Contribution process and guidelines
- [Editing Guide](./docs/editing.md) - Code editing best practices

## 📚 Project Structure

```
anki/
├── rslib/          # Rust backend library (core logic)
├── pylib/          # Python library and bindings
├── qt/             # Qt desktop application
├── ts/             # TypeScript/Svelte frontend
├── ftl/            # Fluent translation files
├── proto/          # Protocol buffer definitions
├── docs/           # Documentation
└── tools/          # Build and development scripts
```

## 🛠️ Technology Stack

- **Rust**: Core backend, scheduling algorithm, database operations
- **Python**: Add-on API, Qt bindings, build scripts
- **TypeScript/Svelte**: Modern web-based user interface
- **Qt**: Desktop application framework
- **Protocol Buffers**: Inter-process communication
- **SQLite**: Database storage

## 📄 License

Anki is licensed under the [AGPL-3.0-or-later](./LICENSE) license.

## 🙏 Acknowledgments

Anki is made possible by the contributions of many developers and the support of the community. See [CONTRIBUTORS](./CONTRIBUTORS) for a list of contributors.

## 🔗 Links

- **Official Website**: https://apps.ankiweb.net
- **Documentation**: https://docs.ankiweb.net
- **Beta Builds**: https://betas.ankiweb.net
- **Add-ons**: https://ankiweb.net/shared/addons/
- **Community**: https://forums.ankiweb.net
- **GitHub**: https://github.com/ankitects/anki

## 📞 Support

- **User Support**: https://help.ankiweb.net
- **Security Issues**: See [SECURITY.md](./SECURITY.md)

---

**Note**: This is the computer version of Anki. For mobile apps, see:
- [AnkiMobile (iOS)](https://apps.apple.com/app/ankimobile-flashcards/id373493387)
- [AnkiDroid (Android)](https://github.com/ankidroid/Anki-Android)
