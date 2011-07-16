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
    (java.util Date)
    (java.text DateFormat)
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


(defn read-artifact-details
  "Given a POM file, read project details"
  [pom-file]
  (if (.isFile pom-file)
    (let [raw-content (slurp pom-file)
          xml-content (xml/parse pom-file)
          map-content (in/xml-map xml-content)]
      (with-out-str ;(ppr/pprint xml-content)
        (ppr/pprint map-content)))
    "(No details available)"))


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
             (jio/file each)
             (jio/file (let [[dir fne] (in/split-filepath
                                        (.getAbsolutePath each))
                             [fnm ext] (in/split-filename fne)]
                         (str dir "/" fnm ".pom")))]))))))


(defn c-list
  "List artifacts in local Maven repo"
  [& args]
  (let [artifact-entries (sort (read-artifact-entries
                                (jio/file mvn/local-repo-path)))
        artifact-str  (fn artstr
                        ([gi ai] (if (= gi ai) ai (str gi "/" ai)))
                        ([[gi ai & more]] (artstr gi ai)))
        flag          (or (first args) :nil)
        invalid-flag? (not (contains? #{:nil "-s" "-f" "-d"} flag))
        each-artifact (fn [f] ; args to f: 1. art-name, 2. artifacts
                        (let [by-art-id (group-by artifact-str
                                                  artifact-entries)]
                          (doseq [art-str (keys by-art-id)]
                            (f art-str (get by-art-id art-str)))))
        df            (DateFormat/getDateTimeInstance)
        date-format   #(.format df %)
        ljustify      (fn [s n]
                        (let [s (str/trim (str s))]
                          (if (> (count s) n) s
                              (apply str
                                     (take n (concat
                                              s (repeat n \space)))))))
        rjustify      (fn [s n]
                        (let [s (str/trim (str s))]
                          (if (> (count s) n) s
                              (apply str
                                     (take-last
                                      n (concat (repeat n \space)
                                                s))))))]
    (cond
     invalid-flag? (println "Invalid argument(s):" (str/join " " args)
                            " ==>  Allowed: [-s | -f | -d]")
     (= :nil flag) (each-artifact
                    (fn [art-name artifacts]
                      (println
                       (format "%s (%s)" art-name
                               (str/join ", "
                                         (for [[g a v f p] artifacts]
                                           v))))))
     (= "-s" flag) (each-artifact
                    (fn [art-name artifacts]
                      (println
                       (format "%s (%s) -- %s" (ljustify art-name 20)
                               (str/join ", "
                                         (for [[g a v f p] artifacts]
                                           v))
                               (or (some #(read-artifact-description
                                           (last %)) artifacts)
                                   "")))))
     (= "-f" flag) (each-artifact
                    (fn [art-name artifacts]
                      (doseq [each artifacts]
                        (let [[g a v ^File f] each
                              an (ljustify (format "[%s \"%s\"]"
                                                   art-name v) 30)
                              nm (ljustify (.getName f) 30)
                              sp (ljustify (format "%s %s" an nm) 62)
                              ln (rjustify (.length f)
                                           (min (- 70 (count sp)) 10))]
                          (println
                           (format "%s %s %s" sp ln
                                   (date-format
                                    (Date. (.lastModified f)))))))))
     (= "-d" flag) (each-artifact
                    (fn [art-name artifacts]
                      (println
                       (format "%s (%s)\n%s" (ljustify art-name 20)
                               (str/join ", "
                                         (for [[g a v f p] artifacts]
                                           v))
                               (or (some #(read-artifact-details
                                           (last %)) artifacts)
                                   ""))))))))


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
