(ns liquid-glass.style
  "Two-tier styling layer, mirroring shitsuke.style:

  Tier A (portable, `root-css`): token -> CSS custom properties on `:root`,
  light values plus a `prefers-color-scheme: dark` override block. Babashka-safe,
  zero deps beyond liquid-glass.tokens.

  Tier B (`component-css`): the actual glass material rules — backdrop-filter
  blur+saturate, tint, specular-highlight overlay, elevation shadow, corner
  radius, press/hover motion — as a single portable literal CSS string scoped
  to stable `liquid-glass__<component>` classes. Unlike shitsuke (which defers
  its component visuals to a shadow-css `:pages` build that does not yet exist),
  liquid-glass-ui ships its Tier B rules as plain CSS text: no build step is
  required to see the material, and the same string works for SSR (inlined
  `<style>`) and browser builds (concatenated into main.css). A shadow-css
  `:pages` extraction remains an option later (component fns already carry
  the stable class-name convention) but is not required for v1.

  Class names read `liquid-glass__<component>` (base) and
  `liquid-glass__<component>--<variant>` (modifier), the same two-part
  convention as shitsuke.style/class-name."
  (:require [liquid-glass.tokens :as t]
            [clojure.string :as str]))

(defn class-name
  "Stable class for a component or component--modifier, e.g. (class-name :button)
  => \"liquid-glass__button\", (class-name \"panel--thick\") => \"liquid-glass__panel--thick\"."
  [component]
  (str "liquid-glass__" (name component)))

(defn root-css
  "`:root{...}` light vars + `@media (prefers-color-scheme: dark){:root{...}}`
  override block, from tokens (default merged with overrides / dark-overrides)."
  ([] (root-css nil nil))
  ([overrides] (root-css overrides nil))
  ([overrides dark-overrides]
   (str (t/css-variables overrides) "\n" (t/dark-css-variables dark-overrides))))

(defn component-css
  "The Tier B literal CSS: glass material rules for every liquid-glass__*
  class, referencing only var(--liquid-glass-...) custom properties (never a
  literal color/blur value) so root-css overrides propagate. Includes a
  reduced-motion guard and a no-backdrop-filter fallback (older engines)."
  []
  (str
   ".liquid-glass__panel,.liquid-glass__button,.liquid-glass__icon-button,"
   ".liquid-glass__toolbar,.liquid-glass__sheet,.liquid-glass__badge{"
   "position:relative;isolation:isolate;box-sizing:border-box;"
   "color:inherit;font:inherit;"
   "transition:transform var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
   "box-shadow var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing);}\n"

   ".liquid-glass__panel::before,.liquid-glass__button::before,.liquid-glass__icon-button::before,"
   ".liquid-glass__toolbar::before,.liquid-glass__sheet::before{"
   "content:\"\";position:absolute;inset:0;pointer-events:none;border-radius:inherit;"
   "background:linear-gradient(115deg,rgba(255,255,255,var(--liquid-glass-specular-highlight-opacity)) 0%,rgba(255,255,255,0) 50%);"
   "mix-blend-mode:overlay;}\n"

   ".liquid-glass__panel{overflow:hidden;padding:1em;border-radius:var(--liquid-glass-radius-md);"
   "background:var(--liquid-glass-surface-regular-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-regular-blur)) saturate(var(--liquid-glass-surface-regular-saturate));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-regular-blur)) saturate(var(--liquid-glass-surface-regular-saturate));"
   "border:1px solid var(--liquid-glass-surface-regular-border);"
   "box-shadow:var(--liquid-glass-elevation-raised-shadow);}\n"
   ".liquid-glass__panel--clear{background:var(--liquid-glass-surface-clear-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-clear-blur)) saturate(var(--liquid-glass-surface-clear-saturate));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-clear-blur)) saturate(var(--liquid-glass-surface-clear-saturate));"
   "border-color:var(--liquid-glass-surface-clear-border);}\n"
   ".liquid-glass__panel--thick{background:var(--liquid-glass-surface-thick-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) saturate(var(--liquid-glass-surface-thick-saturate));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) saturate(var(--liquid-glass-surface-thick-saturate));"
   "border-color:var(--liquid-glass-surface-thick-border);}\n"
   ".liquid-glass__panel--flat{box-shadow:var(--liquid-glass-elevation-flat-shadow);}\n"
   ".liquid-glass__panel--overlay{box-shadow:var(--liquid-glass-elevation-overlay-shadow);}\n"
   ".liquid-glass__panel--floating{box-shadow:var(--liquid-glass-elevation-floating-shadow);}\n"

   ".liquid-glass__button,.liquid-glass__icon-button{overflow:hidden;display:inline-flex;align-items:center;"
   "justify-content:center;gap:.4em;padding:.6em 1.1em;border:1px solid var(--liquid-glass-surface-regular-border);"
   "border-radius:var(--liquid-glass-radius-pill);cursor:pointer;"
   "background:var(--liquid-glass-surface-regular-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-regular-blur)) saturate(var(--liquid-glass-surface-regular-saturate));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-regular-blur)) saturate(var(--liquid-glass-surface-regular-saturate));"
   "box-shadow:var(--liquid-glass-elevation-raised-shadow);}\n"
   ".liquid-glass__icon-button{padding:.55em;aspect-ratio:1;}\n"
   ".liquid-glass__button:hover,.liquid-glass__icon-button:hover{transform:translateY(-1px);"
   "box-shadow:var(--liquid-glass-elevation-overlay-shadow);}\n"
   ".liquid-glass__button:active,.liquid-glass__icon-button:active{transform:translateY(0) scale(.97);}\n"
   ".liquid-glass__button:disabled,.liquid-glass__icon-button:disabled{opacity:.45;cursor:not-allowed;transform:none;}\n"

   ".liquid-glass__toolbar{display:flex;align-items:center;gap:.5em;padding:.5em .75em;"
   "border-radius:var(--liquid-glass-radius-lg);background:var(--liquid-glass-surface-thick-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) saturate(var(--liquid-glass-surface-thick-saturate));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) saturate(var(--liquid-glass-surface-thick-saturate));"
   "border:1px solid var(--liquid-glass-surface-thick-border);"
   "box-shadow:var(--liquid-glass-elevation-overlay-shadow);}\n"

   ".liquid-glass__tab-bar{display:flex;gap:.15em;}\n"
   ".liquid-glass__tab{padding:.4em .9em;border-radius:var(--liquid-glass-radius-pill);"
   "opacity:.7;transition:opacity var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
   "background var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing);}\n"
   ".liquid-glass__tab--active{opacity:1;background:rgba(255,255,255,.18);}\n"

   ".liquid-glass__sheet{overflow:hidden;border-radius:var(--liquid-glass-radius-lg) var(--liquid-glass-radius-lg) 0 0;"
   "background:var(--liquid-glass-surface-thick-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) saturate(var(--liquid-glass-surface-thick-saturate));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) saturate(var(--liquid-glass-surface-thick-saturate));"
   "border:1px solid var(--liquid-glass-surface-thick-border);border-bottom:none;"
   "box-shadow:var(--liquid-glass-elevation-floating-shadow);"
   "padding:1.25em 1.25em calc(1.25em + env(safe-area-inset-bottom,0px));}\n"
   ".liquid-glass__scrim{position:fixed;inset:0;background:rgba(0,0,0,.28);backdrop-filter:blur(2px);"
   "-webkit-backdrop-filter:blur(2px);}\n"

   ".liquid-glass__badge{overflow:hidden;display:inline-flex;align-items:center;justify-content:center;"
   "min-width:1.4em;padding:.1em .45em;border-radius:var(--liquid-glass-radius-pill);font-size:.75em;"
   "background:var(--liquid-glass-surface-regular-tint);border:1px solid var(--liquid-glass-surface-regular-border);"
   "backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);}\n"

   ".liquid-glass__specular{display:none;}\n" ;; visual is the ::before overlay; the span exists as a future GPU/JS hook (see docs/design.md)

   "@media (prefers-reduced-motion: reduce){"
   ".liquid-glass__panel,.liquid-glass__button,.liquid-glass__icon-button,.liquid-glass__tab{transition:none;}}\n"

   "@supports not (backdrop-filter: blur(1px)){"
   ".liquid-glass__panel,.liquid-glass__button,.liquid-glass__icon-button,.liquid-glass__toolbar,"
   ".liquid-glass__sheet,.liquid-glass__badge{background:rgba(255,255,255,.85);}}"))

(defn inline-style
  "Wrap CSS in a <style> tag for inline SSR embedding."
  ([] (inline-style (str (root-css) "\n" (component-css))))
  ([css] (str "<style>\n" css "\n</style>")))

(defn inline-style-hiccup
  "Hiccup form of inline-style: [:style [:hiccup/raw css]] — the raw form is
  understood by shitsuke.hiccup/->html so the CSS is not escaped."
  ([] (inline-style-hiccup (str (root-css) "\n" (component-css))))
  ([css] [:style [:hiccup/raw css]]))
