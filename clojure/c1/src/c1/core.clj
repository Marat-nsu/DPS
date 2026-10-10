(ns c1.core)

(defn extend-string [s alphabet]
  (remove #(= (last s) (last %)) (map #(str s %) alphabet)))

(defn extend-all [strs alphabet]
  (reduce concat (map #(extend-string % alphabet) strs)))

(defn no-repeat-strings 
  [alphabet n]
  (reduce (fn [strs _] (extend-all strs alphabet)) [""] (range n)))
