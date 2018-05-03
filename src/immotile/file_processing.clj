(ns immotile.file-processing
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup.core :refer [html]]
            [immotile.converters.core :as c]))

(defn create-file
  [config page-data]
  (let [type (:type page-data)
        sub-path (str (when type (str (name type) "/"))
                      (:filename page-data) ".html")
        dest (str (:out config) "/" sub-path)
        template-fn (load-file "im-src/templates/default.clj")]
     (io/make-parents (io/file dest))
     (spit dest (->> (template-fn page-data)
                     (html)
                     (str "<!doctype html>")))
     (merge (dissoc page-data :body) {:link sub-path})))

(defn- destination
  [file]
  (-> (.getPath file)
      (str/split #"/")
      (rest)
      (->> (str/join "/"))
      (str/replace "public/" "")))

(defn- copy-public-to-out
  "Copies `file` to public folder in `out-path`."
  [out-path file]
  (let [dest (str out-path "/" (destination file))]
    (io/make-parents dest)
    (io/copy file (io/file dest))))

(defn- is-of-path [regexp file] (boolean (re-find regexp (.getPath file))))
(defn- directory? [file] (.isDirectory file))
(defn- public? [file] (is-of-path #"/public" file))
(defn- post? [file] (is-of-path #"/posts/" file))
(defn- page? [file] (is-of-path #"/pages/" file))

(def posts (atom nil))

(defn- single-file
  [config file]
  (cond
    (directory? file) nil
    (post? file) (create-file config (assoc (c/convert config file) :type :post))
    (page? file) (create-file config (c/convert (assoc config :posts @posts) file))
    (public? file) (copy-public-to-out (:out config) file)
    :else nil))

(defn- process-posts
  [config]
  (let [files (->> (file-seq (io/file "im-src/posts/"))
                   (filter #(.isFile %)))]
    (doall (pmap (fn [file] (create-file config (assoc (c/convert config file) :type :post))) files))))

(defn all-files
  [config]
  (let [paths-to-ignore (re-pattern (str/join "|" ["/templates/" "config.edn" "/posts/"]))
        files (->> (file-seq (io/file "im-src"))
                   (remove #(re-find paths-to-ignore (.getPath %)))
                   (filter #(.isFile %)))]
    (reset! posts (process-posts config))
    (doall (pmap (partial single-file config) files))))

(defn- template? [file] (is-of-path #"/templates/" file))

(defn file
  [config file]
  (if (template? file)
    (do
      (println "Regenerating all files...")
      (time (all-files config)))
    (do
      (println "Regenerating file " (.getPath file))
      (time (single-file config file)))))
