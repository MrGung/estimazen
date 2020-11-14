(defproject estamizen "0.1.0"
  :description "minimalist agile distributed estimation tool"
  :url "https://github.com/MrGung/estimazen"
  :license {:name "The Unlicense"
            :url "https://unlicense.org/"
            :distribution :repo}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert* true}

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/clojurescript "1.10.773"]
   [org.clojure/core.async "1.3.610"]
   [org.clojure/tools.nrepl "0.2.13"]                       ; Optional, for Cider

   [com.taoensso/sente "1.16.0"]                            ; <--- Sente
   [com.taoensso/timbre "4.10.0"]

   [http-kit "2.5.0"]                                       ; Default

   [ring "1.8.1"]
   [ring/ring-defaults "0.3.2"]

   [compojure "1.6.2"]                                      ; Or routing lib of your choice
   [hiccup "1.0.5"]]                                        ; Optional, just for HTML

  ;;; Transit deps optional; may be used to aid perf. of larger data payloads
  ;;; (see reference example for details):
  ;;[com.cognitect/transit-clj  "1.0.324"]
  ;;[com.cognitect/transit-cljs "0.8.264"]]

  :plugins
  [[lein-pprint "1.3.2"]
   [lein-ancient "0.6.15"]
   ;[com.cemerick/austin "0.1.6"]
   [lein-cljsbuild "1.1.8"]]


  :cljsbuild
  {:builds
   [{:id :cljs-client
     :source-paths ["src"]
     :compiler {:output-to "resources/public/main.js"
                :optimizations :whitespace #_:advanced
                :pretty-print true}}]}

  :main estimazen.server

  :clean-targets ^{:protect false} ["resources/public/main.js"]

  ;; Call `lein start-repl` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"start-repl" ["do" "clean," "cljsbuild" "once," "repl" ":headless"]
   "start" ["do" "clean," "cljsbuild" "once," "run"]}

  :repositories
  {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
