(ns immotile.core
  (:require [cljs.build.api :as cljs]
            [clojure.core.async :as a :refer [<!! thread]]
            [immotile.config :refer [config]]
            [immotile.file-processing :as process]
            [immotile.init :as init]
            [immotile.system :as system]
            [immotile.page-utils :as page-utils] ;; require to make it usable on pages
            [clojure.java.io :as io]))

(defn delete-folder
  [path]
  (doseq [f (reverse (file-seq (io/file path)))]
     (io/delete-file f)))

(defn run-build-prod
  [{src :src out :out :as conf}]
  (mapv <!! [(thread (process/all-files (assoc conf :out "build")))
             (thread (cljs/build (str src "/cljs/")
                                 {:optimizations :advanced
                                  :output-dir out
                                  :output-to (str "build/" "js/main.js")}))])
  (delete-folder out))

(defn run-dev
  [conf]
  (thread (process/all-files conf))
  (system/new-system conf)
  (system/start))

(defn run-init
  [project-name]
  (if project-name
    (init/make-project project-name)
    (println "A project name must be entered...")))

(defn -main
  [& args]
  (case (keyword (first args))
    :build-prod (run-build-prod (config))
    :dev (run-dev (config))
    :init (run-init (second args))
    (println "Valid args are build-prod, dev or init")))
