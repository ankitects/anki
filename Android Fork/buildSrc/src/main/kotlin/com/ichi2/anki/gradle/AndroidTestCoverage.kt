// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import groovy.util.Node
import groovy.xml.XmlParser
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.io.FileNotFoundException

/** Rejects missing reports and emulator runs that produced no covered code. */
fun assertNonzeroAndroidTestCoverage(
    jacocoReport: File,
    logger: Logger,
) {
    if (!jacocoReport.exists()) {
        throw FileNotFoundException("jacocoAndroidTestReport.xml was not found after running jacocoAndroidTestReport")
    }

    val xmlParser = XmlParser()
    xmlParser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
    xmlParser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

    val reportRoot: Node = xmlParser.parse(jacocoReport)
    var hasCovered = false

    // https://github.com/jacoco/jacoco/blob/5aabb2eb60bbcd05df968005f1746ba19dcd5361/org.jacoco.report/src/org/jacoco/report/internal/xml/ReportElement.java#L190
    for (rawChild in reportRoot.children()) {
        val child = rawChild as Node
        if (child.name() != "counter") continue

        if (child.attribute("covered") == "0") {
            logger.warn(
                "jacoco registered zero code coverage for counter type ${child.attribute("type")}",
                null as Throwable?,
            )
        } else {
            hasCovered = true
        }
    }

    if (!hasCovered) {
        throw GradleException(
            "androidTest registered zero code coverage in jacocoAndroidTestReport.xml. Probably some incompatibilities in the toolchain.",
        )
    }
}
