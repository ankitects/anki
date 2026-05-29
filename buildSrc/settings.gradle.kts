// SPDX-License-Identifier: GPL-3.0-or-later

// Expose the root project's version catalog so buildSrc can reference
// `libs.plugins.*` / `libs.versions.*` the same way the rest of the build does.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
