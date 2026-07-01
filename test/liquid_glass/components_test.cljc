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
    (is (str/includes? out "shitsuke__input"))
    (is (str/includes? out "placeholder=\"Name\""))
    (is (str/includes? out "liquid-glass__specular"))))

(deftest text-area-test
  (is (str/includes? (html (c/text-area {:value "hi"})) "liquid-glass__text-area")))

(deftest search-field-test
  (let [out (html (c/search-field {:placeholder "Search"}))]
    (is (str/includes? out "liquid-glass__search-field"))
    (is (str/includes? out "liquid-glass__search-icon"))
    (is (str/includes? out "type=\"search\""))))

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
    (is (str/includes? (html (c/list-view [] {:surface :thick})) "liquid-glass__list--thick"))))

;; --- cross-check: every rendered base class has a component-css rule ------

(def ^:private every-component-sample
  [(c/button "x") (c/icon-button "x") (c/toolbar [(c/button "x")])
   (c/tab-bar [[:a "A"]] :a) (c/panel "x") (c/sheet "x") (c/scrim) (c/badge "1")
   (c/text-field {}) (c/text-area {}) (c/search-field {}) (c/menu-select [["a" "A"]] {})
   (c/toggle) (c/checkbox "x") (c/radio "x") (c/slider) (c/stepper 1)
   (c/progress-bar 1) (c/progress-circle) (c/divider) (c/label "x" "x") (c/avatar "x")
   (c/nav-bar "x") (c/alert "x") (c/menu [{:label "x"}]) (c/tooltip "x")
   (c/list-view [(c/list-row "x" {:trailing "x"})])])

(deftest every-rendered-class-has-a-css-rule-test
  (let [css (s/component-css)
        rendered (str/join " " (map html every-component-sample))
        base-classes (->> (re-seq #"liquid-glass__[\w-]+" rendered)
                          (remove #(str/includes? % "--")) ;; modifier classes covered by panel/list-view tests
                          distinct)]
    (doseq [c base-classes]
      (is (str/includes? css (str "." c)) (str "no component-css rule for " c)))))
