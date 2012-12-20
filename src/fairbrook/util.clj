(ns fairbrook.util)

(defmacro fn3->fn2
  "Expands to a function form taking three arguments x y z, drops x and calls
  the result of the form f with y and z. If g is specified, will insert a
  modified version of g at the end of f, such that calls on the form (g y z)
  within f is treated as (g x y z). Both forms must return functions, and f must
  expect a function taking two arguments as last parameter if g is specified."
  ([f-form]
     `(let [f# ~f-form]
        (fn [a# b# c#] (f# b# c#))))
  ([f-form g-form]
     `(let [orig-g# ~g-form]
        (fn [x# y# z#]
          (let [g# (fn [*y# *z#] (orig-g# x# *y# *z#))]
            ((~@f-form g#) y# z#))))))

(defmacro prep-args
  "Prepares the parameters given to the function returned by f-form as if called
   by (fn args (res-of-f-form param-mods*)). With g-form, appends a function
   based upon g-form taking any amount of arguments, where any call to it will
   be equivalent of calling (g-form param*). f-form must take as many arguments
   as the amount of of param-mods, and g-form must take as many parameters as
   the size of params. It is okay to have different amount of params and
   param-mods."
  {:arglists '([[params*] [param-mods*] f-form]
                 [[params*] [param-mods*] f-form])}
  ([params param-mods f-form]
     `(let [f# ~f-form]
        (fn ~params (f# ~@param-mods))))
  ([params param-mods f-form g-form]
     `(let [orig-g# ~g-form]
        (fn ~params
          (let [g# (fn [& ignore#] (orig-g# ~@params))
                f# (~@f-form g#)]
            (f# ~@param-mods))))))

(defmacro <<-
  "Performs ->> in reverse: Inserts the last element as the last item in the
  second last form, making a list of it if it is not a list already. If there
  are more forms, inserts the second last form as the last item in the third
  last form, etc."
  [& forms]
  `(->> ~@(reverse forms)))

(defn right
  "Returns the second argument."
  [a b] b)

(defn left
  "Returns the first argument."
  [a b] a)
