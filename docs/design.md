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
 :liquid-glass/motion      {<phase>   {:duration ... :easing ...}}}
```

- `default-tokens` — v1 **light-scheme** material. Three surface variants:
  `:clear` (barely-there — scrims/tooltips), `:regular` (default control
  surface), `:thick` (toolbars/sheets over busy content). Four elevation
  levels: `:flat` / `:raised` / `:overlay` / `:floating`.
- `dark-tokens` — a **partial** override map (only `:surface` tint/border and
  `:specular` opacity — the entries that actually change when the content
  behind the glass goes dark; blur/saturate/radius/motion are scheme-independent
  and are simply not redeclared).
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
- **Tier B** (`component-css`) — the actual glass material rules, as a single
  literal CSS string scoped to `liquid-glass__<component>` classes. Unlike
  shitsuke (whose component visuals are deferred to a shadow-css `:pages`
  build that isn't wired up yet), liquid-glass-ui ships Tier B as plain CSS
  text: no build step needed to see the material; the same string works
  inlined in SSR or concatenated into a browser build's `main.css`. Every rule
  references `var(--liquid-glass-...)` only — never a literal color/blur
  value — so `root-css` overrides (including the dark-mode block) always
  apply. Includes a `prefers-reduced-motion: reduce` guard and an
  `@supports not (backdrop-filter: blur(1px))` opaque-background fallback for
  engines without backdrop-filter.
- `(class-name component)` → `"liquid-glass__<component>"` (also accepts
  `"panel--thick"`-shaped modifier names).
- `(inline-style css?)` / `(inline-style-hiccup css?)` — `<style>` wrapper for
  SSR embedding (defaults to `root-css` + `component-css` concatenated).

## Layer 3 — `liquid-glass.components`

Pure-hiccup glass primitives (`.cljc`, no reagent import — same dual-render
contract as shitsuke). liquid-glass-ui owns **no** interaction/state logic:
components either wrap the matching `shitsuke.components` fn (keeping its
exact `act`/opts contract) or, where shitsuke has no equivalent, are a small
hiccup literal following the same `data-act` convention.

| fn | based on | shape |
|---|---|---|
| `button` / `icon-button` | `shitsuke.components/button` / `icon-button` | same opts (`:act`, `:disabled`, `:title`, `:type`, `:class`) |
| `toolbar` | `shitsuke.components/toolbar` | `(actions opts?)` |
| `tab-bar` | (shitsuke `mode-tabs` shape, reimplemented glass-only) | `(tabs current opts?)` — `tabs` = `[ [id label] … ]` |
| `panel` | `shitsuke.components/card` | `(body opts?)` — `:surface` (`:clear`\|`:regular`\|`:thick`), `:elevation` (`:flat`\|`:raised`\|`:overlay`\|`:floating`) |
| `sheet` | — (no shitsuke equivalent) | `(body opts?)` — `:label` (aria-label), bottom-sheet/modal surface |
| `scrim` | — (no shitsuke equivalent) | `(opts?)` — full-viewport dismiss backdrop, `:act` |
| `badge` | — (no shitsuke equivalent) | `(label opts?)` — small pill counter |

Every component appends a `liquid-glass__specular` marker span as its last
child (`display:none` in v1 — the actual sheen is the CSS `::before` overlay).
It exists purely as a stable hook for future enhancement (see below); it is
not required for the current visual and removing/ignoring it changes nothing.

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
