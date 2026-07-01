(ns liquid-glass.components-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shitsuke.hiccup :as h]
            [liquid-glass.components :as c]))

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
      (is (not (str/includes? out "panel--raised"))))))

(deftest sheet-test
  (let [out (html (c/sheet "body" {:label "Settings"}))]
    (is (str/includes? out "liquid-glass__sheet"))
    (is (str/includes? out "aria-label=\"Settings\""))))

(deftest scrim-test
  (is (str/includes? (html (c/scrim {:act :dismiss})) "data-act=\"dismiss\"")))

(deftest badge-test
  (is (= "<span class=\"liquid-glass__badge\">3</span>" (html (c/badge "3")))))
