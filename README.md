# Anki

[![Build Status](https://github.com/ankitects/anki/actions/workflows/ci.yml/badge.svg)](https://github.com/ankitects/anki/actions/workflows/ci.yml)
[![Documentation](https://img.shields.io/badge/docs-dev--docs.ankiweb.net-blue)](https://dev-docs.ankiweb.net)

This repo contains the source code for the computer version of
[Anki](https://apps.ankiweb.net).

## About

Anki is a spaced repetition program. Please see the [website](https://apps.ankiweb.net) to learn more.

## Getting Started

### Contributing

Want to contribute to Anki? Check out the [Contribution Guidelines](./docs/contributing.md).

For more information on building and developing, please see [Development](./docs/development.md).

#### Contributors

The following people have contributed to Anki: [CONTRIBUTORS](./CONTRIBUTORS)

### Anki Betas

If you'd like to try development builds of Anki but don't feel comfortable
building the code, please see [Anki betas](https://betas.ankiweb.net/).

## Linux Desktop Integration

On any Linux desktop that follows the [XDG spec](https://specifications.freedesktop.org/desktop-entry-spec/latest/) (GNOME, KDE, XFCE, Cinnamon, MATE, and others), you can install a launcher entry for the development build so it appears in your app grid and can be pinned to the dock.

```bash
# From the repo root
sed "s|ANKI_SRC|$(pwd)|g" tools/anki-dev.desktop \
    > ~/.local/share/applications/anki-dev.desktop
update-desktop-database ~/.local/share/applications/
```

Then open your app grid, search for **Anki (dev)**, right-click, and choose **Add to Favourites** (GNOME) or the equivalent for your desktop.

## License

Anki's license: [LICENSE](./LICENSE)
