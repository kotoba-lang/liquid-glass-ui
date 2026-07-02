# liquid-glass-ui — design

Layer-by-layer API reference. For the *why* see `docs/adr/0001-liquid-glass-ui.md`;
for the superproject-level decision see
`90-docs/adr/2607011900-kotoba-lang-liquid-glass-ui.md`.

## Layer 1 — `liquid-glass.tokens`

Token IR, same shape/merge semantics as `shitsuke.tokens` (group -> token ->
CSS-string value or nested map of CSS props), but with glass-specific groups:

```clojure
{:liquid-glass/surface     {<variant> {:blur ... :saturate ... :tint ... :border ...}}
 :liquid-glass/elevation   {<level>   {:shadow ...}}
 :liquid-glass/specular    {<part>    {...}}
 :liquid-glass/radius      {<size>    <css-length>}
 :liquid-glass/motion      {<phase>   {:duration ... :easing ...}}
 :liquid-glass/accent      {:tint ... :tint-strong ...}
 :liquid-glass/ink         {:default ... :shadow ...}}
```

- `default-tokens` — v1 **light-scheme** material. Three surface variants:
  `:clear` (barely-there — scrims/tooltips), `:regular` (default control
  surface), `:thick` (toolbars/sheets over busy content). Four elevation
  levels: `:flat` / `:raised` / `:overlay` / `:floating`. `:accent` is a
  translucent tint used for "on/checked/filled" states (toggle-on, checkbox/
  radio-checked, slider/progress fill) so those states still read as glass
  rather than a flat swatch (same value in light and dark — override it
  yourself if a themed consumer needs a different accent per scheme). `:ink`
  is the default text color + a soft counter-shadow for text sitting on the
  material (dark ink + a light shadow in the light scheme; light ink + a dark
  shadow in dark) — see the "Text legibility" note below.
- `dark-tokens` — a **partial** override map (`:surface` tint/border,
  `:specular` opacity, and `:ink` — the entries that actually change when the
  content behind the glass goes dark; blur/saturate/radius/motion are
  scheme-independent and are simply not redeclared).
- `deep-merge` — re-exported from `shitsuke.tokens` (same right-biased
  recursive merge; overrides compose identically across both token sets).
- `(resolve-tokens overrides)` / `(resolve-dark-tokens dark-overrides)` —
  default/dark tokens deep-merged with a partial override map.
- `(css-variables overrides?)` — `:root { --liquid-glass-<group>-<name>: ...; }`.
- `(dark-css-variables dark-overrides?)` — the same, wrapped in
  `@media (prefers-color-scheme: dark) { :root { ... } }`, **redeclaring the
  same custom-property names** (not `-dark`-suffixed variants) so components
  only ever reference one var per concept.

## Layer 2 — `liquid-glass.style`

Two-tier, same split as `shitsuke.style`:

- **Tier A** (`root-css`) — portable CSS custom properties: light `:root` +
  dark media-query override, from `liquid-glass.tokens`.
- **Tier B** (`component-css` / `component-rules`) — the actual glass
  material rules, scoped to `liquid-glass__<component>` classes. Generated
  from **EDN declaration data** via [`kotoba-lang/css`](https://github.com/kotoba-lang/css)
  (`css.core`) rather than hand-typed CSS strings — liquid-glass-ui is
  `css.core`'s first real consumer. `component-rules` returns the raw
  `[selector decls-map]` pairs; `component-css` renders them (plus a
  `@keyframes`/`@media (prefers-reduced-motion: reduce)`/`@supports not
  (backdrop-filter)` block) to the CSS string via `css.core/css`. Unlike
  shitsuke (whose component visuals are deferred to a shadow-css `:pages`
  build that isn't wired up yet), liquid-glass-ui ships Tier B as a plain CSS
  string: no build step needed to see the material; the same string works
  inlined in SSR or concatenated into a browser build's `main.css`. Every
  declaration references `var(--liquid-glass-...)` only — never a literal
  color/blur value — so `root-css` overrides (including the dark-mode block)
  always apply.

  **Why data instead of strings**: two real bugs found while building this
  repo were exactly the "wrote a class, forgot the rule" shape — `panel--flat`'s
  shadow rule, and the `:specular :rim` tokens (defined, never referenced).
  Hand-typed CSS strings don't catch that; `component-rules` being actual
  data means a test can assert against it directly instead of regex-scraping
  rendered text — see e.g. `components-test/every-elevation-shadow-carries-both-rim-vars-test`,
  which walks every rule's `:box-shadow` and fails if an elevation shadow is
  ever added without the rim insets (this test caught a real instance: the
  `slider` thumb's vendor-prefixed pseudo-elements were hand-written outside
  the shared helper and had elevation but no rim, until fixed).

  Every glass surface gets the same three material ingredients plus a
  default text treatment, built by four private declaration-map helpers
  (`backdrop-decls`/`glass-bg-decls`/`glass-shadow-decls`/`ink-decls`)
  instead of ~15 hand-typed copies, so a component can't end up with only
  some of them:
  1. **backdrop-filter**: `blur` + `saturate` from its surface tier's tokens,
     plus a fixed `brightness(1.05)` lift — a flat blur reads as smudged
     glass, not lit glass.
  2. **specular overlay** (shared `::before`): a soft `radial-gradient`
     highlight anchored near the top-left, not a flat linear sheen — reads as
     a light source catching a curved surface.
  3. **rim light** (folded into every `box-shadow`, alongside the elevation
     shadow): a bright 1px inset line on the top edge, a much dimmer one on
     the bottom (the `:liquid-glass/specular :rim` token — asymmetric on
     purpose, light from above). This is the detail that turns a flat
     translucent rectangle into something that reads as an actual lit glass
     edge.
  4. **ink** (`color` + `text-shadow` from `:liquid-glass/ink`): applied on a
     broader selector list than the specular treatment (`ink-components` in
     `liquid-glass.style` — every top-level component root that can carry
     text, including `toggle`/`checkbox`/`radio`'s outer `<label>`, not just
     their inner glass box). `color`/`text-shadow` are inherited CSS
     properties, so setting them once here reaches every nested span
     (`checkbox-text`, `list-row-content`, …) without a rule per sub-element.
     **Text legibility**: this replaces the browser default of inheriting
     whatever ancestor color happened to be in scope — which is what
     silently produced illegible text before this token existed (a consumer
     had no way to know their page's own dark-on-light text would render as
     dark-on-translucent-dark inside a glass panel without duplicating a
     color override themselves). It does **not** attempt content-aware
     adaptive tinting (sampling what's actually behind the glass and picking
     a contrasting color) — that needs either JS or GPU compositing, is
     explicitly out of scope for a static CSS material (see "Future work"),
     and `ink` only follows the OS `prefers-color-scheme` signal, the same
     one that already drives the surface tint. A consumer whose own
     background genuinely conflicts with that (e.g. a light-scheme page with
     a deliberately dark hero photo behind a glass panel, as this repo's own
     `docs/index.html` showcase does against its colorful gradient) sets
     `color`/`text-shadow` on their own more-specific selector to override it,
     same as overriding any other CSS default.
- `(class-name component)` → `"liquid-glass__<component>"` (also accepts
  `"panel--thick"`-shaped modifier names).
- `(inline-style css?)` / `(inline-style-hiccup css?)` — `<style>` wrapper for
  SSR embedding (defaults to `root-css` + `component-css` concatenated).

## Layer 3 — `liquid-glass.components`

Pure-hiccup glass primitives (`.cljc`, no reagent import — same dual-render
contract as shitsuke). liquid-glass-ui owns **no** interaction/state logic:
components either wrap the matching `shitsuke.components` fn (keeping its
exact `act`/opts contract) or, where shitsuke has no equivalent, are a small
hiccup literal following the same `data-act` convention. Coverage targets the
practical subset of SwiftUI's control catalog a glass DOM/CSS skin can
actually express — see "Explicitly out of scope" below for what isn't here
and why.

**Structural / navigation**

| fn | based on | shape |
|---|---|---|
| `panel` | `shitsuke.components/card` | `(body opts?)` — `:surface` (`:clear`\|`:regular`\|`:thick`), `:elevation` (`:flat`\|`:raised`\|`:overlay`\|`:floating`) |
| `toolbar` | `shitsuke.components/toolbar` | `(actions opts?)` — floating action bar |
| `nav-bar` | — | `(title opts?)` — `:leading`/`:trailing` (hiccup), page-header bar |
| `tab-bar` | (shitsuke `mode-tabs` shape, reimplemented glass-only) | `(tabs current opts?)` — `tabs` = `[ [id label] … ]`; doubles as a SwiftUI `Picker(.segmented)` |
| `sheet` | — | `(body opts?)` — `:label`, bottom-anchored modal surface |
| `alert` | — | `(body opts?)` — `:label`, centered modal dialog (distinct from `sheet`) |
| `menu` | — | `(items opts?)` — `items` = `[{:label :act :disabled?} …]`, popover action list |
| `scrim` | — | `(opts?)` — full-viewport dismiss backdrop for `sheet`/`alert` |
| `list-view` / `list-row` | — | `(rows opts?)` / `(content opts?)` — `:surface`, row `:act`/`:trailing` |
| `disclosure` | — | `(summary body opts?)` — native `<details>`/`<summary>` (SwiftUI `DisclosureGroup`), no JS needed |

**Controls**

| fn | based on | shape |
|---|---|---|
| `button` / `icon-button` | `shitsuke.components/button` / `icon-button` | same opts (`:act`, `:disabled`, `:title`, `:type`, `:class`) |
| `text-field` / `text-area` | `shitsuke.components/input` / `textarea` | same opts, wrapped in a glass field |
| `search-field` | (text-field + leading glyph) | same opts as `text-field` |
| `menu-select` | `shitsuke.components/select` | `(options opts?)` — same opts as shitsuke `select` |
| `toggle` | — | `(opts?)` — `:checked`, `:on-change`/`:act`, `:disabled` (native `<input type=checkbox>`, glass track+thumb sibling) |
| `checkbox` / `radio` | — | `(label? opts?)` — `radio` groups via `:group` (same-named native `name`) |
| `slider` | — | `(opts?)` — native `<input type=range>`, glass track+thumb via vendor pseudo-elements |
| `stepper` | built from `icon-button` | `(value opts?)` — `:dec-act`/`:inc-act` |

**Feedback / content**

| fn | based on | shape |
|---|---|---|
| `progress-bar` | — | `(value opts?)` — `:max` (default 100), determinate linear fill |
| `progress-circle` | — | `(opts?)` — indeterminate spinner |
| `gauge` | — | `(value opts?)` — `:max` (default 100), determinate ring (SwiftUI `Gauge`) via an inline conic-gradient |
| `badge` | — | `(label opts?)` — small pill counter |
| `chip` | — | `(label opts?)` — `:act`, `:on-remove-act` (dismiss ×) — filter/tag chip |
| `label` | — | `(icon text opts?)` — SwiftUI `Label`-shaped icon+text row |
| `avatar` | — | `(content opts?)` — `:src`/`:alt` for an image, else initials/hiccup |
| `divider` | — | `()` — hairline `<hr>` |
| `tooltip` | — | `(text opts?)` — positioning is the consumer's responsibility |

Every component whose top-level element *is* the glass surface (`panel`,
`button`, `toolbar`, `sheet`, `text-field`, `menu-select`, `nav-bar`, `alert`,
`menu`, `list-view`, `stepper`, `chip`, `disclosure`, …) appends a `liquid-glass__specular` marker
span as its last child (`display:none` in v1 — the actual sheen is the CSS
`::before` overlay). It exists purely as a stable hook for future enhancement
(see below); it is not required for the current visual and
removing/ignoring it changes nothing. Compound controls whose glass surface
is a small *nested* box (`toggle`/`checkbox`/`radio` — the surface is the
track/box `<span>` inside a `<label>`) skip the marker span; see the
`liquid-glass.components` namespace docstring for why.

### Explicitly out of scope

Native-OS-only pickers that a CSS/DOM glass skin cannot meaningfully re-skin
without either losing platform-native affordances or requiring a JS calendar/
color-grid widget (real scope creep for a style layer, not a control layer):
`DatePicker`, `ColorPicker`. Also out: `OutlineGroup` (nested multi-level tree
disclosure — `disclosure` covers the single-level `DisclosureGroup` case; a
tree needs recursive state a "portable, zero deps" component can't own),
`ShareLink` (OS share-sheet integration, platform-specific). Positioning
logic for `menu`/`tooltip` (anchor-to-trigger, viewport-edge flipping) is
deliberately left to the consumer — it needs either JS or CSS
anchor-positioning, neither of which this repo's "portable `.cljc`, zero
deps" contract can own without picking a specific runtime.

## Styling contract

A consumer emits `liquid-glass.style/root-css` + `component-css` once per
page/app (SSR `<style>` or prepended into a browser build's CSS output,
mirroring `slides.build`'s handling of `shitsuke.tokens/css-variables`).
Components never carry inline visual style — only stable classes + `var(...)`
references, so overriding the material (a themed consumer, a different
surface tint) is a token-override problem, not a component-code problem.

## Future work (explicitly out of v1 scope)

- **Shadow-css `:pages` extraction** of Tier B as an alternative to the plain
  CSS string, once shitsuke's own component shadow-css wiring lands (today
  neither repo relies on it — see shitsuke ADR 2606301900's "negative"
  consequence).
- **True refraction / continuous-corner ("squircle") rendering** via a
  `liquid-glass.gpu` namespace on `kotoba-lang/webgpu`'s WGSL DSL, for
  canvas-rendered surfaces (`kami-engine`'s `kotoba.ui` HUD, `kobo`/`kuro`
  editor panes) where a real background-sampling distortion shader is
  possible and DOM `backdrop-filter` cannot express it. The `liquid-glass__specular`
  span and the `:liquid-glass/specular` token group are deliberately left as
  the seam this would attach to (a pointer/device-motion-driven highlight
  position, or a GPU-composited surface) — not built until a concrete
  canvas-context consumer needs it.
- **Manual light/dark override hook** (a `[data-lg-theme="dark"]` selector
  block alongside the `prefers-color-scheme` media query) for apps that need
  a user-facing theme toggle rather than following the OS setting — not added
  until a consumer needs it.
