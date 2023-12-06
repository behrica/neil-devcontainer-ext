(ns behrica.adddev.main
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [babashka.http-client :as http]
   [behrica.adddev.devcontainer :as devc]
   [babashka.cli :as cli]
   [clojure.edn :as edn]
   [cheshire.core :as json]
   [zprint.core :as zp]))


(def cli-options {:with-python {:default false :coerce :boolean}
                  :with-R {:default false :coerce :boolean}})


(defn download []
  (io/copy
   (:body (http/get "https://github.com/behrica/add-devcontainer/raw/main/devcontainer.zip"
                    {:as :stream}))
   (io/file "/tmp/devcontainer.zip")))

(defn update-deps-edn [fragments]
  (binding [*print-namespace-maps* false]
    (as-> (edn/read-string (slurp "deps.edn")) it
      (update it :deps (fn [deps] (merge deps (:deps fragments))))

      (zp/zprint-str it {:map {:comma? false}})
      (spit "deps.edn" it))))

(defn generate-devcontainer-json [fragments]
  (spit ".devcontainer/devcontainer.json"
            (json/generate-string
             (devc/make-dev-container-spec "dummy" (:features fragments))
             {:pretty true})))

(defn -main [& _]

  (let [opts
        (cli/parse-opts *command-line-args*
                        {:restrict (keys cli-options)
                         :spec cli-options
                         :exec-args {:with-R false
                                     :with-python false}})
        fragments (devc/template-fn {} opts)]

    (if (fs/exists? ".devcontainer")
      (println ".devcontainer exists. Skipp")

      (do
        (download)
        (fs/unzip "/tmp/devcontainer.zip" ".")
        (generate-devcontainer-json fragments)
        (println ".devcontainer folder created")
        (update-deps-edn fragments)
        (println "deps.edn enhanced.")))))
