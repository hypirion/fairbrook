(ns fairbrook.meta)

(defn mono-apply
  "Applies f with more-params and elements on both the data and the metadata of
  the elements. Returns the result with the resulting metadata attached."
  {:arglists '([[f & more-params] elements])}
  [[f & m] elts]
  (with-meta
    (apply f (concat m elts))
    (apply f (concat m (map meta elts)))))

(defn duo-apply
  "Applies f with more-params and elements, and meta-f with meta-more-params and
  the metadata of the elements. Returns the result with the resulting metadata
  attached."
  {:arglists '([[f & more-params] [meta-f & meta-more-params] elements])}
  [[f & m] [mf & mm] elts]
  (with-meta
    (apply f (concat m elts))
    (apply mf (concat mm (map elts)))))

(defn mono-reduce
  "Performs a reduction on elements and their metadata with f. Returns the
  result with the resulting metadata attached."
  [f elements]
  (with-meta
    (reduce f elements)
    (reduce f (map meta elements))))

(defn duo-reduce
  "Performs a reduction on elements with f, and their metadata with
  meta-f. Returns the result with the resulting metadata attached."
  [f meta-f elements]
  (with-meta
    (reduce f elements)
    (reduce meta-f (map meta elements))))

(defn meta-merge
  "As merge, but will merge the metadata from each map as well. As with merge,
  if more than one of the metadata maps contains the same key, the mapping from
  the latter will be the mapping in the resulting metadata map."
  [& maps]
  (mono-reduce merge maps))

(defn meta-merge-with
  "As merge-with, but will merge the metadata from each map as well. If a key
  occurs in more than one map or metamap, the mapping(s) from the
  latter (left-to-right) will be combined with the mapping in the result by
  calling (f val-in-result val-in-latter)."
  [f & maps]
  (mono-reduce
   (fn [m1 m2]
     (merge-with f m1 m2))
   maps))
