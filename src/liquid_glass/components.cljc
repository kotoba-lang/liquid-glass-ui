(ns liquid-glass.components
  "Pure-hiccup liquid-glass primitives (.cljc, no reagent import) — the dual-render
  contract inherited from shitsuke: the same hiccup data renders via
  shitsuke.hiccup/->html (SSR) or reagent (browser).

  liquid-glass-ui owns no interaction/state logic of its own. Every component
  either wraps the matching `shitsuke.components` fn (button/icon-button/
  toolbar/mode-tabs/card/input/textarea/select keep their exact `act`/
  `:on-input`/etc. contract; see shitsuke.components docstrings) or, where
  shitsuke has no equivalent, is a small hiccup literal following the same
  `data-act` convention.

  Every component whose top-level element IS the glass surface (panel/button/
  toolbar/sheet/text-field/menu-select/nav-bar/alert/menu/list/stepper/…)
  appends a `liquid-glass__specular` marker span (currently a no-op
  display:none anchor driven purely by the ::before CSS overlay in
  liquid-glass.style/component-css; reserved as the hook a future
  pointer-tracking enhancer or GPU/WebGPU refraction layer would target — see
  docs/design.md #future-work). Compound controls whose glass surface is a
  small *nested* box rather than the top-level element (toggle/checkbox/radio
  — the surface is the track/box span inside a `<label>`, and the native
  `<input>` needs to be the label's first child for click-to-toggle to work)
  skip the marker span rather than force it into a ~20px box; the CSS
  `::before` overlay on `-track`/`-box` still renders the glass sheen without it."
  (:require [shitsuke.components :as sc]
            [liquid-glass.style :as s]
            [liquid-glass.tokens :as t]
            [clojure.string :as str]))

(defn- act->str
  [a]
  (cond
    (nil? a) nil
    (string? a) a
    (keyword? a) (if-let [ns (namespace a)] (str ns "/" (name a)) (name a))
    :else (str a)))

(defn- add-class [attrs extra]
  (update attrs :class (fn [c] (if (seq c) (str c " " extra) extra))))

(defn- cls
  "Join a required liquid-glass class with an optional consumer :class opt."
  [base extra]
  (if (seq extra) (str base " " extra) base))

(defn- specular []
  [:span {:aria-hidden true :class (s/class-name :specular)}])

(defn- glassify
  "Take a hiccup node `[tag attrs & children]` (as returned by a shitsuke.components
  fn), add `lg-class` to its :class, and append the specular decoration span as
  a final child."
  [[tag attrs & children] lg-class]
  (into [tag (add-class attrs lg-class)] (concat children [(specular)])))

(defn button
  "Glass pill button. Same opts as shitsuke.components/button (:act, :disabled,
  :title, :type, :class)."
  ([label] (button label nil))
  ([label opts] (glassify (sc/button label opts) (s/class-name :button))))

(defn icon-button
  "Glass icon button. Same opts as shitsuke.components/icon-button."
  ([icon] (icon-button icon nil))
  ([icon opts] (glassify (sc/icon-button icon opts) (s/class-name :icon-button))))

(defn toolbar
  "Glass toolbar / floating navbar. `actions` is a seq of hiccup (typically
  liquid-glass/button or icon-button). Same opts as shitsuke.components/toolbar."
  ([actions] (toolbar actions nil))
  ([actions opts] (glassify (sc/toolbar actions opts) (s/class-name :toolbar))))

(defn tab-bar
  "Glass segmented control. `tabs` is [id label] pairs; `current` is the active
  id. Same shape as shitsuke.components/mode-tabs (same `data-act` per-tab
  contract) but classed entirely under `liquid-glass__tab`/`tab--active` — the
  visual is fully owned by liquid-glass.style, so it does not also carry the
  shitsuke__mode-tabs/tab classes. opts: :class (container)."
  ([tabs current] (tab-bar tabs current nil))
  ([tabs current opts]
   (let [{:keys [class]} opts]
     [:nav {:class (str (s/class-name :tab-bar) (when class (str " " class)))}
      (for [[id label] tabs]
        [:button {:key (name id)
                  :type "button"
                  :class (str (s/class-name :tab)
                              (when (= id current) (str " " (s/class-name :tab--active))))
                  :data-act (some-> id act->str)}
         label])
      (specular)])))

(defn panel
  "Glass surface container. `body` is hiccup or a seq. opts: :class, :id,
  :surface (:clear | :regular (default) | :thick), :elevation (:flat | :raised
  (default) | :overlay | :floating)."
  ([body] (panel body nil))
  ([body opts]
   (let [{:keys [surface elevation]} opts
         base (sc/card body (dissoc opts :surface :elevation))
         variants (cond-> [(s/class-name :panel)]
                    (and surface (not= surface :regular))
                    (conj (s/class-name (str "panel--" (name surface))))
                    (and elevation (not= elevation :raised))
                    (conj (s/class-name (str "panel--" (name elevation)))))]
     (glassify base (str/join " " variants)))))

(defn sheet
  "Bottom sheet / floating modal surface. `body` is hiccup or a seq. opts:
  :id, :class, :label (aria-label)."
  ([body] (sheet body nil))
  ([body opts]
   (let [{:keys [id class label]} opts]
     [:section {:id id :role "dialog" :aria-modal true :aria-label label
                :class (str (s/class-name :sheet) (when class (str " " class)))}
      (specular) body])))

(defn scrim
  "Full-viewport dismiss backdrop behind a sheet/modal. opts: :act (portable
  dismiss interaction — same `act` contract as shitsuke.components), :class."
  ([] (scrim nil))
  ([opts]
   (let [{:keys [act class]} opts]
     [:div {:aria-hidden true
            :class (str (s/class-name :scrim) (when class (str " " class)))
            :data-act (some-> act act->str)}])))

(defn badge
  "Small glass pill badge/counter. `label` is string or hiccup. opts: :class."
  ([label] (badge label nil))
  ([label opts]
   [:span {:class (str (s/class-name :badge) (when-let [c (:class opts)] (str " " c)))}
    label]))

;; --- form controls -----------------------------------------------------

(defn text-field
  "Glass text input. Wraps shitsuke.components/input; same opts (:id, :value,
  :placeholder, :type, :on-input, :act) plus :class on the *wrapper* (the
  input itself carries no class hook — style it via the wrapper)."
  ([opts] (text-field opts nil))
  ([opts wrap-opts]
   [:div {:class (cls (s/class-name :text-field) (:class wrap-opts))}
    (sc/input (dissoc opts :class))
    (specular)]))

(defn text-area
  "Glass textarea. Wraps shitsuke.components/textarea; same opts (:id, :value,
  :rows, :placeholder, :on-input, :act) plus :class on the wrapper."
  ([opts] (text-area opts nil))
  ([opts wrap-opts]
   [:div {:class (cls (s/class-name :text-area) (:class wrap-opts))}
    (sc/textarea (dissoc opts :class))
    (specular)]))

(defn search-field
  "Glass search input: a text-field with a leading search glyph and
  type=\"search\". Same opts as text-field."
  ([opts] (search-field opts nil))
  ([opts wrap-opts]
   [:div {:class (cls (s/class-name :search-field) (:class wrap-opts))}
    [:span {:aria-hidden true :class (s/class-name :search-icon)} "⌕"]
    (sc/input (assoc (dissoc opts :class) :type "search"))
    (specular)]))

(defn menu-select
  "Glass dropdown. Wraps shitsuke.components/select; `options` is [value
  label] pairs. opts: :id, :value, :on-change, :act, plus :class on the wrapper."
  ([options] (menu-select options nil))
  ([options opts]
   [:div {:class (cls (s/class-name :menu-select) (:class opts))}
    (sc/select options (dissoc opts :class))
    (specular)]))

(defn toggle
  "Glass switch (no shitsuke equivalent). A visually-hidden native
  `<input type=\"checkbox\">` inside a `<label>` so click/keyboard/screen-reader
  behavior stays native; the glass track+thumb is a styled sibling `<span>`.
  opts: :id, :checked, :on-change (cljs), :act (ssr — fires on the underlying
  `change` event), :disabled, :class."
  ([] (toggle nil))
  ([opts]
   (let [{:keys [id checked on-change act disabled class]} opts]
     [:label {:class (cls (s/class-name :toggle) class)}
      [:input {:id id :type "checkbox" :class (s/class-name :toggle-input)
               :checked (when checked true) :on-change on-change
               :disabled (when disabled true) :data-act (some-> act act->str)}]
      [:span {:class (s/class-name :toggle-track)}
       [:span {:class (s/class-name :toggle-thumb)}]]])))

(defn checkbox
  "Glass checkbox (no shitsuke equivalent). `label` is optional trailing
  text/hiccup. opts: :id, :checked, :on-change (cljs), :act (ssr), :disabled,
  :class."
  ([] (checkbox nil nil))
  ([label] (checkbox label nil))
  ([label opts]
   (let [{:keys [id checked on-change act disabled class]} opts]
     [:label {:class (cls (s/class-name :checkbox) class)}
      [:input {:id id :type "checkbox" :class (s/class-name :checkbox-input)
               :checked (when checked true) :on-change on-change
               :disabled (when disabled true) :data-act (some-> act act->str)}]
      [:span {:class (s/class-name :checkbox-box)}]
      (when label [:span {:class (s/class-name :checkbox-text)} label])])))

(defn radio
  "Glass radio button (no shitsuke equivalent). `label` is optional trailing
  text/hiccup. opts: :id, :group (give multiple radios the same :group to tie
  them into one native radio group), :value, :checked, :on-change (cljs),
  :act (ssr), :disabled, :class."
  ([] (radio nil nil))
  ([label] (radio label nil))
  ([label opts]
   (let [{:keys [id group value checked on-change act disabled class]} opts]
     [:label {:class (cls (s/class-name :radio) class)}
      [:input {:id id :type "radio" :class (s/class-name :radio-input) :name group :value value
               :checked (when checked true) :on-change on-change
               :disabled (when disabled true) :data-act (some-> act act->str)}]
      [:span {:class (s/class-name :radio-box)}]
      (when label [:span {:class (s/class-name :radio-text)} label])])))

(defn slider
  "Glass range slider (native `<input type=\"range\">`, no shitsuke
  equivalent — styled via vendor-prefixed track/thumb pseudo-elements, see
  docs/design.md). opts: :id, :min, :max, :value, :step, :on-input (cljs),
  :act (ssr), :class."
  ([] (slider nil))
  ([opts]
   (let [{:keys [id min max value step on-input act class]} opts]
     [:input {:id id :type "range" :class (cls (s/class-name :slider) class)
              :min (or min 0) :max (or max 100) :value (or value 0) :step step
              :on-input on-input :data-act (some-> act act->str)}])))

(defn stepper
  "Glass +/- stepper (no shitsuke equivalent, built from `icon-button`).
  `value` is displayed as-is (string/number — the caller owns the count).
  opts: :dec-act, :inc-act (portable `act` for the -/+ buttons), :dec-disabled,
  :inc-disabled, :class."
  ([value] (stepper value nil))
  ([value opts]
   (let [{:keys [dec-act inc-act dec-disabled inc-disabled class]} opts]
     [:div {:class (cls (s/class-name :stepper) class)}
      (icon-button "–" {:act dec-act :disabled dec-disabled})
      [:span {:class (s/class-name :stepper-value)} value]
      (icon-button "+" {:act inc-act :disabled inc-disabled})
      (specular)])))

;; --- feedback ------------------------------------------------------------

(defn progress-bar
  "Glass linear progress track (no shitsuke equivalent). `value`/opts :max
  (default 100) determine the fill percentage. opts: :max, :class."
  ([value] (progress-bar value nil))
  ([value opts]
   (let [{:keys [max class]} opts
         max (or max 100)
         pct (-> (double value) (/ (double max)) (clojure.core/max 0.0) (clojure.core/min 1.0) (* 100))]
     [:div {:class (cls (s/class-name :progress-bar) class)
            :role "progressbar" :aria-valuenow value :aria-valuemin 0 :aria-valuemax max}
      [:div {:class (s/class-name :progress-bar-fill) :style {:width (str pct "%")}}]])))

(defn progress-circle
  "Glass indeterminate spinner (no shitsuke equivalent). opts: :class,
  :label (aria-label, default \"Loading\")."
  ([] (progress-circle nil))
  ([opts]
   (let [{:keys [class label]} opts]
     [:span {:aria-hidden true :role "status" :aria-label (or label "Loading")
             :class (cls (s/class-name :progress-circle) class)}])))

(defn divider
  "Glass hairline divider (no shitsuke equivalent)."
  []
  [:hr {:class (s/class-name :divider)}])

(defn label
  "Icon + text row, SwiftUI `Label`-shaped (no shitsuke equivalent). `icon`
  and `text` are string/hiccup. opts: :class."
  ([icon text] (label icon text nil))
  ([icon text opts]
   [:span {:class (cls (s/class-name :label) (:class opts))}
    [:span {:aria-hidden true :class (s/class-name :label-icon)} icon]
    [:span {:class (s/class-name :label-text)} text]]))

(defn avatar
  "Glass avatar (no shitsuke equivalent). `content` is initials/hiccup shown
  when no :src is given. opts: :src, :alt, :class."
  ([content] (avatar content nil))
  ([content opts]
   (let [{:keys [src alt class]} opts]
     [:span {:class (cls (s/class-name :avatar) class)}
      (if src [:img {:src src :alt (or alt "")}] content)])))

;; --- navigation / overlay --------------------------------------------------

(defn nav-bar
  "Glass top navigation bar (no shitsuke equivalent). `title` is string/hiccup,
  centered. opts: :leading, :trailing (hiccup, typically icon-button(s)),
  :class."
  ([title] (nav-bar title nil))
  ([title opts]
   (let [{:keys [leading trailing class]} opts]
     [:header {:class (cls (s/class-name :nav-bar) class)}
      [:div {:class (s/class-name :nav-bar-leading)} leading]
      [:div {:class (s/class-name :nav-bar-title)} title]
      [:div {:class (s/class-name :nav-bar-trailing)} trailing]
      (specular)])))

(defn alert
  "Centered glass modal dialog (no shitsuke equivalent; distinct from `sheet`,
  which anchors to the bottom edge). Pair with `scrim` for the dismiss
  backdrop. `body` is hiccup or a seq. opts: :id, :class, :label (aria-label)."
  ([body] (alert body nil))
  ([body opts]
   (let [{:keys [id class label]} opts]
     [:section {:id id :role "alertdialog" :aria-modal true :aria-label label
                :class (cls (s/class-name :alert) class)}
      body
      (specular)])))

(defn menu
  "Glass popover action menu (no shitsuke equivalent). `items` is a seq of
  {:label :act :disabled?} maps. Positioning is the consumer's responsibility
  (an absolutely-positioned menu next to a `position:relative` trigger).
  opts: :class."
  ([items] (menu items nil))
  ([items opts]
   [:div {:role "menu" :class (cls (s/class-name :menu) (:class opts))}
    (for [{:keys [label act disabled]} items]
      [:button {:role "menuitem" :type "button" :class (s/class-name :menu-item)
                :disabled (when disabled true) :data-act (some-> act act->str)}
       label])
    (specular)]))

(defn tooltip
  "Glass tooltip pill (no shitsuke equivalent). `text` is string/hiccup.
  Positioning (top/left/etc.) is the consumer's responsibility — this returns
  an absolutely-positioned span with no default offset. opts: :class."
  ([text] (tooltip text nil))
  ([text opts]
   [:span {:role "tooltip" :class (cls (s/class-name :tooltip) (:class opts))} text]))

(defn list-view
  "Glass list container (no shitsuke equivalent). `rows` is a seq of
  `list-row` (or other hiccup) — an empty collection is fine (renders no
  rows). opts: :surface (:regular (default) | :thick), :class."
  ([rows] (list-view rows nil))
  ([rows opts]
   (let [{:keys [surface class]} opts
         variant (when (and surface (not= surface :regular)) (s/class-name (str "list--" (name surface))))]
     [:div {:class (str/join " " (remove nil? [(s/class-name :list) variant class]))}
      (seq rows)
      (specular)])))

(defn list-row
  "A row inside `list-view` (no shitsuke equivalent). `content` is
  hiccup/string. opts: :act (clickable row), :trailing (hiccup shown at the
  row's end), :class."
  ([content] (list-row content nil))
  ([content opts]
   (let [{:keys [act trailing class]} opts]
     [:div {:class (cls (s/class-name :list-row) class) :data-act (some-> act act->str)}
      [:div {:class (s/class-name :list-row-content)} content]
      (when trailing [:div {:class (s/class-name :list-row-trailing)} trailing])])))

(defn chip
  "Glass filter/tag chip (no shitsuke equivalent). `label` is string/hiccup.
  opts: :act (the chip's own click), :on-remove-act (portable act for a
  dismiss × button — omit to render without one), :class."
  ([label] (chip label nil))
  ([label opts]
   (let [{:keys [act on-remove-act class]} opts]
     [:span {:class (cls (s/class-name :chip) class) :data-act (some-> act act->str)}
      (specular)
      label
      (when on-remove-act
        [:button {:type "button" :class (s/class-name :chip-remove)
                  :aria-label "Remove" :data-act (act->str on-remove-act)}
         "×"])])))

(defn disclosure
  "Glass collapsible group (no shitsuke equivalent; SwiftUI `DisclosureGroup`-
  shaped). Built on native `<details>`/`<summary>` — open/close needs no JS.
  `summary` is the always-visible header (string/hiccup); `body` is the
  collapsible content. opts: :open? (default closed), :class."
  ([summary body] (disclosure summary body nil))
  ([summary body opts]
   (let [{:keys [open? class]} opts]
     [:details {:open (when open? true) :class (cls (s/class-name :disclosure) class)}
      (specular)
      [:summary {:class (s/class-name :disclosure-summary)}
       summary
       [:span {:aria-hidden true :class (s/class-name :disclosure-chevron)} "⌄"]]
      [:div {:class (s/class-name :disclosure-body)} body]])))

(defn lens-filter-defs
  "Inline SVG filter definition for the optional `.liquid-glass--lens`
  refraction treatment (feTurbulence + feDisplacementMap, id
  `liquid-glass-lens`). Emit ONCE per page, anywhere in <body>; the element is
  0×0, aria-hidden and paints nothing itself. Engines whose backdrop-filter
  accepts url() (Chromium — partial; see docs/design.md \"Motion & dynamic
  effects\") composite the displacement into the glass backdrop via the
  @supports upgrade in liquid-glass.style; everything else keeps the plain
  blur fallback, so this is safe to emit unconditionally but the *class* is
  opt-in per showcase surface (displacement over a live backdrop is not free).

  Values come from the `:liquid-glass/lens` token group. SVG filter-primitive
  attributes cannot reference CSS custom properties, so they are resolved here
  at hiccup-emit time — pass a partial token override map to retune, e.g.
  `(lens-filter-defs {:liquid-glass/lens {:scale \"12\"}})`."
  ([] (lens-filter-defs nil))
  ([overrides]
   (let [{:keys [frequency scale octaves]} (:liquid-glass/lens (t/resolve-tokens overrides))]
     [:svg {:aria-hidden true :focusable "false" :width "0" :height "0"
            :style {:position "absolute"}}
      [:filter {:id "liquid-glass-lens" :x "-20%" :y "-20%" :width "140%" :height "140%"
                :color-interpolation-filters "sRGB"}
       [:feTurbulence {:type "fractalNoise" :baseFrequency frequency :numOctaves octaves
                       :seed "7" :result "liquid-glass-lens-noise"}]
       [:feDisplacementMap {:in "SourceGraphic" :in2 "liquid-glass-lens-noise" :scale scale
                            :xChannelSelector "R" :yChannelSelector "G"}]]])))

(defn gauge
  "Glass circular gauge (no shitsuke equivalent; SwiftUI `Gauge`-shaped —
  determinate, unlike `progress-circle`'s indeterminate spinner). `value`/
  opts :max (default 100) set the ring fill via an inline conic-gradient
  (computed once per render — no CSS custom-property trick needed, so it
  works identically across every reagent/SSR target). opts: :max, :label
  (shown inside the ring; default \"<pct>%\"), :class."
  ([value] (gauge value nil))
  ([value opts]
   (let [{:keys [max label class]} opts
         max (or max 100)
         pct (-> (double value) (/ (double max)) (clojure.core/max 0.0) (clojure.core/min 1.0) (* 100))
         ring (str "conic-gradient(var(--liquid-glass-accent-tint-strong) 0 " pct "%,"
                   "var(--liquid-glass-surface-regular-tint) " pct "% 100%)")]
     [:span {:class (cls (s/class-name :gauge) class) :style {:background ring}
             :role "meter" :aria-valuenow value :aria-valuemin 0 :aria-valuemax max}
      [:span {:class (s/class-name :gauge-label)} (or label (str (int pct) "%"))]])))
