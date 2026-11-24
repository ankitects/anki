# buildSrc

[Docs: Sharing Build Logic using `buildSrc`](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html)

A Gradle-reserved directory for build-logic Kotlin code that needs to live
outside the `.gradle.kts` build scripts.

## Uses

* Extracting reusable build logic.
  * NOTE: The overhead for one-off scripts is not worthwhile
* Extracting build logic to support the [Gradle Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
  * Closures inside `.kts` files can capture an implicit `this` reference, which is CC-incompatible.
* Defining a convention plugin to reduce duplication in `.kts` files

## Debug build coverage

To disable JaCoCo instrumentation for debug builds across checkouts and worktrees, add
`enable_coverage=false` to `~/.gradle/gradle.properties`.

## Unit test forks

Each test fork is a JVM with a 2 GiB heap. To change how many run at once, pass
`-PtestMaxParallelForks=<n>` or add `testMaxParallelForks=<n>` to `~/.gradle/gradle.properties`.

## Backend test libraries

`rsdroid-testing` keeps native libraries in the test JVM's temporary directory for reuse
across classloaders and test runs. After unit tests run, `BackendTempLibraryService`
warns if those files total more than 1 GiB. Use `-PbackendTempLibraryWarnBytes=0` to
show the report for any nonempty libraries. Cached and up-to-date test tasks do not report.

The report lists the library files and their sizes. Stop tests in all checkouts and IDEs
before manually removing them; leave the backend's `.lock` files in place. Subsequent
tests extract any libraries they need again. Gradle `clean` does not remove these files.
