(ns leiningen.localrepo.internal
  "Utility functions - mostly taken/adapted from Clj-MiscUtil:
  https://bitbucket.org/kumarshantanu/clj-miscutil/src"
  (:require [clojure.string :as str]
            [clojure.pprint :as ppr])
  (:import  (java.io File)))


(defn illegal-arg
  "Throw IllegalArgumentException using the args"
  [arg & more]
  (let [msg (apply str (interpose " " (into [arg] more)))]
    (throw (IllegalArgumentException. msg))))


(defn ^String java-filepath
  "Accept path (of a file) as argument and return a uniform file path for all
  operating systems.
  Example: \"C:\\path\\to\\file.txt\" becomes \"C:/path/to/file.txt\"
  See also: split-filepath"
  [s]
  (let [p (if (instance? File s) (.getAbsolutePath ^File s)
            (str s))]
    (.replace ^String p "\\" "/")))


(defn ^String split-filepath
  "Given a complete path, split into filedir and filename and return as vector.
  The filedir is normalized as uniform Java filepath.
  See also: java-filepath"
  [s]
  (let [jf (java-filepath s)
        sf (str/split jf #"/")]
    [(str/join "/" (drop-last sf)) (last sf)]))


(defn ^String pick-dirname
  "Given a filepath, return the directory portion from it."
  [s]
  (first (split-filepath s)))


(defn ^String pick-filename
  "Given a filepath, return the filename portion from it."
  [s]
  (last (split-filepath s)))


(defn split-filename
  "Given a partial or complete file path, split into filename and extension and
  return as vector."
  [s]
  (let [f   (pick-filename s)
        sfe (str/split f #"\.")
        sfc (count sfe)
        sf1 (first sfe)
        sf2 (second sfe)]
    (cond
      (= 1 sfc)                    (conj sfe "")
      (and (= 2 sfc) (empty? sf1)) [(str "." sf2) ""]
      :else                        [(str/join "." (drop-last sfe)) (last sfe)])))


(defn ^String pick-filename-name
  "Given a filepath, return the filename (without extension) portion from it."
  [s]
  (first (split-filename s)))


(defn ^String pick-filename-ext
  "Given a filepath, return the file extension portion from it."
  [s]
  (last (split-filename s)))


(defn relative-path
  "Given base path and an absolute path, finds the relative path."
  [^String base ^String path]
  ;; new File(base).toURI()
  ;;   .relativize(new
  ;;     File(path).toURI()).getPath();
  (let [base-uri (-> base
                     File.
                     .toURI)
        path-uri (-> path
                     File.
                     .toURI)]
    (-> base-uri
        (.relativize path-uri)
        .getPath)))


(defn dir?
  "Return true if `d` is a directory, false otherwise."
  [d]
  (and (instance? File d)
       (.isDirectory d)))


(defn dir-or-file
  "Return :dir if `f` (java.io.File) is a directory, :file otherwise."
  [f]
  (assert (instance? File f))
  (if (dir? f) :dir :file))


(defn not-contains?
  "Same as (not (contains? haystack needle))"
  [haystack needle]
  (not (contains? haystack needle)))


(defn common-keys
  "Find the common keys in maps `m1` and `m2`."
  [m1 m2]
  (let [m1-ks (into #{} (keys m1))
        m2-ks (into #{} (keys m2))]
    (filter #(contains? m2-ks %) m1-ks)))


(defn add-into
  ""
  [old-map new-map]
  (let [com-ks (into [] (common-keys old-map new-map))
        com-kv (zipmap com-ks
                       (map #(do [(get old-map %)
                                  (get new-map %)])
                            com-ks))]
    (merge (merge old-map new-map) com-kv)))


(defn as-vector
  "Convert/wrap given argument as a vector."
  [anything]
  (if (vector? anything) anything
    (if (or (seq? anything) (set? anything)) (into [] anything)
      (if (map? anything) [anything]
        (if (nil? anything) []
          [anything])))))


(defn merge-incl
  "Merge two maps inclusively. Values get wrapped inside a vector, e.g
  user=> (merge-incl {:a 10 :b 20} {:b 30 :c 40})
  {:a [10] :b [20 30] :c [40]}"
  [old-map new-map]
  (let [common-ks (into [] (common-keys old-map new-map))
        old-keys (keys old-map)
        new-keys (keys new-map)
        all-keys (distinct (into old-keys new-keys))]
    (reduce  (fn [m each-k]
               (let [old-v (get old-map each-k)
                     new-v (get new-map each-k)
                     added (into (as-vector old-v)
                                 (as-vector new-v))]
                 (into m {each-k added})))
            {} all-keys)))


(defn xml-map
  "Discard attributes in XML structure `e` and turn elements as keys
  with respective values in a nested map. Values wind up in vectors.
  Example:
  (clojure.xml/parse (slurp \"filename.xml\"))"
  [e]
  (let [is-map?  #(or (map? %) (instance? clojure.lang.MapEntry %))
        tag?     #(and (is-map? %)
                       (contains? % :tag))
        not-tag? (comp not tag?)]
    (cond
     (tag? e)  (let []
                 {(:tag e) (xml-map (:content e))})
     (map? e)  (let []
                 [e])
     (coll? e) (cond
                (some not-tag? e) (let [v (as-vector
                                           (map xml-map e))
                                        [w] v]
                                    (if (and (= (count v) 1)
                                             (vector? w))
                                      w v))
                :else             (let [r (reduce merge-incl {}
                                                  (map xml-map e))]
                                    r))
     :else     (let []
                 e))))
