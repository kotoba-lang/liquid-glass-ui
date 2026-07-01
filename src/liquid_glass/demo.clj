(ns liquid-glass.demo
  "Static showcase page for GitHub Pages (docs/index.html). Pure dogfood: every
  element on the page is built from liquid-glass.components hiccup, rendered
  SSR via shitsuke.hiccup/->html — the same fn a JVM/babashka consumer would
  call. No JS, no build step; the glass material is CSS only (backdrop-filter)
  and needs no reagent mount to render correctly.

  Usage: clojure -M:local -m liquid-glass.demo"
  (:require [clojure.java.io :as io]
            [shitsuke.hiccup :as h]
            [liquid-glass.style :as s]
            [liquid-glass.components :as lg]))

(def ^:private page-css
  "html{min-height:100%}
body{margin:0;min-height:100vh;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
     color:#1c1c1e;background:linear-gradient(135deg,#ff6b6b 0%,#f7b733 22%,#4ecdc4 48%,#556fb5 72%,#7b2ff7 100%);
     background-attachment:fixed;}
.demo-shell{max-width:960px;margin:0 auto;padding:48px 24px 96px;}
.demo-header{color:#fff;text-shadow:0 2px 12px rgba(0,0,0,.35);margin-bottom:36px;}
.demo-header h1{font-size:28px;margin:0 0 10px;}
.demo-header p{margin:0;opacity:.92;max-width:680px;line-height:1.55;font-size:14px;}
.demo-header a{color:#fff;}
.demo-section{margin:40px 0;}
.demo-section h2{color:#fff;text-shadow:0 2px 10px rgba(0,0,0,.3);font-size:13px;text-transform:uppercase;
     letter-spacing:.08em;margin:0 0 14px;font-weight:700;}
.demo-row{display:flex;gap:16px;flex-wrap:wrap;align-items:flex-start;}
.demo-panel-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;}
.demo-panel-grid .liquid-glass__panel,.demo-row .liquid-glass__panel{color:#fff;}
.demo-panel-grid .liquid-glass__panel h3{margin:0 0 6px;font-size:15px;}
.demo-panel-grid .liquid-glass__panel p{margin:0;font-size:13px;opacity:.85;line-height:1.5;}
.demo-sheet-wrap{position:relative;max-width:360px;}
.demo-sheet-wrap .liquid-glass__scrim{position:static;border-radius:20px 20px 0 0;height:36px;}
.demo-sheet-wrap .liquid-glass__sheet{color:#fff;}
.demo-sheet-wrap .liquid-glass__sheet h3{margin:0 0 6px;}
.demo-sheet-wrap .liquid-glass__sheet p{margin:0 0 16px;font-size:13px;opacity:.85;line-height:1.5;}
.liquid-glass__button,.liquid-glass__icon-button{color:#fff;}
.demo-dark{background:#0b0b10;border-radius:20px;padding:28px;
     --liquid-glass-surface-clear-tint:rgba(18,18,22,0.30);--liquid-glass-surface-clear-border:rgba(255,255,255,0.08);
     --liquid-glass-surface-regular-tint:rgba(20,20,24,0.42);--liquid-glass-surface-regular-border:rgba(255,255,255,0.12);
     --liquid-glass-surface-thick-tint:rgba(16,16,20,0.58);--liquid-glass-surface-thick-border:rgba(255,255,255,0.16);
     --liquid-glass-specular-highlight-opacity:0.30;
     --liquid-glass-specular-rim-top-opacity:0.5;--liquid-glass-specular-rim-bottom-opacity:0.02;}
.demo-dark .liquid-glass__panel,.demo-dark .liquid-glass__toolbar,.demo-dark .liquid-glass__button,
.demo-dark .liquid-glass__icon-button{color:#fff;}
.demo-footer{color:#fff;opacity:.75;font-size:12px;text-align:center;margin-top:64px;}
.demo-footer a{color:#fff;}")

(defn- variant-panel [title blurb variant]
  (lg/panel [[:h3 title] [:p blurb]] {:surface variant}))

(defn page []
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "liquid-glass-ui — kotoba-lang"]
    [:link {:rel "icon" :href "data:,"}]
    (s/inline-style-hiccup (str (s/root-css) "\n" (s/component-css)))
    [:style page-css]]
   [:body
    [:div.demo-shell
     [:header.demo-header
      [:h1 "liquid-glass-ui"]
      [:p "kotoba-lang's shared liquid-glass visual skin — material tokens + a portable CSS style
           layer + pure-hiccup components, built on "
       [:a {:href "https://github.com/kotoba-lang/shitsuke" :target "_blank"} "shitsuke"]
       ". " [:a {:href "https://github.com/kotoba-lang/liquid-glass-ui"} "Source"]
       " · " [:a {:href "https://github.com/kotoba-lang/liquid-glass-ui/blob/main/docs/design.md"} "Design docs"]]]

     [:section.demo-section
      [:h2 "Toolbar"]
      (lg/toolbar [(lg/icon-button "☰") (lg/icon-button "🔍") (lg/badge "3") (lg/icon-button "⚙")])]

     [:section.demo-section
      [:h2 "Tab bar"]
      (lg/tab-bar [[:visual "Visual"] [:edn "EDN"] [:preview "Preview"]] :visual)]

     [:section.demo-section
      [:h2 "Surface variants"]
      [:div.demo-panel-grid
       (variant-panel "clear" "Barely-there — scrims and tooltips." :clear)
       (variant-panel "regular" "Default control surface." :regular)
       (variant-panel "thick" "Toolbars/sheets over busy content." :thick)]]

     [:section.demo-section
      [:h2 "Elevation"]
      [:div.demo-row
       (lg/panel [[:p "flat"]] {:elevation :flat})
       (lg/panel [[:p "raised"]] {:elevation :raised})
       (lg/panel [[:p "overlay"]] {:elevation :overlay})
       (lg/panel [[:p "floating"]] {:elevation :floating})]]

     [:section.demo-section
      [:h2 "Buttons"]
      [:div.demo-row
       (lg/button "Continue" {:act :continue})
       (lg/button "Disabled" {:disabled true})
       (lg/icon-button "♥")]]

     [:section.demo-section
      [:h2 "Sheet"]
      [:div.demo-sheet-wrap
       [:div.liquid-glass__scrim]
       (lg/sheet [[:h3 "Share"]
                  [:p "Bottom-sheet surface — thick material, floating elevation."]
                  (lg/button "Done" {:act :dismiss})]
                 {:label "Share"})]]

     [:section.demo-section
      [:h2 "Dark"]
      [:div.demo-dark
       [:div.demo-row
        (lg/panel [[:p "thick / dark"]] {:surface :thick})
        (lg/toolbar [(lg/icon-button "☰") (lg/badge "3")])
        (lg/button "Continue")]]]

     [:footer.demo-footer "kotoba-lang/liquid-glass-ui · ADR-2607011900"]]]])

(defn html []
  (str "<!doctype html>\n" (h/->html (page)) "\n"))

(defn write!
  ([] (write! "docs"))
  ([dir]
   (let [out (io/file dir "index.html")]
     (io/make-parents out)
     (spit out (html))
     out)))

(defn -main [& _]
  (println (str "wrote " (.getPath (write!)))))
