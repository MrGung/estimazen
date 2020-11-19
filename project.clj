(defproject estimazen "0.1.0"
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
   [ring-cors "0.1.7"]

   [compojure "1.6.2"]                                      ; Or routing lib of your choice
   [hiccup "1.0.5"]]                                         ; Optional, just for HTML


  :plugins
  [[lein-pprint "1.3.2"]                                    ;; pprinting project map
   [lein-ancient "0.6.15"]                                  ;; A Leiningen plugin to check your project for outdated dependencies and plugins.
   ;[com.cemerick/austin "0.1.6"] ;; cljs REPL
   [lein-cljsbuild "1.1.8"]]                                ;; compile ClojureScript into Javascript whenever modified



  :cljsbuild {:builds
              [{:id :cljs-client
                :source-paths ["src"]
                :compiler {:output-to "resources/public/main.js"
                           :main estimazen.client
                           :optimizations :none #_:whitespace #_:advanced
                           :source-map true
                           :pretty-print true}}]}
  :main estimazen.server

  ;; files removed by `lein clean`
  :clean-targets ^{:protect false} ["resources/public/main.js"]

  ;; Call `lein start-repl` to get a (headless) development repl that you can
  ;; connect to with Cider+emacs or your IDE of choice:
  :aliases
  {"start-repl" ["do" "clean," "cljsbuild" "once," "repl" ":headless"]
   "start" ["do" "clean," "cljsbuild" "once," "run"]}

  :repositories
  {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
