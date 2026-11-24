// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.annotations

/**
 * Marks repository code outside the standard packages for unit-test coverage.
 *
 * Annotate a class, or use `@file:CoverageOptIn` for a file containing top-level functions.
 * Add the class name and its nested-class pattern (`ClassName$*`) to `gradle/jacoco-includes.txt`.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class CoverageOptIn
