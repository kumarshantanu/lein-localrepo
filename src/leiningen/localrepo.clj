(ns leiningen.localrepo
  (:require
    [leiningen.util.maven :as mvn]
    [leiningen.install    :as ins]
    [leiningen.pom        :as pom]
    [clojure.java.io      :as jio]
    [clojure.string       :as str]
    [clojure.pprint       :as ppr]
    [clojure.xml          :as xml]
    [leiningen.localrepo.internal :as in])
  (:import
    (java.io File)
    (java.util.jar JarFile)
    (org.apache.maven.artifact.installer ArtifactInstaller)))


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
      (in/illegal-arg "Invalid groupId/artifactId:" artifact-id)
      (if (nil? ai)
          [gi gi]
          [gi ai]))))


(defn c-coords
  "Guess Leiningen coordinates of given filename.
  Example:
  Input  -  local/jars/foo-bar-1.0.6.jar
  Output - foo-bar-1.0.6.jar foo-bar 1.0.6"
  [^String filepath]
  (let [filename (in/pick-filename filepath)
        tokens   (drop-last
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


(defn read-artifact-description
  [pom-file]
  (if (.isFile pom-file)
    (let [raw-content (slurp pom-file)
          xml-content (xml/parse pom-file)
          map-content (in/xml-map xml-content)]
      ;(with-out-str (ppr/pprint xml-content) (ppr/pprint map-content))
      (first (:description (:project map-content))))
    "(No description available)"))


(defn read-artifact-entries
  "Read artifact entries from specified `dir` and return as a list. If
  dir contains only sub-dirs then it recurses to find actual entries."
  [dir]
  (assert (in/dir? dir))
  (let [entries         (filter #(not (.startsWith (.getName %) "."))
                                (.listFiles dir))
        {subdirs :dir
         nondirs :file} (group-by in/dir-or-file entries)]
    (if (not (empty? subdirs))
      (reduce into [] (map read-artifact-entries subdirs))
      (let [ignore-ext #{"lastUpdated" "pom" "properties"
                         "repositories" "sha1" "xml"}
            arti-file? #(in/not-contains? ignore-ext
                                          (in/pick-filename-ext %))]
        (for [each (filter #(arti-file? (.getName %)) nondirs)]
          ;; parent = version, parent->parent = artifactId
          ;; parent->parent->parent[relative-path] = groupId
          (let [parent  (.getParentFile each)
                version (.getName parent)
                artifact-id (.getName (.getParentFile parent))
                group-path  (in/java-filepath
                             (in/relative-path mvn/local-repo-path
                                               (-> parent
                                                   .getParentFile
                                                   .getParentFile
                                                   .getAbsolutePath)))
                group-clean (let [slash? #(= \/ %)
                                  rtrim  #(if (slash? (last  %)) (drop-last %) %)
                                  ltrim  #(if (slash? (first %)) (rest %)      %)]
                              (apply str (-> group-path rtrim ltrim)))
                group-id    (str/replace group-clean "/" ".")]
            [group-id
             artifact-id
             version
             (.getName each)
             (read-artifact-description
              (jio/file (let [[dir fne] (in/split-filepath
                                         (.getAbsolutePath each))
                              [fnm ext] (in/split-filename fne)]
                          (str dir "/" fnm ".pom"))))]))))))


(defn c-list
  "List artifacts in local Maven repo"
  [& args]
  (if (and (not (zero? (count args)))
           (or (> (count args) 1)
               (not (contains? #{"-f" "-g"} (first args)))))
    (println "Invalid argument(s):" (apply str (interpose " " args))
             " ==>  Allowed: [-f]")
    (let [artifact-entries (read-artifact-entries
                            (jio/file mvn/local-repo-path))
          by-group-id (group-by first artifact-entries)]
      (doseq [group-id (keys by-group-id)]
        (if (nil? (first args))
          ;; default - print it like lein-search
          (let [group-artifacts (get by-group-id group-id)
                with-pomfile (map #(let [fnm (last %)
                                         ] into [] (drop-last %))
                                      group-artifacts)]
            (doseq [artifact group-artifacts]
              (let [[gid aid ver fnm des] artifact
                    artifact-descript des]
                (println (format "[%s/%s \"%s\"] %s"
                                 gid aid ver artifact-descript)))))
          ;; custom format
          (do
            (println (format "[%s]" group-id))
            (let [by-artifact-id (group-by second
                                           (get by-group-id group-id))]
              (doseq [artifact-id (keys by-artifact-id)]
                (let [artifacts (get by-artifact-id artifact-id)
                      description (last (first artifacts))
                      versions (distinct (map #(nth % 2) artifacts))]
                  (case (or (first args) :nil)
                        "-g" (println
                              (format "  %s/%s (%s) - %s"
                                      group-id artifact-id
                                      (apply str (interpose ", "
                                                            versions))
                                      (or description "")))
                        "-f" (do (println (format "  %s" artifact-id))
                                 (doseq [each-v versions]
                                   (println (format "    [%s]" each-v))
                                   (let [artifacts (get by-artifact-id
                                                        artifact-id)]
                                     (doseq [each-a (filter #(= each-v
                                                                (nth % 2))
                                                            artifacts)]
                                       (println
                                        (format "      %s/%s - %s"
                                                group-id artifact-id
                                                (last (butlast each-a))))))))
                        "-d" (println
                              "Detail option is not yet implemented")
                        (println
                         (str "Bad arg(s): "
                              (apply str (interpose " " args))))))))))))))


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
list     List artifacts in local repository
remove   Remove artifact from local repository (Not Yet Implemented)
help     This help screen

For help on individual commands use 'help' with command name, e.g.:

$ lein localrepo help install
"))
  ([command]
    (case command
      "coords"  (doc c-coords)
      "install" (doc c-install)
      "list"    (doc c-list)
      "remove"  (doc c-remove)
      "help"    (doc c-help)
      (in/illegal-arg "Illegal command:" command
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
        "list"    (apply-cmd #(or (= argc 0)
                                  (= argc 1)) command c-list    args)
        "remove"  (apply-cmd #(>= argc 0)     command c-remove  args)
        "help"    (apply-cmd #(or (= argc 0)
                                  (= argc 1)) command c-help    args)
        (c-help)))))
