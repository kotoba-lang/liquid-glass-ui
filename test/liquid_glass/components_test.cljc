(ns liquid-glass.components-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shitsuke.hiccup :as h]
            [liquid-glass.components :as c]
            [liquid-glass.style :as s]))

(defn html [hic] (h/->html hic))

(deftest button-test
  (let [out (html (c/button "Go" {:act :go}))]
    (is (str/includes? out "shitsuke__button"))
    (is (str/includes? out "liquid-glass__button"))
    (is (str/includes? out "data-act=\"go\""))
    (is (str/includes? out "liquid-glass__specular"))
    (testing "namespaced act preserved (shitsuke contract carried through)"
      (is (str/includes? (html (c/button "Add" {:act :cart/add})) "data-act=\"cart/add\"")))))

(deftest icon-button-test
  (is (str/includes? (html (c/icon-button "x")) "liquid-glass__icon-button")))

(deftest toolbar-test
  (let [out (html (c/toolbar [(c/button "A") (c/button "B")]))]
    (is (str/includes? out "liquid-glass__toolbar"))
    (is (str/includes? out "liquid-glass__button"))))

(deftest tab-bar-test
  (let [out (html (c/tab-bar [[:visual "Visual"] [:edn "EDN"]] :visual))]
    (is (str/includes? out "liquid-glass__tab-bar"))
    (is (str/includes? out "liquid-glass__tab liquid-glass__tab--active"))
    (is (str/includes? out "data-act=\"visual\""))
    (is (not (str/includes? out "shitsuke__")))))

(deftest panel-test
  (is (str/includes? (html (c/panel "body")) "liquid-glass__panel"))
  (testing "surface variant modifier class"
    (is (str/includes? (html (c/panel "body" {:surface :thick})) "liquid-glass__panel--thick")))
  (testing "elevation variant modifier class"
    (is (str/includes? (html (c/panel "body" {:elevation :floating})) "liquid-glass__panel--floating")))
  (testing "default surface/elevation add no modifier class"
    (let [out (html (c/panel "body"))]
      (is (not (str/includes? out "panel--regular")))
      (is (not (str/includes? out "panel--raised")))))
  (testing "every non-default surface/elevation modifier class has a component-css rule"
    (let [css (s/component-css)]
      (doseq [surface [:clear :thick]]
        (is (str/includes? css (str "." (s/class-name (str "panel--" (name surface)))))
            (str "no component-css rule for surface " surface)))
      (doseq [elevation [:flat :overlay :floating]]
        (is (str/includes? css (str "." (s/class-name (str "panel--" (name elevation)))))
            (str "no component-css rule for elevation " elevation))))))

(deftest sheet-test
  (let [out (html (c/sheet "body" {:label "Settings"}))]
    (is (str/includes? out "liquid-glass__sheet"))
    (is (str/includes? out "aria-label=\"Settings\""))))

(deftest scrim-test
  (is (str/includes? (html (c/scrim {:act :dismiss})) "data-act=\"dismiss\"")))

(deftest badge-test
  (is (= "<span class=\"liquid-glass__badge\">3</span>" (html (c/badge "3")))))

;; --- form controls -----------------------------------------------------

(deftest text-field-test
  (let [out (html (c/text-field {:id "n" :placeholder "Name"}))]
    (is (str/includes? out "liquid-glass__text-field"))
    (is (str/includes? out "<input"))
    (is (str/includes? out "placeholder=\"Name\""))
    (is (str/includes? out "liquid-glass__specular"))
    (testing "the input carries no class of its own (styled via the wrapper's descendant selector)"
      (is (not (str/includes? out "shitsuke__input")))))
  (testing ":act keeps the portable data-act SSR contract"
    (is (str/includes? (html (c/text-field {:act :chat/send})) "data-act=\"chat/send\""))))

(deftest text-area-test
  (let [hic (c/text-area {:value "hi"})]
    (is (str/includes? (html hic) "liquid-glass__text-area"))
    (testing ":value rides as an attribute, not element content (value-as-child
              makes the textarea silently uncontrolled after mount under React)"
      (is (= "hi" (get-in hic [2 1 :value])))
      (is (= 2 (count (nth hic 2))) "textarea node is [:textarea attrs] with no children")))
  (testing ":rows defaults to 6 and passes through"
    (is (= 6 (get-in (c/text-area {}) [2 1 :rows])))
    (is (= 3 (get-in (c/text-area {:rows 3}) [2 1 :rows])))))

(deftest search-field-test
  (let [out (html (c/search-field {:placeholder "Search"}))]
    (is (str/includes? out "liquid-glass__search-field"))
    (is (str/includes? out "liquid-glass__search-icon"))
    (is (str/includes? out "type=\"search\""))))

;; --- keystroke-loss regression (net-babiniku, reagent async rendering) ----
;; The bug: shitsuke.components/input|textarea emit :value + :on-input, and
;; reagent's async-rendering-safe controlled-input path only engages for
;; :value + :on-change — so React restored the DOM to the stale rendered
;; value after every keystroke, losing all but the last one whenever a
;; keystroke landed before the next (rAF-batched) render. These tests pin the
;; fixed contract: stable, shape-identical hiccup with the caller's handler on
;; :on-change.

(deftest text-field-stable-hiccup-test
  (let [f (fn [_e] nil)
        opts {:value "abc" :placeholder "Name" :on-input f :aria-label "Name"}]
    (testing "equal args yield = hiccup (no gensym/instance-varying parts)"
      (is (= (c/text-field opts) (c/text-field opts)))
      (is (= (c/text-area opts) (c/text-area opts)))
      (is (= (c/search-field opts) (c/search-field opts))))))

(deftest text-field-input-path-stable-test
  (testing "the input sits at the same index with and without optional opts"
    (let [path-of (fn [hic] (first (keep-indexed (fn [i x] (when (and (vector? x) (#{:input :textarea} (first x))) i)) hic)))]
      (is (= 2
             (path-of (c/text-field {}))
             (path-of (c/text-field {:value "v" :placeholder "p" :id "i" :disabled true :aria-label "l"}))
             (path-of (c/text-field {} {:class "extra"}))))
      (is (= 2
             (path-of (c/text-area {}))
             (path-of (c/text-area {:value "v" :rows 3 :maxLength 10}))))
      (is (= 3
             (path-of (c/search-field {}))
             (path-of (c/search-field {:value "v" :placeholder "p"})))))))

(deftest text-field-attrs-passthrough-test
  (let [f (fn [_e] nil)
        k (fn [_e] nil)
        attrs (get-in (c/text-field {:value "v" :on-input f :on-key-down k :disabled true
                                     :aria-label "Name" :aria-describedby "hint"
                                     :maxLength 40 :min "1" :type "number" :placeholder "p"})
                      [2 1])]
    (testing "caller attrs reach the bare input untouched"
      (is (= "v" (:value attrs)))
      (is (= k (:on-key-down attrs)))
      (is (true? (:disabled attrs)))
      (is (= "Name" (:aria-label attrs)))
      (is (= "hint" (:aria-describedby attrs)))
      (is (= 40 (:maxLength attrs)))
      (is (= "1" (:min attrs)))
      (is (= "number" (:type attrs))))
    (testing ":on-input is attached as :on-change (reagent's async-safe controlled-input path)"
      (is (= f (:on-change attrs)))
      (is (not (contains? attrs :on-input))))
    (testing "no nil-noise attrs (:id/:data-act only when supplied)"
      (is (not (contains? attrs :id)))
      (is (not (contains? attrs :data-act))))))

(deftest text-field-on-change-contract-test
  (let [f (fn [_e] nil)
        g (fn [_e] nil)]
    (testing "a caller :on-change passes through untouched"
      (is (= f (get-in (c/text-field {:value "v" :on-change f}) [2 1 :on-change]))))
    (testing "explicit :on-input AND :on-change both survive"
      (let [attrs (get-in (c/text-field {:value "v" :on-input f :on-change g}) [2 1])]
        (is (= f (:on-input attrs)))
        (is (= g (:on-change attrs)))))
    (testing "same mapping on text-area"
      (let [attrs (get-in (c/text-area {:value "v" :on-input f}) [2 1])]
        (is (= f (:on-change attrs)))
        (is (not (contains? attrs :on-input)))))
    (testing "a supplied nil :value normalizes to a controlled empty string; absent :value stays absent"
      (is (= "" (get-in (c/text-field {:value nil :on-change f}) [2 1 :value])))
      (is (not (contains? (get-in (c/text-field {:on-change f}) [2 1]) :value))))))

(deftest menu-select-test
  (let [out (html (c/menu-select [["a" "A"] ["b" "B"]] {:value "a"}))]
    (is (str/includes? out "liquid-glass__menu-select"))
    (is (str/includes? out "shitsuke__select"))))

(deftest toggle-test
  (let [out (html (c/toggle {:checked true :act :dark-mode}))]
    (is (str/includes? out "liquid-glass__toggle-track"))
    (is (str/includes? out "liquid-glass__toggle-thumb"))
    (is (str/includes? out "checked"))
    (is (str/includes? out "data-act=\"dark-mode\""))))

(deftest checkbox-test
  (let [out (html (c/checkbox "Remember me" {:checked true}))]
    (is (str/includes? out "liquid-glass__checkbox-box"))
    (is (str/includes? out "Remember me"))
    (is (str/includes? out "checked"))))

(deftest radio-test
  (let [out (html (c/radio "Option A" {:group "g" :value "a" :checked true}))]
    (is (str/includes? out "liquid-glass__radio-box"))
    (is (str/includes? out "name=\"g\""))
    (is (str/includes? out "value=\"a\""))))

(deftest slider-test
  (let [out (html (c/slider {:value 40 :min 0 :max 100}))]
    (is (str/includes? out "liquid-glass__slider"))
    (is (str/includes? out "type=\"range\""))
    (is (str/includes? out "value=\"40\""))))

(deftest stepper-test
  (let [out (html (c/stepper 3 {:dec-act :dec :inc-act :inc}))]
    (is (str/includes? out "liquid-glass__stepper"))
    (is (str/includes? out "liquid-glass__stepper-value\">3<"))
    (is (str/includes? out "data-act=\"dec\""))
    (is (str/includes? out "data-act=\"inc\""))))

;; --- feedback ------------------------------------------------------------

(deftest progress-bar-test
  (let [out (html (c/progress-bar 40 {:max 100}))]
    (is (str/includes? out "liquid-glass__progress-bar"))
    (is (str/includes? out "width:40.0%"))
    (is (str/includes? out "aria-valuenow=\"40\""))))

(deftest progress-circle-test
  (is (str/includes? (html (c/progress-circle)) "liquid-glass__progress-circle")))

(deftest divider-test
  (is (= "<hr class=\"liquid-glass__divider\">" (html (c/divider)))))

(deftest label-test
  (let [out (html (c/label "♥" "Favorites"))]
    (is (str/includes? out "liquid-glass__label-icon"))
    (is (str/includes? out "Favorites"))))

(deftest avatar-test
  (is (= "<span class=\"liquid-glass__avatar\">JK</span>" (html (c/avatar "JK"))))
  (testing "src renders an img"
    (is (str/includes? (html (c/avatar "JK" {:src "a.png" :alt "Jun"})) "<img src=\"a.png\" alt=\"Jun\">"))))

;; --- navigation / overlay --------------------------------------------------

(deftest nav-bar-test
  (let [out (html (c/nav-bar "Settings" {:leading (c/icon-button "<") :trailing (c/icon-button "+")}))]
    (is (str/includes? out "liquid-glass__nav-bar"))
    (is (str/includes? out "liquid-glass__nav-bar-title\">Settings<"))
    (is (str/includes? out "liquid-glass__nav-bar-leading"))
    (is (str/includes? out "liquid-glass__nav-bar-trailing"))))

(deftest alert-test
  (let [out (html (c/alert [[:h3 "Delete?"]] {:label "Delete"}))]
    (is (str/includes? out "liquid-glass__alert"))
    (is (str/includes? out "role=\"alertdialog\""))
    (is (str/includes? out "aria-label=\"Delete\""))))

(deftest menu-test
  (let [out (html (c/menu [{:label "Rename" :act :rename} {:label "Delete" :act :delete :disabled true}]))]
    (is (str/includes? out "liquid-glass__menu\""))
    (is (str/includes? out "liquid-glass__menu-item"))
    (is (str/includes? out "data-act=\"rename\""))
    (is (str/includes? out "disabled"))))

(deftest tooltip-test
  (is (= "<span role=\"tooltip\" class=\"liquid-glass__tooltip\">Hello</span>" (html (c/tooltip "Hello")))))

(deftest list-view-test
  (let [out (html (c/list-view [(c/list-row "Row 1") (c/list-row "Row 2" {:trailing ">" :act :open})]))]
    (is (str/includes? out "liquid-glass__list\""))
    (is (str/includes? out "liquid-glass__list-row"))
    (is (str/includes? out "liquid-glass__list-row-trailing"))
    (is (str/includes? out "data-act=\"open\"")))
  (testing "surface variant modifier class"
    (is (str/includes? (html (c/list-view [] {:surface :thick})) "liquid-glass__list--thick")))
  ;; Kaizen (co-scientist round 75, net-babiniku): a screen reader had no way to
  ;; expose a group of rows as a navigable list -- role="list"/role="listitem" is
  ;; the standard WAI-ARIA pairing for a list container + its items.
  (testing "list/listitem ARIA roles"
    (let [out (html (c/list-view [(c/list-row "Row 1") (c/list-row "Row 2")]))]
      (is (str/includes? out "role=\"list\""))
      (is (= 2 (count (re-seq #"role=\"listitem\"" out)))))))

(deftest chip-test
  (let [out (html (c/chip "Vegetarian" {:on-remove-act :remove-veg}))]
    (is (str/includes? out "liquid-glass__chip\""))
    (is (str/includes? out "liquid-glass__chip-remove"))
    (is (str/includes? out "data-act=\"remove-veg\"")))
  (testing "no on-remove-act means no remove button"
    (is (not (str/includes? (html (c/chip "Vegetarian")) "chip-remove")))))

(deftest disclosure-test
  (let [out (html (c/disclosure "Advanced" [[:p "more"]] {:open? true}))]
    (is (str/includes? out "liquid-glass__disclosure\""))
    (is (str/includes? out "<details open"))
    (is (str/includes? out "liquid-glass__disclosure-summary"))
    (is (str/includes? out "liquid-glass__disclosure-chevron")))
  (testing "closed by default"
    (is (not (str/includes? (html (c/disclosure "Advanced" [[:p "more"]])) "<details open")))))

(deftest lens-filter-defs-test
  (let [out (html (c/lens-filter-defs))]
    (testing "one inline SVG filter definition with the stable id the CSS @supports upgrade targets"
      (is (str/includes? out "<filter id=\"liquid-glass-lens\""))
      (is (str/includes? out "<feTurbulence"))
      (is (str/includes? out "<feDisplacementMap"))
      (is (str/includes? out "aria-hidden")))
    (testing "attribute values are the :liquid-glass/lens tokens (resolved at emit time —
              SVG filter attributes can't read CSS custom properties)"
      (is (str/includes? out "baseFrequency=\"0.008\""))
      (is (str/includes? out "scale=\"8\""))
      (is (str/includes? out "numOctaves=\"2\"")))
    (testing "token overrides retune the filter through the same pipeline"
      (let [out (html (c/lens-filter-defs {:liquid-glass/lens {:scale "12"}}))]
        (is (str/includes? out "scale=\"12\""))
        (is (str/includes? out "baseFrequency=\"0.008\""))))
    (testing "paints nothing itself"
      (is (str/includes? out "width=\"0\""))
      (is (str/includes? out "height=\"0\"")))))

(deftest gauge-test
  (let [out (html (c/gauge 72))]
    (is (str/includes? out "liquid-glass__gauge\""))
    (is (str/includes? out "72.0%"))
    (is (str/includes? out "72%"))
    (is (str/includes? out "role=\"meter\"")))
  (testing "custom label overrides the computed percentage text"
    (is (str/includes? (html (c/gauge 30 {:label "30/100"})) "30/100"))))

;; --- cross-check: every rendered base class has a component-css rule ------

(def ^:private every-component-sample
  [(c/button "x") (c/icon-button "x") (c/toolbar [(c/button "x")])
   (c/tab-bar [[:a "A"]] :a) (c/panel "x") (c/sheet "x") (c/scrim) (c/badge "1")
   (c/text-field {}) (c/text-area {}) (c/search-field {}) (c/menu-select [["a" "A"]] {})
   (c/toggle) (c/checkbox "x") (c/radio "x") (c/slider) (c/stepper 1)
   (c/progress-bar 1) (c/progress-circle) (c/gauge 50) (c/divider) (c/label "x" "x") (c/avatar "x")
   (c/nav-bar "x") (c/alert "x") (c/menu [{:label "x"}]) (c/tooltip "x")
   (c/list-view [(c/list-row "x" {:trailing "x"})])
   (c/chip "x" {:on-remove-act :x}) (c/disclosure "x" [[:p "x"]])])

;; --- data-level checks against s/component-rules (not the rendered string) -
;; The point of the css.core migration: assert against the EDN rules directly
;; instead of regex-scraping rendered CSS text.

(deftest component-rules-shape-test
  (testing "component-rules is data: every entry is a [selector decls-map] pair"
    (doseq [[sel decls] (s/component-rules)]
      (is (string? sel))
      (is (map? decls)))))

(deftest every-elevation-shadow-carries-both-rim-vars-test
  (testing "no rule can have an elevation box-shadow without the rim edge-light (the panel--flat/orphaned-rim bug class, at the data level)"
    (doseq [[sel decls] (s/component-rules)
            :let [shadow (:box-shadow decls)]
            :when (and shadow (str/includes? shadow "elevation"))]
      (is (str/includes? shadow "specular-rim-top-opacity") (str sel " has an elevation shadow but no top rim"))
      (is (str/includes? shadow "specular-rim-bottom-opacity") (str sel " has an elevation shadow but no bottom rim")))))

(deftest ink-rule-present-test
  (let [ink-rule (first (filter (fn [[_ decls]] (= "var(--liquid-glass-ink-default)" (:color decls)))
                                 (s/component-rules)))]
    (is (some? ink-rule) "no rule sets the default ink color")
    (is (= "var(--liquid-glass-ink-shadow)" (:text-shadow (second ink-rule))))
    (testing "applies broadly across component roots, not just the ::before-bearing glass surfaces"
      (is (str/includes? (first ink-rule) "liquid-glass__toggle,"))
      (is (str/includes? (first ink-rule) "liquid-glass__checkbox,"))
      (is (str/includes? (first ink-rule) "liquid-glass__tooltip")))))

(deftest every-rendered-class-has-a-css-rule-test
  (let [css (s/component-css)
        rendered (str/join " " (map html every-component-sample))
        base-classes (->> (re-seq #"liquid-glass__[\w-]+" rendered)
                          (remove #(str/includes? % "--")) ;; modifier classes covered by panel/list-view tests
                          distinct)]
    (doseq [c base-classes]
      (is (str/includes? css (str "." c)) (str "no component-css rule for " c)))))
