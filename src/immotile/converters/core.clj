(ns immotile.converters.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [immotile.config :as c]
   [hawk.core :as hawk]
   [hiccup.core :refer [html]]))

(defonce ^:private state (atom {}))

(defn- create-folders [path] (.mkdirs (io/file path)))
(defn- get-file-path [f] (str/join "/" (drop 1 (str/split (.getPath f) #"/"))))
(defn- read-template-fn [path] (load-file path))
(defn- remove-newlines [s] (str/replace s #"\n" ""))

(defn- copy-to-out
  [out-path file]
  (let [destination (str out-path "/" (get-file-path file))]
    (io/make-parents destination)
    (io/copy
     file
     (io/file destination))))


(defn- generate-page
  [template-fn data]
  (->> (template-fn data)
       (html)
       (remove-newlines)
       (str "<!doctype html>")))


(defmulti convert
  (fn [config ^java.io.File file]
    (keyword (last (str/split (.getAbsolutePath file) #"\.")))))

(defmethod convert :edn [config file])
(defmethod convert :default [config file] (copy-to-out (:out config) file))

(defn- filename-without-extension
  [^java.io.File file]
  (-> file
      .getName
      (str/split #"\.")
      drop-last
      (->> (str/join "."))))

(filename-without-extension (io/file "im-src/pages/index.org"))

(defn- write-file
  [config file sub-path]
  (let [page-data (convert config file)
        filename (filename-without-extension file)
        dest (str (:out config) "/" sub-path filename ".html")]
    (io/make-parents (io/file dest))
    (spit dest
          (generate-page
           (read-template-fn "resources/templates/default.clj")
           page-data))))

(defn- write-page [config file] (write-file config file ""))

(defn- write-post [config file] (write-file config file "posts/"))

(defn- post? [file] (boolean (re-find #"/posts/" (.getPath file))))
(defn- page? [file] (boolean (re-find #"/pages/" (.getPath file))))
(defn- directory? [file] (.isDirectory file))
(defn- config-file? [file] (boolean (re-find #"config.edn" (.getName file))))

(defn regenerate-file
  [config {file :file kind :kind}]
  (condp #(%1 %2) file
    directory? nil
    config-file? nil
    post? (write-post config file)
    page? (write-page config file)
    (copy-to-out (:out config) file)))

(defn process-all-source-files
  [config]
  (doall
   (pmap (fn [f] (when (.isFile f) (regenerate-file config {:file f})))
         (drop 1 (file-seq (io/file "im-src"))))))

(defn start-watcher
  [config]
  (let [w (hawk/watch! [{:paths ["im-src/"]
                         :handler (fn [_ x] (regenerate-file config x))}])]
    (swap! state (fn [x] w))))


(defn stop-watcher [] (hawk/stop! @state) (reset! state nil))


(defn start
  [config]
  (create-folders (:out config))
  (process-all-source-files config)
  (start-watcher config))
