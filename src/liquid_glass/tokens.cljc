(ns liquid-glass.tokens
  "Design-token IR for the liquid-glass material, layered next to shitsuke.tokens
  (same IR shape: a map of groups, deep-merge overridable, emitted as `:root`
  CSS custom properties). liquid-glass owns glass-specific groups that
  shitsuke's web-CSS token set does not model:

    {:liquid-glass/surface     {<variant> {:blur ... :saturate ... :tint ... :border ...}}
     :liquid-glass/elevation   {<level>   {:shadow ...}}
     :liquid-glass/specular    {<part>    {...}}
     :liquid-glass/radius      {<size>    <css-length>}
     :liquid-glass/motion      {<phase>   {:duration ... :easing ...}}}

  `default-tokens` is the light-scheme material. `dark-tokens` is a *partial*
  override map (surface tint/border + specular opacity only — the values a
  glass material actually needs to change when the content behind it goes
  dark) applied under `@media (prefers-color-scheme: dark)` by
  `liquid-glass.style/root-css`, the same var-name-override technique used for
  dark mode in CSS (redeclare the same custom property inside the media query
  rather than emit a second `-dark` variable — components only ever reference
  one name).

  `deep-merge` is reused from `shitsuke.tokens` (a pure, host-independent fn) so
  overrides compose the same way across both token sets. Portable .cljc, zero
  deps beyond shitsuke, babashka-safe."
  (:require [shitsuke.tokens :as shitsuke]
            [clojure.string :as str]))

(def default-tokens
  "v1 light-scheme material. `:clear` (barely-there — sheet scrims, tooltips),
  `:regular` (the default control surface), `:thick` (toolbars/sheets that sit
  over busy content and need more optical separation)."
  {:liquid-glass/surface
   {:clear   {:blur "12px" :saturate "160%" :tint "rgba(255,255,255,0.06)" :border "rgba(255,255,255,0.22)"}
    :regular {:blur "20px" :saturate "180%" :tint "rgba(255,255,255,0.14)" :border "rgba(255,255,255,0.35)"}
    :thick   {:blur "32px" :saturate "200%" :tint "rgba(255,255,255,0.22)" :border "rgba(255,255,255,0.45)"}}
   :liquid-glass/elevation
   {:flat     {:shadow "none"}
    :raised   {:shadow "0 1px 2px rgba(0,0,0,.12), 0 4px 10px rgba(0,0,0,.10)"}
    :overlay  {:shadow "0 8px 24px rgba(0,0,0,.18), 0 2px 6px rgba(0,0,0,.12)"}
    :floating {:shadow "0 20px 48px rgba(0,0,0,.28), 0 6px 16px rgba(0,0,0,.16)"}}
   :liquid-glass/specular
   {:highlight {:opacity "0.55"}
    :rim       {:top-opacity "0.9" :bottom-opacity "0.05"}}
   :liquid-glass/radius
   {:sm "10px" :md "16px" :lg "24px" :pill "999px"}
   :liquid-glass/motion
   {:press  {:duration "120ms" :easing "cubic-bezier(.32,.72,0,1)"}
    :settle {:duration "420ms" :easing "cubic-bezier(.22,1,.36,1)"}}})

(def dark-tokens
  "Partial override applied inside `@media (prefers-color-scheme: dark)`. Only
  the entries a dark background actually changes — blur/saturate/radius/motion
  are scheme-independent so they are omitted here (default-tokens values carry
  through unchanged)."
  {:liquid-glass/surface
   {:clear   {:tint "rgba(18,18,22,0.30)"  :border "rgba(255,255,255,0.08)"}
    :regular {:tint "rgba(20,20,24,0.42)"  :border "rgba(255,255,255,0.12)"}
    :thick   {:tint "rgba(16,16,20,0.58)" :border "rgba(255,255,255,0.16)"}}
   :liquid-glass/specular
   {:highlight {:opacity "0.30"}
    :rim       {:top-opacity "0.5" :bottom-opacity "0.02"}}})

(def deep-merge
  "Re-exported from shitsuke.tokens: right-biased recursive merge for token maps."
  shitsuke/deep-merge)

(defn resolve-tokens
  "default-tokens deep-merged with overrides (a partial token map of the same shape)."
  [overrides]
  (deep-merge default-tokens overrides))

(defn resolve-dark-tokens
  "dark-tokens deep-merged with dark-overrides (a partial token map, same shape
  as dark-tokens — i.e. only the scheme-sensitive groups)."
  [dark-overrides]
  (deep-merge dark-tokens dark-overrides))

(defn- css-var-name [group k]
  (str "--liquid-glass-" (name group) "-" (name k)))

(defn- pair->css
  [group k v]
  (cond
    (map? v)
    (str/join "\n" (for [[pk pv] v] (str "  " (css-var-name group k) "-" (name pk) ": " pv ";")))
    :else
    (str "  " (css-var-name group k) ": " v ";")))

(defn- tokens->body [tokens]
  (str/join "\n"
            (for [[group m] tokens
                  [k v] m
                  :when (some? v)]
              (pair->css group k v))))

(defn css-variables
  "Emit a `:root { ... }` CSS string from the light material (default merged
  with overrides)."
  ([] (css-variables nil))
  ([overrides] (str ":root {\n" (tokens->body (resolve-tokens overrides)) "\n}")))

(defn dark-css-variables
  "Emit a `@media (prefers-color-scheme: dark) { :root { ... } }` CSS string
  that re-declares only the scheme-sensitive custom properties (dark-tokens
  merged with dark-overrides)."
  ([] (dark-css-variables nil))
  ([dark-overrides]
   (str "@media (prefers-color-scheme: dark) {\n:root {\n"
        (tokens->body (resolve-dark-tokens dark-overrides))
        "\n}\n}")))
