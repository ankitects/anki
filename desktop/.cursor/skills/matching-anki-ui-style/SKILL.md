---
name: matching-anki-ui-style
description: >-
  Use when creating or modifying any Anki UI — Svelte components, web pages,
  CSS/SCSS, or Qt widgets/dialogs — so new UI matches Anki's existing look and
  feel and works in both light and dark (night mode) themes. Covers using Anki's
  shared design tokens (CSS custom properties on the web, aqt.colors + theme_manager
  in Qt) instead of hardcoded colors/sizes, reusing the ts/lib/components library,
  following the SCSS + Bootstrap 5 conventions, and avoiding drastic visual or
  theme changes. Use whenever adding buttons, inputs, dialogs, layouts, icons, or
  styles to Anki.
---

# Matching Anki's UI Style

## Overview

Anki has a **single, token-based design system** shared across the web layer
(Svelte + SCSS) and the Qt layer (Python), with **automatic light/dark ("night
mode") theming**. New UI must look native to Anki and theme correctly in both
modes.

> **Prime directive: reuse before you restyle. Never hardcode colors, sizes, or
> radii — always use Anki's design tokens and existing components. Do not
> introduce a new visual language (new palette, fonts, spacing, or component
> library).**

A screen that "looks fine" in light mode but uses literal hex colors is a defect:
it won't adapt to night mode and it drifts from Anki's identity.

## When to Use

- Adding/editing Svelte components, mediasrv pages, or any web styling in `ts/`.
- Adding/editing Qt widgets, dialogs, or `.ui` files in `qt/aqt/`.
- Any task that touches colors, spacing, typography, borders, icons, or layout.

For the discipline of safe in-tree changes generally, also use the
`anki-brownfield-development` skill.

## Design Tokens — Use These, Never Hardcode

Tokens are defined once in `ts/lib/sass/_vars.scss` (raw colors in
`_color-palette.scss`) and exposed to **both** layers. Each token carries a light
and a dark value, so using a token gets you night mode for free.

### Web (Svelte / SCSS): CSS custom properties

Reference with `var(--token)`. They're generated into `:root` (light) and
`:root.night-mode` (dark) by `ts/lib/sass/_root-vars.scss`.

| Purpose                | Tokens                                                                                                                                                                                       |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Text / foreground      | `--fg`, `--fg-subtle`, `--fg-disabled`, `--fg-faint`, `--fg-link`                                                                                                                            |
| Surfaces / backgrounds | `--canvas`, `--canvas-elevated`, `--canvas-inset`, `--canvas-overlay`, `--canvas-code`, `--canvas-glass`                                                                                     |
| Borders                | `--border`, `--border-subtle`, `--border-strong`, `--border-focus`                                                                                                                           |
| Shadows                | `--shadow`, `--shadow-inset`, `--shadow-subtle`, `--shadow-focus`                                                                                                                            |
| Sizing / shape         | `--font-size`, `--border-radius`, `--border-radius-medium`, `--border-radius-large`, `--buttons-size`                                                                                        |
| Motion                 | `--transition`, `--transition-medium`, `--transition-slow`                                                                                                                                   |
| Semantic accents       | `--accent-card`, `--accent-note`, `--accent-danger`, `--state-new`/`-learn`/`-review`/`-buried`/`-suspended`/`-marked`, `--flag-1`…`--flag-7`, `--highlight-bg`/`-fg`, `--selected-bg`/`-fg` |

```scss
/* GOOD — themed, adapts to night mode automatically */
.panel {
    color: var(--fg);
    background: var(--canvas-elevated);
    border: 1px solid var(--border-subtle);
    border-radius: var(--border-radius);
    font-size: var(--font-size);
}

/* BAD — hardcoded; breaks night mode and Anki's identity */
.panel {
    color: #333;
    background: #fff;
    border: 1px solid #ddd;
    border-radius: 8px;
    font-size: 14px;
}
```

Prefer **semantic tokens** (`--fg`, `--canvas`) over reaching into the raw
palette; the palette exists to define tokens, not for direct use in app code.

### Qt (Python): `aqt.colors` + `theme_manager`

The same tokens exist as constants in `qt/aqt/colors.py` (and `props`). Access
them through the theme manager — never construct literal `QColor("#...")` or
branch on night mode yourself.

```python
from aqt import colors, props
from aqt.theme import theme_manager

qcolor = theme_manager.qcolor(colors.CANVAS)        # -> QColor
value = theme_manager.var(colors.FG_LINK)           # -> resolved value string
duration = int(theme_manager.var(props.TRANSITION))
```

Token names mirror the web (`colors.FG`, `colors.CANVAS`, `colors.BORDER_FOCUS`,
`colors.FG_DISABLED`, `colors.BUTTON_GRADIENT_START`, …). For `.ui` files,
prefer the existing styling path rather than embedding literal colors.

## Reuse Existing Components (Web)

Before writing markup or styles, check `ts/lib/components/` — there are ~48
shared components. Compose these instead of building your own:

| Need                | Use                                                                                                            |
| ------------------- | -------------------------------------------------------------------------------------------------------------- |
| Buttons             | `LabelButton`, `IconButton`, `ButtonGroup`, `ButtonToolbar`, `ButtonGroupItem`                                 |
| Inputs / controls   | `Select`, `Switch`, `SwitchRow`, `SpinBox`, `EnumSelector`, `CheckBox`, `ConfigInput`                          |
| Layout / containers | `Container`, `TitledContainer`, `Row`, `Col`, `Item`, `Spacer`, `Collapsible`, `ScrollArea`, `StickyContainer` |
| Overlays / feedback | `Popover`, `WithFloating`, `WithTooltip`, `HelpModal`, `Badge`, `Icon`, `IconConstrain`                        |

Only create a new shared component if nothing fits — and when you do, model its
API and styling on the closest existing one (e.g. the `export { className as
class }` prop, event forwarding, `tooltip` prop conventions).

## SCSS & Markup Conventions

- Use `<style lang="scss">` in Svelte and `@use "../sass/..."` for shared mixins
  (e.g. `@use "../sass/button-mixins" as button;` then `@include button.base(...)`)
  rather than writing button styling from scratch.
- **Bootstrap 5** is the base framework — prefer existing Bootstrap utilities and
  classes already used in the codebase over new bespoke CSS.
- Match neighbors for spacing, radius, and font sizing; reference tokens, not
  literals.
- Put the license header on new `.svelte` / `.scss` files (`just minilints`
  enforces it).
- User-facing strings go through the ftl `tr.*` API, never hardcoded (see
  `.cursor/rules/i18n.md`).

## Always Verify Both Themes

Check the change in **light and dark (night mode)**. If you used tokens, dark
mode works automatically; if something looks wrong only in one mode, you almost
certainly hardcoded a value. Verify web changes by running the relevant page
(`just run`, or `just web-watch` for live reload).

## Common Mistakes

| Mistake                                                   | Do instead                                                  |
| --------------------------------------------------------- | ----------------------------------------------------------- |
| Literal `#hex` / `rgb()` in CSS                           | `var(--token)` from `_vars.scss`                            |
| `QColor("#...")` or `if night_mode:` color branches in Qt | `theme_manager.qcolor(colors.X)` / `theme_manager.var(...)` |
| Hardcoded `px` radius / font size                         | `--border-radius*`, `--font-size`                           |
| Introducing a new color scheme, font, or spacing system   | Reuse existing tokens; no drastic theme change              |
| Hand-rolling a button/input                               | Reuse `LabelButton`/`IconButton`/`Select`/… + mixins        |
| Adding a new CSS framework or heavy inline literal styles | Bootstrap 5 + tokens                                        |
| Reaching into the raw color palette in app code           | Use semantic tokens                                         |
| Editing generated CSS under `out/`                        | Edit the `.scss`/source and rebuild                         |
| Shipping without checking night mode                      | Test light AND dark                                         |

## Quick Reference

```
PRIME       reuse before restyle; no new visual language
TOKENS-WEB  var(--fg|--canvas*|--border*|--shadow|--font-size|--border-radius*|--buttons-size)
TOKENS-QT   theme_manager.qcolor(colors.X) / theme_manager.var(colors.X|props.X)
COMPONENTS  check ts/lib/components/ first (buttons, inputs, layout, overlays)
SCSS        <style lang="scss"> + @use mixins; Bootstrap 5 base; license header
THEME       every value must work in light AND night-mode — never hardcode
```

## Related Guidance

- `anki-brownfield-development` skill — safe, conventional in-tree changes.
- `navigating-large-codebases` skill — how to explore/trace this repo.
- Source of truth: `ts/lib/sass/_vars.scss`, `_root-vars.scss`, `_color-palette.scss`.
- Component library: `ts/lib/components/`.
- Qt theming: `qt/aqt/colors.py`, `qt/aqt/theme.py`.
