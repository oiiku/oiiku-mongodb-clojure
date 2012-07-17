# oiiku-mongodb

A collection of tools for working with MongoDB from Clojure.

## Connecting

We don't use the horrible global singleton of the underlying library. You need to create a connection and pass it to all database operations.

    (def connection (oiiku-mongodb.db/create-connection "name-of-database"))

It's up to you to make this connection available so that you can pass it to your queries.


## Models

Models are modular. Here's an example of how to make a "model".

    (ns myapp.models.user
      (:require [oiiku-mongodb.db :as db]))
    
    (def insert (db/make-insert users" validator-fn-here))
    (def find-one (db/make/find-one "users"))

A validator function is a function that returns nil, or an object that looks like this:

    {:attrs {"some-attr" ["needs sauce" "can't be blank"]} :base ["Quota exceeded"]}

See separate section for an intro on how to easily create such validator functions.

Note: Currently does not support separate validations for create and update. We'll probably need that so it'll probably be added once we implement some code that requires this feature.


## Validation

A toolkit for mixing smaller validation functions into an actual model validation function exists.

    (require [oiiku-mongodb.validator :as v])
    
    (def validator
      (v/make-validator
        (v/validator-required "username")
        (v/validator-required password")
        (fn [data]
          ["some-attribute" "some error message"])
        (fn [data]
          "This error message is for the whole model, not a specific attr.")))
