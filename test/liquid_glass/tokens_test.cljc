(ns liquid-glass.tokens-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [liquid-glass.tokens :as t]))

(deftest resolve-tokens-test
  (is (= t/default-tokens (t/resolve-tokens nil)))
  (testing "override merges over defaults"
    (let [r (t/resolve-tokens {:liquid-glass/surface {:regular {:blur "40px"}}})]
      (is (= "40px" (get-in r [:liquid-glass/surface :regular :blur])))
      (is (= "180%" (get-in r [:liquid-glass/surface :regular :saturate]))))))

(deftest resolve-dark-tokens-test
  (is (= t/dark-tokens (t/resolve-dark-tokens nil)))
  (testing "dark override merges over dark-tokens"
    (let [r (t/resolve-dark-tokens {:liquid-glass/surface {:regular {:tint "rgba(0,0,0,0.9)"}}})]
      (is (= "rgba(0,0,0,0.9)" (get-in r [:liquid-glass/surface :regular :tint]))))))

(deftest css-variables-test
  (let [css (t/css-variables)]
    (is (str/starts-with? css ":root {"))
    (is (str/includes? css "--liquid-glass-surface-regular-blur: 20px;"))
    (is (str/includes? css "--liquid-glass-radius-pill: 999px;"))
    (is (str/includes? css "--liquid-glass-motion-press-easing: cubic-bezier(.32,.72,0,1);"))
    (is (str/includes? css "--liquid-glass-accent-tint-strong: rgba(10,132,255,0.85);")))
  (testing "overrides flow into emitted vars"
    (let [css (t/css-variables {:liquid-glass/radius {:md "20px"}})]
      (is (str/includes? css "--liquid-glass-radius-md: 20px;")))))

(deftest dark-css-variables-test
  (let [css (t/dark-css-variables)]
    (is (str/includes? css "@media (prefers-color-scheme: dark)"))
    (is (str/includes? css "--liquid-glass-surface-regular-tint: rgba(20,20,24,0.42);"))
    (testing "scheme-independent groups are not redeclared in the dark block"
      (is (not (str/includes? css "--liquid-glass-radius-"))))))
