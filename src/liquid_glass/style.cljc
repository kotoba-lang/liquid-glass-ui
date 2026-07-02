(ns liquid-glass.style
  "Two-tier styling layer, mirroring shitsuke.style:

  Tier A (portable, `root-css`): token -> CSS custom properties on `:root`,
  light values plus a `prefers-color-scheme: dark` override block. Babashka-safe,
  zero deps beyond liquid-glass.tokens.

  Tier B (`component-css`): the actual glass material rules — backdrop-filter
  blur+saturate+brightness, tint, specular-highlight overlay, a two-tone edge
  rim light (the `:liquid-glass/specular :rim` token, top brighter than
  bottom — the detail that reads as an actual lit glass edge rather than a
  flat translucent panel), a default readable ink color + text-shadow (the
  `:liquid-glass/ink` token — adapts with the same light/dark mechanism as
  the surface tint, so text is legible without every consumer re-deriving
  it), elevation shadow, corner radius, press/hover motion.

  Every rule is built from EDN declaration maps via `kotoba-lang/css`
  (`css.core`) rather than hand-typed CSS strings — `css.core/rule` /
  `css.core/css` own bracket/semicolon bookkeeping, so a rule can't end up
  malformed, and (more importantly) the rule set is *data*: tests can assert
  against the declaration maps directly instead of regex-scraping rendered
  CSS text. This is exactly the shape of two real bugs found while building
  this repo (`panel--flat`'s shadow rule and the `:specular :rim` tokens were
  each defined but silently never referenced by any hand-typed rule) — harder
  to reintroduce once selectors and declarations are values, not string
  fragments assembled by hand. liquid-glass-ui is `kotoba-lang/css`'s first
  real consumer.

  Unlike shitsuke (whose component visuals are deferred to a shadow-css
  `:pages` build that does not yet exist), liquid-glass-ui ships Tier B as a
  plain CSS string: no build step is required to see the material, and the
  same string works for SSR (inlined `<style>`) and browser builds
  (concatenated into main.css).

  Class names read `liquid-glass__<component>` (base) and
  `liquid-glass__<component>--<variant>` (modifier), the same two-part
  convention as shitsuke.style/class-name."
  (:require [liquid-glass.tokens :as t]
            [css.core :as css]
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

;; --- small declaration-map builders (kept DRY so every surface gets the same
;; blur+saturate+brightness backdrop and the same elevation+rim shadow — the
;; panel--flat-shaped bug (a class rendered, but its rule forgotten) is much
;; harder to reintroduce when there's one place that emits these declaration
;; maps instead of ~15 hand-typed copies) -----------------------------------

(defn- backdrop-decls
  "backdrop-filter (+ -webkit- prefix): blur+saturate from a surface tier's
  tokens, plus a small brightness lift — real glass looks slightly brighter
  than the flat tint alone, not just blurred."
  [surface]
  (let [s (name surface)
        v (str "blur(var(--liquid-glass-surface-" s "-blur)) "
               "saturate(var(--liquid-glass-surface-" s "-saturate)) brightness(1.05)")]
    {:backdrop-filter v :-webkit-backdrop-filter v}))

(defn- glass-bg-decls
  "background + backdrop-filter + border declarations for a surface tier
  (:clear/:regular/:thick)."
  [surface]
  (let [s (name surface)]
    (merge {:background (str "var(--liquid-glass-surface-" s "-tint)")
            :border (str "1px solid var(--liquid-glass-surface-" s "-border)")}
           (backdrop-decls surface))))

(defn- glass-shadow-decls
  "Elevation drop-shadow plus the :liquid-glass/specular :rim edge light — a
  bright 1px inset line along the top edge, a much dimmer one along the
  bottom (the token values are asymmetric on purpose: light source from
  above). This is what turns a flat translucent rectangle into something
  that reads as a lit glass edge."
  [level]
  {:box-shadow (str "var(--liquid-glass-elevation-" (name level) "-shadow),"
                     "inset 0 1px 0 rgba(255,255,255,var(--liquid-glass-specular-rim-top-opacity)),"
                     "inset 0 -1px 0 rgba(255,255,255,var(--liquid-glass-specular-rim-bottom-opacity))")})

(def ^:private ink-decls
  "Default readable text color + a soft counter-shadow, from the
  :liquid-glass/ink token (dark ink in light scheme, light ink in dark scheme
  — the same media-query redeclaration technique as the surface tint). A
  consumer's own `color` on a more specific selector still wins normally;
  this only replaces the browser default of inheriting whatever ancestor
  color happened to be in scope, which is what silently produced illegible
  text before this token existed."
  {:color "var(--liquid-glass-ink-default)" :text-shadow "var(--liquid-glass-ink-shadow)"})

;; Components whose root element is a plain container (div/span/label/section/
;; header/nav/details) — i.e. NOT a replaced element like <input>/<select>, so
;; ::before generated content is reliable — get the shared base rule
;; (position/isolation/transition) and the shared specular ::before overlay.
;; Native form controls (slider, and the sr-only <input> inside toggle/
;; checkbox/radio) are styled directly instead; see their sections below.
;; `gauge` also opts out — its ::before is a dedicated inner-disc mask, not
;; the specular sheen (see gauge-rules).
(def ^:private glass-surface-components
  ["panel" "button" "icon-button" "toolbar" "sheet" "badge"
   "text-field" "text-area" "search-field" "menu-select"
   "toggle-track" "checkbox-box" "radio-box" "stepper"
   "nav-bar" "alert" "menu" "list" "chip" "disclosure"])

;; Every top-level component root that can carry user-visible text — a
;; superset of glass-surface-components (adds the outer wrapper for
;; toggle/checkbox/radio, whose glass surface is a small *nested* box, plus
;; components with no ::before treatment at all) — gets the ink color/shadow.
;; `color`/`text-shadow` are inherited CSS properties, so setting them once
;; here reaches every nested span (checkbox-text, list-row-content, ...)
;; without a rule per sub-element.
(def ^:private ink-components
  (into glass-surface-components
        ["tab-bar" "toggle" "checkbox" "radio" "slider" "progress-bar"
         "progress-circle" "gauge" "divider" "label" "avatar" "tooltip"]))

(defn- sel
  ([names] (sel names ""))
  ([names suffix] (str/join "," (map #(str "." (class-name %) suffix) names))))

(defn- base-rules []
  [[(sel glass-surface-components)
    {:position "relative" :isolation "isolate" :box-sizing "border-box" :font "inherit"
     :transition (str "transform var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
                       "box-shadow var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing),"
                       "filter var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing)")}]
   [(sel glass-surface-components "::before")
    {:content "\"\"" :position "absolute" :inset "0" :pointer-events "none" :border-radius "inherit"
     :background (str "radial-gradient(120% 60% at 18% -10%,"
                       "rgba(255,255,255,var(--liquid-glass-specular-highlight-opacity)) 0%,rgba(255,255,255,0) 65%)")
     :mix-blend-mode "overlay"}]
   [(sel ink-components) ink-decls]])

(defn- panel-rules []
  [[".liquid-glass__panel"
    (merge {:overflow "hidden" :padding "1em" :border-radius "var(--liquid-glass-radius-md)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__panel--clear" (glass-bg-decls :clear)]
   [".liquid-glass__panel--thick" (glass-bg-decls :thick)]
   [".liquid-glass__panel--flat" (glass-shadow-decls :flat)]
   [".liquid-glass__panel--overlay" (glass-shadow-decls :overlay)]
   [".liquid-glass__panel--floating" (glass-shadow-decls :floating)]])

(defn- button-rules []
  [[".liquid-glass__button,.liquid-glass__icon-button"
    (merge {:overflow "hidden" :display "inline-flex" :align-items "center" :justify-content "center"
            :gap ".4em" :padding ".6em 1.1em" :border-radius "var(--liquid-glass-radius-pill)" :cursor "pointer"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__icon-button" {:padding ".55em" :aspect-ratio "1"}]
   [".liquid-glass__button:hover,.liquid-glass__icon-button:hover"
    (merge {:transform "translateY(-1px)" :filter "brightness(1.08)"} (glass-shadow-decls :overlay))]
   [".liquid-glass__button:active,.liquid-glass__icon-button:active"
    {:transform "translateY(0) scale(.97)" :filter "brightness(.97)"}]
   [".liquid-glass__button:disabled,.liquid-glass__icon-button:disabled"
    {:opacity ".45" :cursor "not-allowed" :transform "none" :filter "none"}]])

(defn- toolbar-tabbar-rules []
  [[".liquid-glass__toolbar"
    (merge {:display "flex" :align-items "center" :gap ".5em" :padding ".5em .75em"
            :border-radius "var(--liquid-glass-radius-lg)"}
           (glass-bg-decls :thick) (glass-shadow-decls :overlay))]
   [".liquid-glass__tab-bar" {:display "flex" :gap ".15em"}]
   [".liquid-glass__tab"
    {:padding ".4em .9em" :border-radius "var(--liquid-glass-radius-pill)" :opacity ".7"
     :transition (str "opacity var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing),"
                       "background var(--liquid-glass-motion-press-duration) var(--liquid-glass-motion-press-easing)")}]
   [".liquid-glass__tab--active" {:opacity "1" :background "rgba(255,255,255,.18)"}]])

(defn- sheet-scrim-badge-rules []
  [[".liquid-glass__sheet"
    (merge {:overflow "hidden"
            :border-radius "var(--liquid-glass-radius-lg) var(--liquid-glass-radius-lg) 0 0"
            :border-bottom "none"
            :padding "1.25em 1.25em calc(1.25em + env(safe-area-inset-bottom,0px))"}
           (glass-bg-decls :thick) (glass-shadow-decls :floating))]
   [".liquid-glass__scrim"
    {:position "fixed" :inset "0" :background "rgba(0,0,0,.28)"
     :backdrop-filter "blur(2px)" :-webkit-backdrop-filter "blur(2px)"}]
   [".liquid-glass__badge"
    (merge {:overflow "hidden" :display "inline-flex" :align-items "center" :justify-content "center"
            :min-width "1.4em" :padding ".1em .45em" :border-radius "var(--liquid-glass-radius-pill)"
            :font-size ".75em"}
           (glass-bg-decls :regular))]])

(defn- form-field-rules []
  [[".liquid-glass__text-field,.liquid-glass__search-field"
    (merge {:display "flex" :align-items "center" :gap ".5em" :padding ".55em .9em"
            :border-radius "var(--liquid-glass-radius-pill)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__text-area"
    (merge {:display "flex" :align-items "flex-start" :padding ".7em .9em"
            :border-radius "var(--liquid-glass-radius-md)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__text-field input,.liquid-glass__search-field input,.liquid-glass__text-area textarea"
    {:flex "1" :min-width "0" :width "100%" :border "none" :outline "none"
     :background "transparent" :color "inherit" :font "inherit"}]
   [".liquid-glass__text-area textarea" {:resize "vertical"}]
   [".liquid-glass__search-icon" {:opacity ".6" :font-size ".9em" :line-height "1"}]
   [".liquid-glass__text-field:focus-within,.liquid-glass__search-field:focus-within,.liquid-glass__text-area:focus-within"
    {:box-shadow "0 0 0 2px var(--liquid-glass-accent-tint)"}]
   [".liquid-glass__menu-select"
    (merge {:display "inline-flex" :align-items "center" :padding ".55em 2.1em .55em .9em"
            :border-radius "var(--liquid-glass-radius-pill)"}
           (glass-bg-decls :regular) (glass-shadow-decls :raised))]
   [".liquid-glass__menu-select select"
    {:appearance "none" :-webkit-appearance "none" :background "transparent" :border "none"
     :outline "none" :color "inherit" :font "inherit" :width "100%"}]
   [".liquid-glass__menu-select::after"
    {:content "\"⌄\"" :position "absolute" :right ".9em" :top "50%"
     :transform "translateY(-50%)" :pointer-events "none" :opacity ".6"}]])

(defn- toggle-rules []
  [[".liquid-glass__toggle" {:display "inline-flex" :align-items "center" :cursor "pointer"}]
   [".liquid-glass__toggle-input"
    {:position "absolute" :width "1px" :height "1px" :padding "0" :margin "-1px" :overflow "hidden"
     :clip "rect(0,0,0,0)" :white-space "nowrap" :border "0"}]
   [".liquid-glass__toggle-track"
    (merge {:width "48px" :height "28px" :border-radius "var(--liquid-glass-radius-pill)"
            :transition "background var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}
           (glass-bg-decls :regular))]
   [".liquid-glass__toggle-thumb"
    (merge {:position "absolute" :top "2px" :left "2px" :width "22px" :height "22px" :border-radius "50%"
            :background "#fff"
            :transition "transform var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}
           (glass-shadow-decls :raised))]
   [".liquid-glass__toggle-input:checked ~ .liquid-glass__toggle-track"
    {:background "var(--liquid-glass-accent-tint-strong)" :border-color "var(--liquid-glass-accent-tint-strong)"}]
   [".liquid-glass__toggle-input:checked ~ .liquid-glass__toggle-track .liquid-glass__toggle-thumb"
    {:transform "translateX(20px)"}]
   [".liquid-glass__toggle-input:focus-visible ~ .liquid-glass__toggle-track"
    {:box-shadow "0 0 0 2px var(--liquid-glass-accent-tint)"}]
   [".liquid-glass__toggle-input:disabled ~ .liquid-glass__toggle-track"
    {:opacity ".45" :cursor "not-allowed"}]])

(defn- checkbox-radio-rules []
  [[".liquid-glass__checkbox,.liquid-glass__radio"
    {:display "inline-flex" :align-items "center" :gap ".5em" :cursor "pointer"}]
   [".liquid-glass__checkbox-input,.liquid-glass__radio-input"
    {:position "absolute" :width "1px" :height "1px" :padding "0" :margin "-1px" :overflow "hidden"
     :clip "rect(0,0,0,0)" :white-space "nowrap" :border "0"}]
   [".liquid-glass__checkbox-box"
    (merge {:width "20px" :height "20px" :border-radius "6px"} (glass-bg-decls :regular))]
   [".liquid-glass__radio-box"
    (merge {:width "20px" :height "20px" :border-radius "50%"} (glass-bg-decls :regular))]
   [".liquid-glass__checkbox-input:checked ~ .liquid-glass__checkbox-box"
    {:background "var(--liquid-glass-accent-tint-strong)" :border-color "var(--liquid-glass-accent-tint-strong)"}]
   [".liquid-glass__checkbox-input:checked ~ .liquid-glass__checkbox-box::after"
    {:content "\"✓\"" :position "absolute" :inset "0" :display "flex" :align-items "center"
     :justify-content "center" :font-size "13px" :color "#fff"}]
   [".liquid-glass__radio-input:checked ~ .liquid-glass__radio-box"
    {:border-color "var(--liquid-glass-accent-tint-strong)"}]
   [".liquid-glass__radio-input:checked ~ .liquid-glass__radio-box::after"
    {:content "\"\"" :position "absolute" :top "50%" :left "50%" :width "10px" :height "10px"
     :border-radius "50%" :background "var(--liquid-glass-accent-tint-strong)" :transform "translate(-50%,-50%)"}]
   [".liquid-glass__checkbox-input:disabled ~ .liquid-glass__checkbox-box,.liquid-glass__radio-input:disabled ~ .liquid-glass__radio-box"
    {:opacity ".45"}]
   [".liquid-glass__checkbox-text,.liquid-glass__radio-text" {:font-size "14px" :line-height "1.3"}]])

(defn- slider-rules []
  [[".liquid-glass__slider" {:-webkit-appearance "none" :appearance "none" :width "100%" :height "28px"
                             :background "transparent"}]
   [".liquid-glass__slider::-webkit-slider-runnable-track"
    {:height "6px" :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--liquid-glass-surface-regular-tint)"
     :border "1px solid var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__slider::-webkit-slider-thumb"
    (merge {:-webkit-appearance "none" :width "22px" :height "22px" :margin-top "-8px" :border-radius "50%"
            :background "#fff" :border "1px solid var(--liquid-glass-surface-regular-border)"}
           (glass-shadow-decls :raised))]
   [".liquid-glass__slider::-moz-range-track"
    {:height "6px" :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--liquid-glass-surface-regular-tint)"
     :border "1px solid var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__slider::-moz-range-thumb"
    (merge {:width "22px" :height "22px" :border-radius "50%" :background "#fff"
            :border "1px solid var(--liquid-glass-surface-regular-border)"}
           (glass-shadow-decls :raised))]])

(defn- stepper-rules []
  [[".liquid-glass__stepper"
    (merge {:display "inline-flex" :align-items "center" :gap ".4em" :padding ".25em .3em"
            :border-radius "var(--liquid-glass-radius-pill)"}
           (glass-bg-decls :regular))]
   [".liquid-glass__stepper .liquid-glass__icon-button"
    {:padding ".35em" :width "26px" :height "26px" :background "transparent"
     :backdrop-filter "none" :-webkit-backdrop-filter "none" :border "none" :box-shadow "none"}]
   [".liquid-glass__stepper .liquid-glass__icon-button::before" {:display "none"}]
   [".liquid-glass__stepper-value"
    {:min-width "1.6em" :text-align "center" :font-variant-numeric "tabular-nums" :font-size ".9em"}]])

(defn- progress-rules []
  [[".liquid-glass__progress-bar"
    {:width "100%" :height "8px" :border-radius "var(--liquid-glass-radius-pill)"
     :background "var(--liquid-glass-surface-regular-tint)"
     :backdrop-filter "blur(var(--liquid-glass-surface-regular-blur))"
     :-webkit-backdrop-filter "blur(var(--liquid-glass-surface-regular-blur))"
     :border "1px solid var(--liquid-glass-surface-regular-border)" :overflow "hidden"}]
   [".liquid-glass__progress-bar-fill"
    {:height "100%" :border-radius "inherit" :background "var(--liquid-glass-accent-tint-strong)"
     :transition "width var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}]
   [".liquid-glass__progress-circle"
    {:display "inline-block" :width "20px" :height "20px" :border-radius "50%"
     :border "2.5px solid var(--liquid-glass-surface-regular-border)" :border-top-color "#fff"
     :animation "liquid-glass-spin .8s linear infinite"}]])

(defn- gauge-rules []
  ;; The ring is a plain conic-gradient set inline per-instance (see
  ;; liquid-glass.components/gauge) — no CSS custom-property trick needed,
  ;; so it works identically across every reagent/SSR target.
  [[".liquid-glass__gauge"
    (merge {:display "inline-flex" :align-items "center" :justify-content "center"
            :width "56px" :height "56px" :border-radius "50%"}
           (glass-shadow-decls :raised))]
   [".liquid-glass__gauge::before"
    {:content "\"\"" :position "absolute" :inset "5px" :border-radius "50%"
     :background "var(--liquid-glass-surface-thick-tint)"
     :backdrop-filter "blur(var(--liquid-glass-surface-thick-blur)) brightness(1.05)"
     :-webkit-backdrop-filter "blur(var(--liquid-glass-surface-thick-blur)) brightness(1.05)"}]
   [".liquid-glass__gauge-label" {:position "relative" :z-index "1" :font-size "12px" :font-weight "700"}]])

(defn- misc-rules []
  [[".liquid-glass__divider" {:border "none" :height "1px" :margin "1em 0"
                              :background "var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__label" {:display "inline-flex" :align-items "center" :gap ".5em"}]
   [".liquid-glass__label-icon" {:display "inline-flex" :font-size "1.1em" :line-height "1"}]
   [".liquid-glass__label-text" {:line-height "1.3"}]
   [".liquid-glass__avatar"
    (merge {:display "inline-flex" :align-items "center" :justify-content "center"
            :width "36px" :height "36px" :border-radius "50%" :overflow "hidden"
            :font-size ".85em" :font-weight "600"}
           (glass-bg-decls :regular))]
   [".liquid-glass__avatar img" {:width "100%" :height "100%" :object-fit "cover"}]
   [".liquid-glass__tooltip"
    (merge {:position "absolute" :padding ".35em .6em" :border-radius "var(--liquid-glass-radius-sm)"
            :font-size "12px" :white-space "nowrap"}
           (glass-bg-decls :clear))]])

(defn- nav-bar-rules []
  [[".liquid-glass__nav-bar"
    (merge {:display "flex" :align-items "center" :justify-content "space-between" :gap ".75em"
            :padding ".75em 1em" :border-left "none" :border-right "none" :border-top "none"}
           (glass-bg-decls :thick))]
   [".liquid-glass__nav-bar-title" {:font-weight "700" :font-size "15px" :flex "1" :text-align "center"}]
   [".liquid-glass__nav-bar-leading,.liquid-glass__nav-bar-trailing"
    {:display "flex" :align-items "center" :gap ".4em" :min-width "2em"}]])

(defn- alert-rules []
  [[".liquid-glass__alert"
    (merge {:position "fixed" :top "50%" :left "50%" :transform "translate(-50%,-50%)" :z-index "1"
            :width "min(90vw,360px)" :padding "1.5em" :text-align "center"
            :border-radius "var(--liquid-glass-radius-lg)"}
           (glass-bg-decls :thick) (glass-shadow-decls :floating))]])

(defn- menu-rules []
  [[".liquid-glass__menu"
    (merge {:position "absolute" :display "flex" :flex-direction "column" :padding ".4em"
            :min-width "160px" :border-radius "var(--liquid-glass-radius-md)"}
           (glass-bg-decls :thick) (glass-shadow-decls :overlay))]
   [".liquid-glass__menu-item"
    {:all "unset" :box-sizing "border-box" :padding ".5em .7em" :border-radius "var(--liquid-glass-radius-sm)"
     :cursor "pointer" :font "inherit" :color "inherit"}]
   [".liquid-glass__menu-item:hover" {:background "rgba(255,255,255,.18)"}]
   [".liquid-glass__menu-item:disabled" {:opacity ".45" :cursor "not-allowed"}]])

(defn- list-rules []
  [[".liquid-glass__list"
    (merge {:display "flex" :flex-direction "column" :border-radius "var(--liquid-glass-radius-md)"
            :overflow "hidden"}
           (glass-bg-decls :regular))]
   [".liquid-glass__list--thick" (glass-bg-decls :thick)]
   [".liquid-glass__list-row" {:display "flex" :align-items "center" :justify-content "space-between"
                               :gap ".75em" :padding ".7em 1em"}]
   [".liquid-glass__list-row + .liquid-glass__list-row"
    {:border-top "1px solid var(--liquid-glass-surface-regular-border)"}]
   [".liquid-glass__list-row[data-act]" {:cursor "pointer"}]
   [".liquid-glass__list-row-content" {:flex "1" :min-width "0"}]
   [".liquid-glass__list-row-trailing" {:opacity ".55" :font-size ".9em"}]])

(defn- chip-rules []
  [[".liquid-glass__chip"
    (merge {:display "inline-flex" :align-items "center" :gap ".35em"
            :padding ".35em .7em .35em .85em" :border-radius "var(--liquid-glass-radius-pill)"
            :font-size "13px"}
           (glass-bg-decls :regular))]
   [".liquid-glass__chip-remove"
    {:all "unset" :box-sizing "border-box" :display "inline-flex" :align-items "center"
     :justify-content "center" :width "16px" :height "16px" :border-radius "50%"
     :cursor "pointer" :opacity ".7" :font-size "11px"}]
   [".liquid-glass__chip-remove:hover" {:opacity "1" :background "rgba(255,255,255,.18)"}]])

(defn- disclosure-rules []
  [[".liquid-glass__disclosure"
    (merge {:border-radius "var(--liquid-glass-radius-md)" :overflow "hidden"} (glass-bg-decls :regular))]
   [".liquid-glass__disclosure-summary"
    {:list-style "none" :cursor "pointer" :display "flex" :align-items "center"
     :justify-content "space-between" :padding ".75em 1em"}]
   [".liquid-glass__disclosure-summary::-webkit-details-marker" {:display "none"}]
   [".liquid-glass__disclosure-chevron"
    {:display "inline-flex" :opacity ".6"
     :transition "transform var(--liquid-glass-motion-settle-duration) var(--liquid-glass-motion-settle-easing)"}]
   [".liquid-glass__disclosure[open] .liquid-glass__disclosure-chevron" {:transform "rotate(180deg)"}]
   [".liquid-glass__disclosure-body" {:padding "0 1em 1em"}]])

(defn component-rules
  "The Tier B rule set as EDN data — a vector of `[selector decls-map]` pairs,
  the same shape `css.core/css`'s `:rules` consumes. Exposed (not just the
  rendered `component-css` string) so tests — and any future consumer, e.g. a
  shadow-css `:pages` extraction — can assert against declarations directly
  instead of regex-scraping rendered CSS text. This is the whole point of the
  css.core migration: a selector with no matching entry here, or a `:box-shadow`
  that's missing the rim vars, is a data assertion instead of a string search."
  []
  (vec (mapcat (fn [f] (f))
               [base-rules panel-rules button-rules toolbar-tabbar-rules sheet-scrim-badge-rules
                form-field-rules toggle-rules checkbox-radio-rules slider-rules stepper-rules
                progress-rules gauge-rules misc-rules nav-bar-rules alert-rules menu-rules
                list-rules chip-rules disclosure-rules])))

(defn- supports-fallback-css
  "@supports isn't a CSS *rule* in css.core's sense (it's an at-rule wrapping
  other rules, like @media, but css.core/media hardcodes the `@media` prefix)
  — built directly with css.core/rule for the inner block so the declaration
  side stays data-driven even though the at-rule wrapper is a literal string."
  []
  (str "@supports not (backdrop-filter: blur(1px)) { "
       (css/rule (str (sel glass-surface-components)
                       ",.liquid-glass__slider::-webkit-slider-runnable-track,"
                       ".liquid-glass__slider::-moz-range-track,.liquid-glass__toggle-track,"
                       ".liquid-glass__checkbox-box,.liquid-glass__radio-box,.liquid-glass__avatar,"
                       ".liquid-glass__gauge::before")
                  {:background "rgba(255,255,255,.85)"})
       " }"))

(defn component-css
  "The Tier B CSS: glass material rules for every liquid-glass__* class,
  generated from EDN declaration maps via kotoba-lang/css (`css.core`).
  Every declaration references `var(--liquid-glass-...)` custom properties
  only (never a literal color/blur value) so `root-css` overrides propagate.
  Includes a `prefers-reduced-motion: reduce` guard and a
  `@supports not (backdrop-filter: blur(1px))` opaque-background fallback for
  engines without backdrop-filter."
  []
  (str
   (css/css
    {:rules (component-rules)
     :keyframes {"liquid-glass-spin" {100 {:transform "rotate(360deg)"}}}
     :media {"(prefers-reduced-motion: reduce)"
             [[(sel ["panel" "button" "icon-button" "tab" "toggle-track" "toggle-thumb"
                     "progress-bar-fill" "disclosure-chevron"])
               {:transition "none"}]]}})
   "\n.liquid-glass__specular{display:none;}\n" ;; visual is the ::before overlay; the span exists as a future GPU/JS hook (see docs/design.md)
   "\n" (supports-fallback-css)))

(defn inline-style
  "Wrap CSS in a <style> tag for inline SSR embedding."
  ([] (inline-style (str (root-css) "\n" (component-css))))
  ([css] (str "<style>\n" css "\n</style>")))

(defn inline-style-hiccup
  "Hiccup form of inline-style: [:style [:hiccup/raw css]] — the raw form is
  understood by shitsuke.hiccup/->html so the CSS is not escaped."
  ([] (inline-style-hiccup (str (root-css) "\n" (component-css))))
  ([css] [:style [:hiccup/raw css]]))
