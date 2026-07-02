// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.internal.os.OperatingSystem
import java.io.File

/**
 * Prints the location of a generated HTML report
 *
 * @see open
 */
object ReportOpener {
    /**
     * Prints the location of a generated HTML report and, if [openRequested] is true,
     * tries to open it in the system default browser.
     */
    @JvmStatic
    fun open(
        htmlOutDir: File,
        openRequested: Boolean,
        linuxHtmlCmd: String?,
    ) {
        val reportPath = "$htmlOutDir/index.html"
        println("HTML Report: file://$reportPath")

        if (!openRequested) {
            println("to open the report automatically in your default browser add '-Popen-report' cli argument")
            return
        }

        val os = OperatingSystem.current()
        when {
            os.isWindows -> ProcessBuilder("cmd", "/c", "start $reportPath").start()
            os.isMacOsX -> ProcessBuilder("open", reportPath).start()
            os.isLinux ->
                try {
                    ProcessBuilder("xdg-open", reportPath).start()
                } catch (ignored: Exception) {
                    if (!linuxHtmlCmd.isNullOrEmpty()) {
                        ProcessBuilder(linuxHtmlCmd, reportPath).start()
                    } else {
                        println("'linux-html-cmd' property could not be found in 'local.properties'")
                    }
                }
        }
    }
}
