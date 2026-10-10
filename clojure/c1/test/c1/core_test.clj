(ns c1.core-test
  (:require [clojure.test :refer [deftest is]]
            [c1.core :refer [no-repeat-strings]]))

;; Пример из условия. Порядок не важен, поэтому сравниваем множества.
(deftest example-from-task
  (is (= #{"ab" "ac" "ba" "bc" "ca" "cb"}
         (set (no-repeat-strings '("a" "b" "c") 2)))))
