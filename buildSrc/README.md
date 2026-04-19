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