(ns leiningen.localrepo.internal
  "Utility functions - mostly taken/adapted from Clj-MiscUtil:
  https://bitbucket.org/kumarshantanu/clj-miscutil/src"
  (:require [clojure.string :as str])
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
