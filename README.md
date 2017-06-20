# lein-localrepo

Leiningen plugin to work with local Maven repository.


## Installation

### Lein 2 users

The recommended way is to install as a global plugin in `~/.lein/profiles.clj`
(for Windows users `%USERPROFILE%\.lein\profiles.clj`):

    {:user {:plugins [[lein-localrepo "0.5.4"]]}}

You may also install as a project plugin in `project.clj`:

    :plugins [[lein-localrepo "0.5.4"]]


### Lein 1.x users

Either install as a plugin:

    $ lein plugin install lein-localrepo "0.3"

Or, include as a dev-dependency:

    :dev-dependencies [lein-localrepo "0.3"]


## Usage

### Guess Leiningen (Maven) coordinates of a file

    $ lein localrepo coords <filename>

Example:

    $ lein localrepo coords foo-bar-1.0.6.jar

Output:

    foo-bar-1.0.6.jar foo-bar/foo-bar 1.0.6


### Install artifacts to local Maven repository

    $ lein localrepo install [-r repo-path] [-p pom-file] <filename> <[groupId/]artifactId> <version>

If no POM file is specified, a minimal POM will be automatically generated.

Examples:

    $ lein localrepo install foo-1.0.6.jar com.example/foo 1.0.6
    $ lein localrepo install foomatic-1.3.9.jar foomatic 1.3.9
    $ lein localrepo coords /tmp/foobar-1.0.0-SNAPSHOT.jar | xargs lein localrepo install


### List artifacts in local Maven repository:

    $ lein localrepo list [-r repo-path] [-s | -f | -d]

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

Note:
As an alternative while this feature is being implemented, removing artifacts is composed of two steps:
First, find the path to the artifact with `lein classpath | tr ":" "\n" | grep m2.*<YOUR ARTIFACT ID HERE>`.
Second, delete the directory of that artifact from the group ID or the root of that artifact.


## Getting in touch

On Twitter: [@kumarshantanu](http://twitter.com/kumarshantanu)

On Leiningen mailing list: [http://groups.google.com/group/leiningen](http://groups.google.com/group/leiningen)


## License

Copyright (C) 2011-2017 Shantanu Kumar

Distributed under the Eclipse Public License, the same as Clojure.
