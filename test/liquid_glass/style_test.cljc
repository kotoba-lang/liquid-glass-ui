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
      (is (str/includes? css "@media (prefers-reduced-motion: reduce)")))
    (testing "the :liquid-glass/specular :rim tokens are actually wired into a rule (not orphaned)"
      (is (str/includes? css "var(--liquid-glass-specular-rim-top-opacity)"))
      (is (str/includes? css "var(--liquid-glass-specular-rim-bottom-opacity)")))
    (testing "backdrop-filter includes a brightness lift, not just blur+saturate"
      (is (str/includes? css "brightness(1.05)")))))

(deftest inline-style-test
  (is (str/starts-with? (s/inline-style) "<style>"))
  (is (str/includes? (s/inline-style) ":root {")))

;; --- motion & dynamic effects ------------------------------------------------

(deftest overlay-enter-exit-test
  (let [css (s/component-css)]
    (testing "every overlay component has enter AND exit keyframes"
      (doseq [c ["scrim" "sheet" "alert" "menu" "tooltip"]]
        (is (str/includes? css (str "@keyframes liquid-glass-" c "-enter")) (str c " missing enter keyframes"))
        (is (str/includes? css (str "@keyframes liquid-glass-" c "-exit")) (str c " missing exit keyframes"))))
    (testing "exit is the data-state closing attribute contract"
      (doseq [c ["scrim" "sheet" "alert" "menu" "tooltip"]]
        (is (str/includes? css (str ".liquid-glass__" c "[data-state=\"closing\"]"))
            (str c " missing [data-state=closing] exit rule"))))
    (testing "durations/easing/offsets are tokens, not literals"
      (is (str/includes? css "var(--liquid-glass-motion-overlay-enter-duration)"))
      (is (str/includes? css "var(--liquid-glass-motion-overlay-exit-easing)"))
      (is (str/includes? css "var(--liquid-glass-motion-overlay-enter-distance)"))
      (is (str/includes? css "var(--liquid-glass-motion-overlay-enter-scale-y)")))
    (testing "alert keyframes keep the centering translate so animating doesn't un-center it"
      (is (str/includes? css "translate(-50%,calc(-50% + var(--liquid-glass-motion-overlay-enter-distance)))")))))

(deftest overlay-motion-rules-data-test
  (testing "at the data level: every overlay component rule pairs an enter animation with a closing exit"
    (let [rules (s/component-rules)
          animation-of (fn [selector] (some (fn [[sel decls]] (when (= sel selector) (:animation decls))) rules))]
      (doseq [c ["scrim" "sheet" "alert" "menu" "tooltip"]]
        (is (str/includes? (or (animation-of (str ".liquid-glass__" c)) "") "-enter"))
        (is (str/includes? (or (animation-of (str ".liquid-glass__" c "[data-state=\"closing\"]")) "") "-exit")))
      (testing "menu scales from its top edge"
        (is (some (fn [[sel decls]] (and (= sel ".liquid-glass__menu") (= "top center" (:transform-origin decls))))
                  rules))))))

(deftest spring-easing-test
  (let [css (s/component-css)]
    (testing "spring upgrade is feature-tested; cubic-bezier default stays outside it"
      (is (str/includes? css "@supports (transition-timing-function: linear(0, 1))"))
      (is (str/includes? css "var(--liquid-glass-motion-spring-easing)"))
      (is (str/includes? css "var(--liquid-glass-motion-spring-duration)"))
      (let [supports-at (str/index-of css "@supports (transition-timing-function")]
        (is (str/includes? (subs css 0 supports-at) "var(--liquid-glass-motion-settle-easing)")
            "default cubic-bezier settle must remain before the @supports upgrade")))
    (testing "spring reaches thumb slide and chevron flip inside the upgrade block"
      (let [block (subs css (str/index-of css "@supports (transition-timing-function"))]
        (is (str/includes? block ".liquid-glass__toggle-thumb"))
        (is (str/includes? block ".liquid-glass__disclosure-chevron"))))))

(deftest press-morph-test
  (testing "button :active squashes via the press scale tokens, not a flat literal scale"
    (let [active-rule (some (fn [[sel decls]]
                              (when (str/includes? sel ".liquid-glass__button:active") decls))
                            (s/component-rules))]
      (is (some? active-rule))
      (is (str/includes? (:transform active-rule) "scaleX(var(--liquid-glass-motion-press-scale-x))"))
      (is (str/includes? (:transform active-rule) "scaleY(var(--liquid-glass-motion-press-scale-y))")))))

(deftest pointer-specular-test
  (let [css (s/component-css)
        rules (s/component-rules)]
    (testing "the highlight is entirely gated behind .liquid-glass-js (no JS, span stays display:none)"
      (is (str/includes? css ".liquid-glass__specular{display:none;}"))
      (is (str/includes? css ".liquid-glass-js .liquid-glass__specular")))
    (testing "gradient position and opacity come from the pointer vars / tokens"
      (is (str/includes? css "calc(var(--liquid-glass-pointer-x,.5)*100%)"))
      (is (str/includes? css "calc(var(--liquid-glass-pointer-y,.5)*100%)"))
      (is (str/includes? css "var(--liquid-glass-specular-pointer-opacity)"))
      (is (str/includes? css "var(--liquid-glass-specular-pointer-size)")))
    (testing "data level: the highlight can't intercept the pointer, and only opacity transitions"
      (let [decls (some (fn [[sel d]] (when (= sel ".liquid-glass-js .liquid-glass__specular") d)) rules)]
        (is (= "none" (:pointer-events decls)))
        (is (str/starts-with? (:transition decls) "opacity "))))
    (testing "hover state rule"
      (is (some (fn [[sel d]] (and (= sel ".liquid-glass-js [data-lg-pointer] > .liquid-glass__specular")
                                   (= "1" (:opacity d))))
                rules)))))

(deftest specular-selector-test
  (let [selector (s/specular-selector)]
    (testing "covers the components that carry the marker span"
      (doseq [c ["button" "panel" "toolbar" "menu" "disclosure" "tab-bar"]]
        (is (str/includes? selector (str ".liquid-glass__" c)))))
    (testing "excludes nested-surface controls and span-less components"
      (doseq [c ["toggle-track" "checkbox-box" "radio-box" "badge" "scrim" "tooltip" "gauge"]]
        (is (not (str/includes? selector (str ".liquid-glass__" c ","))))
        (is (not (str/ends-with? selector (str ".liquid-glass__" c))))))))

(deftest lens-test
  (let [css (s/component-css)]
    (testing "displacement upgrade is feature-tested on backdrop-filter url() support"
      (is (str/includes? css "@supports (backdrop-filter: url(#liquid-glass-lens))"))
      (is (str/includes? css "url(#liquid-glass-lens)")))
    (testing "the upgraded value keeps blur/saturate so a parse-only engine still gets glass"
      (let [block (subs css (str/index-of css "@supports (backdrop-filter: url"))]
        (is (str/includes? block "blur(var(--liquid-glass-surface-regular-blur))"))
        (is (str/includes? block "saturate(var(--liquid-glass-surface-regular-saturate))"))))
    (testing "plain-blur fallback rule exists outside the upgrade (data level)"
      (let [decls (some (fn [[sel d]] (when (= sel ".liquid-glass--lens") d)) (s/component-rules))]
        (is (some? decls))
        (is (str/includes? (:backdrop-filter decls) "blur(var(--liquid-glass-surface-regular-blur))"))
        (is (not (str/includes? (:backdrop-filter decls) "url(")))))))

(deftest reduced-motion-disables-new-motion-test
  (let [css (s/component-css)
        rm-at (str/last-index-of css "@media (prefers-reduced-motion: reduce)")
        block (subs css rm-at)]
    (testing "the guard is the LAST block so it out-cascades the @supports spring upgrade"
      (is (> rm-at (str/index-of css "@supports (transition-timing-function")))
      (is (> rm-at (str/index-of css "@supports (backdrop-filter: url"))))
    (testing "overlay animations off, including the higher-specificity closing variants"
      (is (str/includes? block "animation: none"))
      (is (str/includes? block ".liquid-glass__alert[data-state=\"closing\"]")))
    (testing "pointer highlight off even when the script added .liquid-glass-js"
      (is (str/includes? block ".liquid-glass-js .liquid-glass__specular { display: none;")))))
