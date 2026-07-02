(ns liquid-glass.style
  "Two-tier styling layer, mirroring shitsuke.style:

  Tier A (portable, `root-css`): token -> CSS custom properties on `:root`,
  light values plus a `prefers-color-scheme: dark` override block. Babashka-safe,
  zero deps beyond liquid-glass.tokens.

  Tier B (`component-css`): the actual glass material rules — backdrop-filter
  blur+saturate+brightness, tint, specular-highlight overlay, a two-tone edge
  rim light (the `:liquid-glass/specular :rim` token, top brighter than
  bottom — the detail that reads as an actual lit glass edge rather than a
  flat translucent panel), elevation shadow, corner radius, press/hover
  motion — as a single portable literal CSS string scoped to stable
  `liquid-glass__<component>` classes. Unlike shitsuke (which defers its
  component visuals to a shadow-css `:pages` build that does not yet exist),
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

;; --- small CSS-fragment builders (kept DRY so every surface gets the same
;; blur+saturate+brightness backdrop and the same elevation+rim shadow — the
;; panel--flat-shaped bug (a class rendered, but its CSS forgotten) is much
;; harder to reintroduce when there's one place that emits these fragments
;; instead of ~15 hand-typed copies) ---------------------------------------

(defn- backdrop
  "backdrop-filter (+ -webkit- prefix): blur+saturate from a surface tier's
  tokens, plus a small brightness lift — real glass looks slightly brighter
  than the flat tint alone, not just blurred."
  [surface]
  (let [s (name surface)]
    (str "backdrop-filter:blur(var(--liquid-glass-surface-" s "-blur)) "
         "saturate(var(--liquid-glass-surface-" s "-saturate)) brightness(1.05);"
         "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-" s "-blur)) "
         "saturate(var(--liquid-glass-surface-" s "-saturate)) brightness(1.05);")))

(defn- glass-bg
  "background + backdrop-filter + border for a surface tier (:clear/:regular/:thick)."
  [surface]
  (let [s (name surface)]
    (str "background:var(--liquid-glass-surface-" s "-tint);"
         (backdrop surface)
         "border:1px solid var(--liquid-glass-surface-" s "-border);")))

(defn- glass-shadow
  "Elevation drop-shadow plus the :liquid-glass/specular :rim edge light — a
  bright 1px inset line along the top edge, a much dimmer one along the
  bottom (the token values are asymmetric on purpose: light source from
  above). This is what turns a flat translucent rectangle into something
  that reads as a lit glass edge."
  [level]
  (str "box-shadow:var(--liquid-glass-elevation-" (name level) "-shadow),"
       "inset 0 1px 0 rgba(255,255,255,var(--liquid-glass-specular-rim-top-opacity)),"
       "inset 0 -1px 0 rgba(255,255,255,var(--liquid-glass-specular-rim-bottom-opacity));"))

;; Components whose root element is a plain container (div/span/label/section/
;; header/nav/details) — i.e. NOT a replaced element like <input>/<select>, so
;; ::before generated content is reliable — get the shared base rule
;; (position/isolation/transition) and the shared specular ::before overlay.
;; Native form controls (slider, and the sr-only <input> inside toggle/
;; checkbox/radio) are styled directly instead; see their sections below.
;; `gauge` also opts out — its ::before is a dedicated inner-disc mask, not
;; the specular sheen (see gauge-css).
(def ^:private glass-surface-components
  ["panel" "button" "icon-button" "toolbar" "sheet" "badge"
   "text-field" "text-area" "search-field" "menu-select"
   "toggle-track" "checkbox-box" "radio-box" "stepper"
   "nav-bar" "alert" "menu" "list" "chip" "disclosure"])

(defn- sel
  ([names] (sel names ""))
  ([names suffix] (str/join "," (map #(str "." (class-name %) suffix) names))))

(defn- base-css []
  (str
   (sel glass-surface-components) "{"
   "position:relative;isolation:isolate;box-sizing:border-box;color:inherit;font:inherit;"
   "transition:transform var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
   "box-shadow var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing),"
   "filter var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing);}\n"
   (sel glass-surface-components "::before") "{"
   "content:\"\";position:absolute;inset:0;pointer-events:none;border-radius:inherit;"
   "background:radial-gradient(120% 60% at 18% -10%,"
   "rgba(255,255,255,var(--liquid-glass-specular-highlight-opacity)) 0%,rgba(255,255,255,0) 65%);"
   "mix-blend-mode:overlay;}\n"))

(defn- panel-css []
  (str
   ".liquid-glass__panel{overflow:hidden;padding:1em;border-radius:var(--liquid-glass-radius-md);"
   (glass-bg :regular) (glass-shadow :raised) "}\n"
   ".liquid-glass__panel--clear{" (glass-bg :clear) "}\n"
   ".liquid-glass__panel--thick{" (glass-bg :thick) "}\n"
   ".liquid-glass__panel--flat{" (glass-shadow :flat) "}\n"
   ".liquid-glass__panel--overlay{" (glass-shadow :overlay) "}\n"
   ".liquid-glass__panel--floating{" (glass-shadow :floating) "}\n"))

(defn- button-css []
  (str
   ".liquid-glass__button,.liquid-glass__icon-button{overflow:hidden;display:inline-flex;align-items:center;"
   "justify-content:center;gap:.4em;padding:.6em 1.1em;border-radius:var(--liquid-glass-radius-pill);cursor:pointer;"
   (glass-bg :regular) (glass-shadow :raised) "}\n"
   ".liquid-glass__icon-button{padding:.55em;aspect-ratio:1;}\n"
   ".liquid-glass__button:hover,.liquid-glass__icon-button:hover{transform:translateY(-1px);filter:brightness(1.08);"
   (glass-shadow :overlay) "}\n"
   ".liquid-glass__button:active,.liquid-glass__icon-button:active{transform:translateY(0) scale(.97);filter:brightness(.97);}\n"
   ".liquid-glass__button:disabled,.liquid-glass__icon-button:disabled{opacity:.45;cursor:not-allowed;"
   "transform:none;filter:none;}\n"))

(defn- toolbar-tabbar-css []
  (str
   ".liquid-glass__toolbar{display:flex;align-items:center;gap:.5em;padding:.5em .75em;"
   "border-radius:var(--liquid-glass-radius-lg);" (glass-bg :thick) (glass-shadow :overlay) "}\n"
   ".liquid-glass__tab-bar{display:flex;gap:.15em;}\n"
   ".liquid-glass__tab{padding:.4em .9em;border-radius:var(--liquid-glass-radius-pill);"
   "opacity:.7;transition:opacity var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
   "background var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing);}\n"
   ".liquid-glass__tab--active{opacity:1;background:rgba(255,255,255,.18);}\n"))

(defn- sheet-scrim-badge-css []
  (str
   ".liquid-glass__sheet{overflow:hidden;border-radius:var(--liquid-glass-radius-lg) var(--liquid-glass-radius-lg) 0 0;"
   (glass-bg :thick) "border-bottom:none;" (glass-shadow :floating)
   "padding:1.25em 1.25em calc(1.25em + env(safe-area-inset-bottom,0px));}\n"
   ".liquid-glass__scrim{position:fixed;inset:0;background:rgba(0,0,0,.28);backdrop-filter:blur(2px);"
   "-webkit-backdrop-filter:blur(2px);}\n"
   ".liquid-glass__badge{overflow:hidden;display:inline-flex;align-items:center;justify-content:center;"
   "min-width:1.4em;padding:.1em .45em;border-radius:var(--liquid-glass-radius-pill);font-size:.75em;"
   (glass-bg :regular) "}\n"))

(defn- form-field-css []
  (str
   ".liquid-glass__text-field,.liquid-glass__search-field{display:flex;align-items:center;gap:.5em;"
   "padding:.55em .9em;border-radius:var(--liquid-glass-radius-pill);"
   (glass-bg :regular) (glass-shadow :raised) "}\n"
   ".liquid-glass__text-area{display:flex;align-items:flex-start;padding:.7em .9em;"
   "border-radius:var(--liquid-glass-radius-md);" (glass-bg :regular) (glass-shadow :raised) "}\n"
   ".liquid-glass__text-field input,.liquid-glass__search-field input,.liquid-glass__text-area textarea{"
   "flex:1;min-width:0;width:100%;border:none;outline:none;background:transparent;color:inherit;font:inherit;}\n"
   ".liquid-glass__text-area textarea{resize:vertical;}\n"
   ".liquid-glass__search-icon{opacity:.6;font-size:.9em;line-height:1;}\n"
   ".liquid-glass__text-field:focus-within,.liquid-glass__search-field:focus-within,"
   ".liquid-glass__text-area:focus-within{box-shadow:0 0 0 2px var(--liquid-glass-accent-tint);}\n"
   ".liquid-glass__menu-select{display:inline-flex;align-items:center;padding:.55em 2.1em .55em .9em;"
   "border-radius:var(--liquid-glass-radius-pill);" (glass-bg :regular) (glass-shadow :raised) "}\n"
   ".liquid-glass__menu-select select{appearance:none;-webkit-appearance:none;background:transparent;"
   "border:none;outline:none;color:inherit;font:inherit;width:100%;}\n"
   ".liquid-glass__menu-select::after{content:\"⌄\";position:absolute;right:.9em;top:50%;"
   "transform:translateY(-50%);pointer-events:none;opacity:.6;}\n"))

(defn- toggle-css []
  (str
   ".liquid-glass__toggle{display:inline-flex;align-items:center;cursor:pointer;}\n"
   ".liquid-glass__toggle-input{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;"
   "clip:rect(0,0,0,0);white-space:nowrap;border:0;}\n"
   ".liquid-glass__toggle-track{width:48px;height:28px;border-radius:var(--liquid-glass-radius-pill);"
   (glass-bg :regular)
   "transition:background var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing);}\n"
   ".liquid-glass__toggle-thumb{position:absolute;top:2px;left:2px;width:22px;height:22px;border-radius:50%;"
   "background:#fff;" (glass-shadow :raised)
   "transition:transform var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing);}\n"
   ".liquid-glass__toggle-input:checked ~ .liquid-glass__toggle-track{background:var(--liquid-glass-accent-tint-strong);"
   "border-color:var(--liquid-glass-accent-tint-strong);}\n"
   ".liquid-glass__toggle-input:checked ~ .liquid-glass__toggle-track .liquid-glass__toggle-thumb{"
   "transform:translateX(20px);}\n"
   ".liquid-glass__toggle-input:focus-visible ~ .liquid-glass__toggle-track{"
   "box-shadow:0 0 0 2px var(--liquid-glass-accent-tint);}\n"
   ".liquid-glass__toggle-input:disabled ~ .liquid-glass__toggle-track{opacity:.45;cursor:not-allowed;}\n"))

(defn- checkbox-radio-css []
  (str
   ".liquid-glass__checkbox,.liquid-glass__radio{display:inline-flex;align-items:center;gap:.5em;cursor:pointer;}\n"
   ".liquid-glass__checkbox-input,.liquid-glass__radio-input{position:absolute;width:1px;height:1px;padding:0;"
   "margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;}\n"
   ".liquid-glass__checkbox-box{width:20px;height:20px;border-radius:6px;" (glass-bg :regular) "}\n"
   ".liquid-glass__radio-box{width:20px;height:20px;border-radius:50%;" (glass-bg :regular) "}\n"
   ".liquid-glass__checkbox-input:checked ~ .liquid-glass__checkbox-box{"
   "background:var(--liquid-glass-accent-tint-strong);border-color:var(--liquid-glass-accent-tint-strong);}\n"
   ".liquid-glass__checkbox-input:checked ~ .liquid-glass__checkbox-box::after{content:\"✓\";position:absolute;"
   "inset:0;display:flex;align-items:center;justify-content:center;font-size:13px;color:#fff;}\n"
   ".liquid-glass__radio-input:checked ~ .liquid-glass__radio-box{border-color:var(--liquid-glass-accent-tint-strong);}\n"
   ".liquid-glass__radio-input:checked ~ .liquid-glass__radio-box::after{content:\"\";position:absolute;top:50%;"
   "left:50%;width:10px;height:10px;border-radius:50%;background:var(--liquid-glass-accent-tint-strong);"
   "transform:translate(-50%,-50%);}\n"
   ".liquid-glass__checkbox-input:disabled ~ .liquid-glass__checkbox-box,"
   ".liquid-glass__radio-input:disabled ~ .liquid-glass__radio-box{opacity:.45;}\n"
   ".liquid-glass__checkbox-text,.liquid-glass__radio-text{font-size:14px;line-height:1.3;}\n"))

(defn- slider-css []
  (str
   ".liquid-glass__slider{-webkit-appearance:none;appearance:none;width:100%;height:28px;background:transparent;}\n"
   ".liquid-glass__slider::-webkit-slider-runnable-track{height:6px;border-radius:var(--liquid-glass-radius-pill);"
   "background:var(--liquid-glass-surface-regular-tint);border:1px solid var(--liquid-glass-surface-regular-border);}\n"
   ".liquid-glass__slider::-webkit-slider-thumb{-webkit-appearance:none;width:22px;height:22px;margin-top:-8px;"
   "border-radius:50%;background:#fff;border:1px solid var(--liquid-glass-surface-regular-border);"
   "box-shadow:var(--liquid-glass-elevation-raised-shadow);}\n"
   ".liquid-glass__slider::-moz-range-track{height:6px;border-radius:var(--liquid-glass-radius-pill);"
   "background:var(--liquid-glass-surface-regular-tint);border:1px solid var(--liquid-glass-surface-regular-border);}\n"
   ".liquid-glass__slider::-moz-range-thumb{width:22px;height:22px;border-radius:50%;background:#fff;"
   "border:1px solid var(--liquid-glass-surface-regular-border);box-shadow:var(--liquid-glass-elevation-raised-shadow);}\n"))

(defn- stepper-css []
  (str
   ".liquid-glass__stepper{display:inline-flex;align-items:center;gap:.4em;padding:.25em .3em;"
   "border-radius:var(--liquid-glass-radius-pill);" (glass-bg :regular) "}\n"
   ".liquid-glass__stepper .liquid-glass__icon-button{padding:.35em;width:26px;height:26px;background:transparent;"
   "backdrop-filter:none;-webkit-backdrop-filter:none;border:none;box-shadow:none;}\n"
   ".liquid-glass__stepper .liquid-glass__icon-button::before{display:none;}\n"
   ".liquid-glass__stepper-value{min-width:1.6em;text-align:center;font-variant-numeric:tabular-nums;font-size:.9em;}\n"))

(defn- progress-css []
  (str
   ".liquid-glass__progress-bar{width:100%;height:8px;border-radius:var(--liquid-glass-radius-pill);"
   "background:var(--liquid-glass-surface-regular-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-regular-blur));"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-regular-blur));"
   "border:1px solid var(--liquid-glass-surface-regular-border);overflow:hidden;}\n"
   ".liquid-glass__progress-bar-fill{height:100%;border-radius:inherit;background:var(--liquid-glass-accent-tint-strong);"
   "transition:width var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing);}\n"
   ".liquid-glass__progress-circle{display:inline-block;width:20px;height:20px;border-radius:50%;"
   "border:2.5px solid var(--liquid-glass-surface-regular-border);border-top-color:#fff;"
   "animation:liquid-glass-spin .8s linear infinite;}\n"
   "@keyframes liquid-glass-spin{to{transform:rotate(360deg);}}\n"))

(defn- gauge-css []
  (str
   ;; The ring is a plain conic-gradient set inline per-instance (see
   ;; liquid-glass.components/gauge) — no CSS custom-property trick needed,
   ;; so it works identically across every reagent/SSR target.
   ".liquid-glass__gauge{position:relative;display:inline-flex;align-items:center;justify-content:center;"
   "width:56px;height:56px;border-radius:50%;" (glass-shadow :raised) "}\n"
   ".liquid-glass__gauge::before{content:\"\";position:absolute;inset:5px;border-radius:50%;"
   "background:var(--liquid-glass-surface-thick-tint);"
   "backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) brightness(1.05);"
   "-webkit-backdrop-filter:blur(var(--liquid-glass-surface-thick-blur)) brightness(1.05);}\n"
   ".liquid-glass__gauge-label{position:relative;z-index:1;font-size:12px;font-weight:700;}\n"))

(defn- misc-css []
  (str
   ".liquid-glass__divider{border:none;height:1px;margin:1em 0;background:var(--liquid-glass-surface-regular-border);}\n"
   ".liquid-glass__label{display:inline-flex;align-items:center;gap:.5em;}\n"
   ".liquid-glass__label-icon{display:inline-flex;font-size:1.1em;line-height:1;}\n"
   ".liquid-glass__label-text{line-height:1.3;}\n"
   ".liquid-glass__avatar{display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;"
   "border-radius:50%;overflow:hidden;font-size:.85em;font-weight:600;" (glass-bg :regular) "}\n"
   ".liquid-glass__avatar img{width:100%;height:100%;object-fit:cover;}\n"
   ".liquid-glass__tooltip{position:absolute;padding:.35em .6em;border-radius:var(--liquid-glass-radius-sm);"
   "font-size:12px;white-space:nowrap;" (glass-bg :clear) "}\n"))

(defn- nav-bar-css []
  (str
   ".liquid-glass__nav-bar{display:flex;align-items:center;justify-content:space-between;gap:.75em;"
   "padding:.75em 1em;" (glass-bg :thick) "border-left:none;border-right:none;border-top:none;}\n"
   ".liquid-glass__nav-bar-title{font-weight:700;font-size:15px;flex:1;text-align:center;}\n"
   ".liquid-glass__nav-bar-leading,.liquid-glass__nav-bar-trailing{display:flex;align-items:center;gap:.4em;"
   "min-width:2em;}\n"))

(defn- alert-css []
  (str
   ".liquid-glass__alert{position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);z-index:1;"
   "width:min(90vw,360px);padding:1.5em;text-align:center;border-radius:var(--liquid-glass-radius-lg);"
   (glass-bg :thick) (glass-shadow :floating) "}\n"))

(defn- menu-css []
  (str
   ".liquid-glass__menu{position:absolute;display:flex;flex-direction:column;padding:.4em;min-width:160px;"
   "border-radius:var(--liquid-glass-radius-md);" (glass-bg :thick) (glass-shadow :overlay) "}\n"
   ".liquid-glass__menu-item{all:unset;box-sizing:border-box;padding:.5em .7em;border-radius:var(--liquid-glass-radius-sm);"
   "cursor:pointer;font:inherit;color:inherit;}\n"
   ".liquid-glass__menu-item:hover{background:rgba(255,255,255,.18);}\n"
   ".liquid-glass__menu-item:disabled{opacity:.45;cursor:not-allowed;}\n"))

(defn- list-css []
  (str
   ".liquid-glass__list{display:flex;flex-direction:column;border-radius:var(--liquid-glass-radius-md);"
   "overflow:hidden;" (glass-bg :regular) "}\n"
   ".liquid-glass__list--thick{" (glass-bg :thick) "}\n"
   ".liquid-glass__list-row{display:flex;align-items:center;justify-content:space-between;gap:.75em;padding:.7em 1em;}\n"
   ".liquid-glass__list-row + .liquid-glass__list-row{border-top:1px solid var(--liquid-glass-surface-regular-border);}\n"
   ".liquid-glass__list-row[data-act]{cursor:pointer;}\n"
   ".liquid-glass__list-row-content{flex:1;min-width:0;}\n"
   ".liquid-glass__list-row-trailing{opacity:.55;font-size:.9em;}\n"))

(defn- chip-css []
  (str
   ".liquid-glass__chip{display:inline-flex;align-items:center;gap:.35em;padding:.35em .7em .35em .85em;"
   "border-radius:var(--liquid-glass-radius-pill);font-size:13px;" (glass-bg :regular) "}\n"
   ".liquid-glass__chip-remove{all:unset;box-sizing:border-box;display:inline-flex;align-items:center;"
   "justify-content:center;width:16px;height:16px;border-radius:50%;cursor:pointer;opacity:.7;font-size:11px;}\n"
   ".liquid-glass__chip-remove:hover{opacity:1;background:rgba(255,255,255,.18);}\n"))

(defn- disclosure-css []
  (str
   ".liquid-glass__disclosure{border-radius:var(--liquid-glass-radius-md);overflow:hidden;" (glass-bg :regular) "}\n"
   ".liquid-glass__disclosure-summary{list-style:none;cursor:pointer;display:flex;align-items:center;"
   "justify-content:space-between;padding:.75em 1em;}\n"
   ".liquid-glass__disclosure-summary::-webkit-details-marker{display:none;}\n"
   ".liquid-glass__disclosure-chevron{display:inline-flex;opacity:.6;"
   "transition:transform var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing);}\n"
   ".liquid-glass__disclosure[open] .liquid-glass__disclosure-chevron{transform:rotate(180deg);}\n"
   ".liquid-glass__disclosure-body{padding:0 1em 1em;}\n"))

(defn- guards-css []
  (str
   ".liquid-glass__specular{display:none;}\n" ;; visual is the ::before overlay; the span exists as a future GPU/JS hook (see docs/design.md)

   "@media (prefers-reduced-motion: reduce){"
   ".liquid-glass__panel,.liquid-glass__button,.liquid-glass__icon-button,.liquid-glass__tab,"
   ".liquid-glass__toggle-track,.liquid-glass__toggle-thumb,.liquid-glass__progress-bar-fill,"
   ".liquid-glass__disclosure-chevron{transition:none;}}\n"

   "@supports not (backdrop-filter: blur(1px)){"
   (sel glass-surface-components) ",.liquid-glass__slider::-webkit-slider-runnable-track,"
   ".liquid-glass__slider::-moz-range-track,.liquid-glass__toggle-track,.liquid-glass__checkbox-box,"
   ".liquid-glass__radio-box,.liquid-glass__avatar,.liquid-glass__gauge::before"
   "{background:rgba(255,255,255,.85);}}\n"))

(defn component-css
  "The Tier B literal CSS: glass material rules for every liquid-glass__*
  class, referencing only var(--liquid-glass-...) custom properties (never a
  literal color/blur value) so root-css overrides propagate. Includes a
  reduced-motion guard and a no-backdrop-filter fallback (older engines)."
  []
  (str (base-css) (panel-css) (button-css) (toolbar-tabbar-css) (sheet-scrim-badge-css)
       (form-field-css) (toggle-css) (checkbox-radio-css) (slider-css) (stepper-css)
       (progress-css) (gauge-css) (misc-css) (nav-bar-css) (alert-css) (menu-css) (list-css)
       (chip-css) (disclosure-css) (guards-css)))

(defn inline-style
  "Wrap CSS in a <style> tag for inline SSR embedding."
  ([] (inline-style (str (root-css) "\n" (component-css))))
  ([css] (str "<style>\n" css "\n</style>")))

(defn inline-style-hiccup
  "Hiccup form of inline-style: [:style [:hiccup/raw css]] — the raw form is
  understood by shitsuke.hiccup/->html so the CSS is not escaped."
  ([] (inline-style-hiccup (str (root-css) "\n" (component-css))))
  ([css] [:style [:hiccup/raw css]]))
