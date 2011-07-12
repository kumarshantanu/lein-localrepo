(ns leiningen.localrepo
  (:require
    [leiningen.util.maven :as mvn]
    [leiningen.install    :as ins]
    [leiningen.pom       :as pom]
    [clojure.java.io      :as jio]
    [clojure.string       :as str])
  (:import
    (java.io File)
    (java.util.jar JarFile)
    (org.apache.maven.artifact.installer ArtifactInstaller)))


(defn illegal-arg
  "Throw IllegalArgumentException using the args"
  [arg & more]
  (let [msg (apply str (interpose " " (into [arg] more)))]
    (throw (IllegalArgumentException. msg))))


(defn split-artifactid
  "Given 'groupIp/artifactId' string split them up and return
  as a vector of 2 elements."
  [^String artifact-id]
  (let [tokens (str/split artifact-id #"/")
        tcount (count tokens)
        [gi ai] tokens]
    (if (or (zero? tcount)
            (> tcount 2)
            (nil? gi))
      (illegal-arg "Invalid groupId/artifactId:" artifact-id)
      (if (nil? ai)
          [gi gi]
          [gi ai]))))


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


(defn c-coords
  "Guess Leiningen coordinates of given filename.
  Example:
  Input  -  local/jars/foo-bar-1.0.6.jar
  Output - foo-bar-1.0.6.jar foo-bar 1.0.6"
  [^String filepath]
  (let [filename (pick-filename filepath)
        tokens (drop-last
                 (re-find (re-matcher #"(.+)\-(\d.+)\.(\w+)"
                                      filename)))
        [_ artifact-id version] tokens]
    (println filepath (str artifact-id "/" artifact-id) version)))


(defn c-install
  "Install artifact to local repository"
  [filename artifact-id version]
  (let [the-file   (jio/file filename)
        [gid aid]  (split-artifactid artifact-id)
        project    {:name  aid
                    :group gid
                    :version version
                    :root    "."
                    :source-path ""
                    :test-path   ""
                    :resources-path     ""
                    :dev-resources-path ""}
        model      (mvn/make-model project)
        artifact   (mvn/make-artifact model)
        installer  (.lookup mvn/container ArtifactInstaller/ROLE)
        local-repo (mvn/make-local-repo)]
    ;(when (not= "pom" (.getPackaging model))
    ;      (mvn/add-metadata artifact (jio/file (pom/pom project))))
    (ins/install-shell-wrappers (JarFile. the-file))
    (.install installer the-file artifact local-repo)))


(defn c-list
  "List artifacts in local Maven repo"
  [& args]
  (println "Not yet implemented"))


(defn c-remove
  "Remove artifacts from local Maven repo"
  [& args]
  (println "Not yet implemented"))


(defn c-help
  "Display help for plugin, or for specified command"
  ([]
    (println "
Leiningen plugin to work with local Maven repository.

coords   Guess Leiningen (Maven) coords of a file
install  Install artifact to local repository
list     List artifacts in local repository    (Not Yet Implemented)
remove   Remove artifact from local repository (Not Yet Implemented)
help     This help screen
"))
  ([command]
    (case command
      "coords"  (doc c-coords)
      "install" (doc c-install)
      "list"    (doc c-list)
      "remove"  (doc c-remove)
      "help"    (doc c-help)
      (illegal-arg "Illegal command:" command
        ", Allowed: coords, install, list, remove, help"))))


(defn apply-cmd
  [pred cmd f args]
  (if (pred) (apply f args)
             (c-help cmd)))


(defn localrepo
  "Work with local Maven repository"
  ([]
    (c-help))
  ([command & args]
    (let [argc (count args)]
      (case command
        "coords"  (apply-cmd #(=  argc 1)     command c-coords  args)
        "install" (apply-cmd #(=  argc 3)     command c-install args)
        "list"    (apply-cmd #(>= argc 0)     command c-list    args)
        "remove"  (apply-cmd #(>= argc 0)     command c-remove  args)
        "help"    (apply-cmd #(or (= argc 0)
                                  (= argc 1)) command c-help    args)
        (c-help)))))
