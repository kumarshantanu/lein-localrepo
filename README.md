# lein-localrepo

Leiningen plugin to work with local Maven repository.


## Usage

Either install as a plugin:

    $ lein plugin install lein-localrepo "0.2"

Or, include as a dev-dependency:

    :dev-dependencies [lein-localrepo "0.2"]


### Guess Leiningen (Maven) coordinates of a file

    $ lein localrepo coords <filename>

Example:

    $ lein localrepo coords foo-bar-1.0.6.jar

Output:

    foo-bar-1.0.6.jar foo-bar/foo-bar 1.0.6


### Install artifacts to local Maven repository

    $ lein localrepo install <filename> <[groupId/]artifactId> <version>

Examples:

    $ lein localrepo install foo-1.0.6.jar com.example/foo 1.0.6
    $ lein localrepo install foomatic-1.3.9.jar foomatic 1.3.9
    $ lein localrepo coords /tmp/foobar-1.0.0-SNAPSHOT.jar | xargs lein localrepo install


### List artifacts in local Maven repository ('-d' Not Yet Implemented):

    $ lein localrepo list [-s | -f | -d]

Examples:

    $ lein localrepo list       # lists all artifacts, all versions
    $ lein localrepo list -s    # lists all artifacts with description
    $ lein localrepo list -f    # lists all artifacts and filenames
    $ lein localrepo list -d    # lists all artifacts with detail


### Remove artifacts from local Maven repository (Not Yet Implemented):

    $ lein localrepo remove <[groupId/]artifactId> [<version>]

Examples:

    $ lein localrepo remove com.example/foo        # removes all versions
    $ lein localrepo remove foomatic               # removes all versions
    $ lein localrepo remove com.example/foo 1.0.3  # removes only specified version


## Getting in touch

On Twitter: [@kumarshantanu](http://twitter.com/kumarshantanu)

On Leiningen mailing list: [http://groups.google.com/group/leiningen](http://groups.google.com/group/leiningen)


## License

Copyright (C) 2011 Shantanu Kumar

Distributed under the Eclipse Public License, the same as Clojure.
