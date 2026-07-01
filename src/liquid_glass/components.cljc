(ns liquid-glass.components
  "Pure-hiccup liquid-glass primitives (.cljc, no reagent import) — the dual-render
  contract inherited from shitsuke: the same hiccup data renders via
  shitsuke.hiccup/->html (SSR) or reagent (browser).

  liquid-glass-ui owns no interaction/state logic of its own. Every component
  either wraps the matching `shitsuke.components` fn (button/icon-button/
  toolbar/mode-tabs/card keep their exact `act`/`:on-input`/etc. contract; see
  shitsuke.components docstrings) or, where shitsuke has no equivalent
  (sheet/scrim/badge/tab-bar-as-glass-container), is a small hiccup literal
  following the same `data-act` convention. Every component appends a
  `liquid-glass__specular` marker span (currently a no-op display:none anchor
  driven purely by the ::before CSS overlay in liquid-glass.style/component-css;
  reserved as the hook a future pointer-tracking enhancer or GPU/WebGPU
  refraction layer would target — see docs/design.md #future-work)."
  (:require [shitsuke.components :as sc]
            [liquid-glass.style :as s]
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
