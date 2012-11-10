# oiiku-mongodb

Warning: not battle proven! This is a very early preview release.

A small wrapper on top of the Monger MongoDB library, for working with MongoDB from Clojure apps.

Open sourced it after someone on the Clojure IRC channel wanted to see it. It's not currently in release quality (lack of documentation etc).

# Why it exists

It allows for the creation of "models". Essentially, this library consists of a bunch of functions that takes a collection name and some options like validation function etc, and returns a new functoin that you actually use to work with MongoDB. This allows for the cration of models. See separate section on models.

It also makes database operations completely functional, instead of using a hidden internal singleton to store it.

## Connecting

We don't use the global singleton of the underlying library. You need to create a connection and pass it to all database operations.

    (def db (oiiku-mongodb.db/create-db "name-of-database"))

It's up to you to make this connection available so that you can pass it to your queries.

We prefer a functional system that easily allows us to have multiple instances of our application in one process, for example. The default singleton of Monger makes that a bit cumbersome.

## Models

Models are modular. Here's an example of how to make a "model".

    (ns myapp.models.user
      (:require [oiiku-mongodb.db :as db]))
    
    (def insert (db/make-insert users" validator-fn-here))
    (def find-one (db/make/find-one "users"))

The function `make-insert` returns a new function that takes two arguments: the database to work on (see previous section) and the data to insert. It will return `[true the-data]` if it is successfully inserted, and `[false validation-errors]` if not.

TODO: Document all the functions.

See more about validators below.


## Validation

Some of the factories takes a validator. The validator is any function that takes the provided data as input. If the function returns something falsy, validation passes. If not, the data returned from the function is used as the error data.

You can use any validation library you want, such as [Validateur](http://clojurevalidations.info/) or our own [oiiku-data-validator](https://github.com/oiiku/oiiku-data-validation).
