(ns liquid-glass.style-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [liquid-glass.style :as s]))

(deftest class-name-test
  (is (= "liquid-glass__button" (s/class-name :button)))
  (is (= "liquid-glass__panel--thick" (s/class-name "panel--thick"))))

(deftest root-css-test
  (let [css (s/root-css)]
    (is (str/includes? css ":root {"))
    (is (str/includes? css "@media (prefers-color-scheme: dark)"))))

(deftest component-css-test
  (let [css (s/component-css)]
    (testing "every component class is defined"
      (doseq [c ["panel" "button" "icon-button" "toolbar" "tab-bar" "tab" "tab--active"
                 "sheet" "scrim" "badge" "specular"]]
        (is (str/includes? css (str ".liquid-glass__" c)) (str "missing rule for " c))))
    (testing "rules reference custom properties, not literal values"
      (is (str/includes? css "var(--liquid-glass-surface-regular-tint)")))
    (testing "no-backdrop-filter fallback and reduced-motion guard are present"
      (is (str/includes? css "@supports not (backdrop-filter"))
      (is (str/includes? css "@media (prefers-reduced-motion: reduce)")))))

(deftest inline-style-test
  (is (str/starts-with? (s/inline-style) "<style>"))
  (is (str/includes? (s/inline-style) ":root {")))
