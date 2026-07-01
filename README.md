# liquid-glass-ui

`liquid-glass-ui` is the kotoba-lang shared **"liquid glass" visual skin**: a
translucent, specular-highlighted, blur+saturate material (in the spirit of
Apple's Liquid Glass / iOS 26 design language) built as a thin layer **on top
of [`shitsuke`](../shitsuke)**, kotoba-lang's UI design system (tokens +
hiccup + style + portable re-frame seam + component primitives).

liquid-glass-ui does not reimplement interaction, state, or the dual-render
contract — shitsuke already owns that. It owns exactly one thing: **the glass
material** — as its own token group, its own two-tier style layer, and a set
of pure-hiccup components that wrap (or, where no shitsuke equivalent exists,
extend) `shitsuke.components` with glass classes and a specular-highlight
decoration.

```text
liquid-glass-ui = material tokens (blur/saturate/tint/elevation/specular/radius/motion/accent)
                  + style (CSS vars + literal component CSS, portable, no build step)
                  + 26 components: panel/toolbar/nav-bar/tab-bar/sheet/alert/menu/scrim/list
                    + button/icon-button/text-field/text-area/search-field/menu-select/
                      toggle/checkbox/radio/slider/stepper
                    + progress-bar/progress-circle/badge/label/avatar/divider/tooltip
                  on top of shitsuke (tokens/hiccup/style/re-frame/components)
```

Component coverage targets the practical subset of SwiftUI's control catalog
a glass DOM/CSS skin can actually express — see `docs/design.md` for the full
per-component table and what's explicitly out of scope (DatePicker/ColorPicker
and friends — native-OS-only widgets a CSS skin can't meaningfully re-skin).

## Boundaries

| layer | role |
|---|---|
| `liquid-glass.tokens` | material token IR (`:liquid-glass/surface` `:elevation` `:specular` `:radius` `:motion` `:accent`) + light/dark resolver + `:root` / `@media (prefers-color-scheme: dark)` CSS-var emitter |
| `liquid-glass.style` | `class-name` registry (`liquid-glass__<component>`) + `root-css` (Tier A vars) + `component-css` (Tier B literal glass rules — backdrop-filter, specular overlay, elevation shadow, press/hover motion, reduced-motion + no-backdrop-filter fallback) |
| `liquid-glass.components` | 26 pure-hiccup glass primitives — see `docs/design.md` for the full table |
| shitsuke (dep) | dual-render contract, `act` interaction convention, `button`/`icon-button`/`toolbar`/`card`/`input`/`textarea`/`select` primitives that liquid-glass-ui wraps |

## Why a separate repo instead of adding it to shitsuke

`shitsuke` is host-shape (tokens/hiccup/style/state) and intentionally has no
opinion on *what a component looks like* beyond a stable class-name hook.
`liquid-glass-ui` is one visual skin among others a kotoba-lang frontend might
choose (a plain/flat skin, an OS-native skin, etc.) — it belongs in its own
repo so a consumer that wants shitsuke's structure without the glass look
(or a future alternative skin) doesn't pull in glass-specific tokens/CSS. See
`docs/adr/0001-liquid-glass-ui.md`.

## Usage

```clojure
(require '[liquid-glass.style :as ls]
         '[liquid-glass.components :as lg]
         '[shitsuke.hiccup :as h])

;; once per page/app: emit the material CSS (SSR <style> or prepend to main.css)
(ls/inline-style)

;; pure hiccup, same dual-render contract as shitsuke
(def view
  (lg/panel
   [(lg/toolbar [(lg/icon-button "☰") (lg/badge "3")])
    (lg/tab-bar [[:visual "Visual"] [:edn "EDN"]] :visual)]
   {:surface :thick :elevation :floating}))

(h/->html view)   ; SSR (clj/babashka)
;; browser: the same `view` is returned by a reagent component and mounted via
;; shitsuke.reagent.core/render; state via shitsuke.re-frame.core (unchanged
;; from shitsuke — liquid-glass-ui does not touch the state layer).
```

## Tests

```bash
clojure -M:test            # published git shitsuke dep
clojure -M:local:test      # local ../shitsuke override
```

## Design

See `docs/design.md` for the token/style/component API and
`docs/adr/0001-liquid-glass-ui.md` for the decision record. Superproject-level
decision: `90-docs/adr/2607011900-kotoba-lang-liquid-glass-ui.md`.
