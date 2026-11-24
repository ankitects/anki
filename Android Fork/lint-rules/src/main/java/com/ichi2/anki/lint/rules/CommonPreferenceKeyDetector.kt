// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_STRING
import com.android.resources.ResourceFolderType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.ichi2.anki.lint.utils.Constants
import com.intellij.psi.PsiField
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.w3c.dom.Element
import java.util.EnumSet

/** Keeps shared preference keys distinct from display strings at Kotlin call sites. */
class CommonPreferenceKeyDetector :
    ResourceXmlDetector(),
    SourceCodeScanner {
    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements() = listOf(TAG_STRING)

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        if (context.file.parentFile.name != "values") return
        // Keep the defining file's classification before dependency and test resources are merged.
        val filename = context.file.name
        val isKey = filename == "preferences.xml" || filename.startsWith("preferences_")
        context.resultMap(RESOURCES).put(element.getAttribute(ATTR_NAME), isKey)
    }

    override fun getApplicableUastTypes() = listOf(USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        val file = context.uastFile?.sourcePsi as? KtFile ?: return null
        val keyAliasNames = mutableSetOf("CommonPreferenceKey", KEY_ALIAS)
        file.importDirectives.forEach { directive ->
            if (directive.importedFqName?.asString() == KEY_ALIAS) {
                directive.aliasName?.let(keyAliasNames::add)
            }
        }
        return object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val field = node.resolve() as? PsiField ?: return
                if (field.containingClass?.qualifiedName != "com.ichi2.anki.common.android.R.string") return
                if (context.driver.isSuppressed(context, ISSUE, node)) return

                val qualified = (node.uastParent as? UQualifiedReferenceExpression)?.takeIf { it.selector == node }
                val usesKeyAlias =
                    if (qualified != null) {
                        qualified.receiver.referenceName() in keyAliasNames
                    } else {
                        file.importsKeyField(node.identifier)
                    }
                val reference =
                    LintMap()
                        .put(RESOURCE_NAME, field.name)
                        .put(USES_KEY_ALIAS, usesKeyAlias)
                        .put(LOCATION, context.getLocation(qualified ?: node))
                qualified?.let { reference.put(QUALIFIER_LOCATION, context.getLocation(it.receiver)) }
                val references = context.resultMap(REFERENCES)
                references.put(references.size.toString(), reference)
            }
        }
    }

    /** Explicit field imports take precedence over wildcard imports. */
    private fun KtFile.importsKeyField(name: String): Boolean {
        val explicitImport =
            importDirectives.firstOrNull { directive ->
                !directive.isAllUnder && (directive.aliasName ?: directive.importedFqName?.shortName()?.asString()) == name
            }
        if (explicitImport != null) {
            return explicitImport.importedFqName?.parent()?.asString() == KEY_ALIAS
        }
        return importDirectives.any { it.isAllUnder && it.importedFqName?.asString() == KEY_ALIAS }
    }

    /** Preserve alias names while ignoring whitespace, comments, and identifier escaping. */
    private fun UExpression.referenceName(): String? {
        return when (this) {
            is USimpleNameReferenceExpression -> identifier
            is UQualifiedReferenceExpression -> {
                val receiverName = receiver.referenceName() ?: return null
                val selectorName = selector.referenceName() ?: return null
                "$receiverName.$selectorName"
            }
            else -> null
        }
    }

    override fun checkPartialResults(
        context: Context,
        partialResults: PartialResult,
    ) {
        val resources = partialResults.maps().mapNotNull { it.getMap(RESOURCES) }
        val knownNames = resources.flatMap { it.keys() }.toSet()
        val keyNames = resources.flatMap { map -> map.keys().filter { map.getBoolean(it) == true } }.toSet()
        for (map in partialResults.maps()) {
            val references = map.getMap(REFERENCES) ?: continue
            for (key in references) {
                val reference = references.getMap(key) ?: continue
                val name = reference.getString(RESOURCE_NAME) ?: continue
                if (name !in knownNames) continue
                if ((name in keyNames) != reference.getBoolean(USES_KEY_ALIAS)) {
                    reportReference(context, reference)
                }
            }
        }
    }

    private fun reportReference(
        context: Context,
        reference: LintMap,
    ) {
        val location = reference.getLocation(LOCATION) ?: return
        val qualifierLocation = reference.getLocation(QUALIFIER_LOCATION)
        val usesKeyAlias = reference.getBoolean(USES_KEY_ALIAS) == true
        val expectedAlias = if (usesKeyAlias) "CommonString" else "CommonPreferenceKey"
        val replacement =
            "com.ichi2.anki.$expectedAlias" +
                if (qualifierLocation == null) ".${reference.getString(RESOURCE_NAME)}" else ""
        val message =
            if (usesKeyAlias) {
                "Use CommonString for strings that are not preference keys"
            } else {
                "Use CommonPreferenceKey for shared preference keys"
            }
        context.report(
            Incident(
                ISSUE,
                location,
                message,
                fix()
                    .name("Use $expectedAlias")
                    .replace()
                    .range(qualifierLocation ?: location)
                    .with(replacement)
                    .shortenNames()
                    .build(),
            ),
        )
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }

    private fun Context.resultMap(key: String): LintMap {
        val results = getPartialResults(ISSUE).map()
        return results.getMap(key) ?: LintMap().also { results.put(key, it) }
    }

    companion object {
        private const val KEY_ALIAS = "com.ichi2.anki.CommonPreferenceKey"
        private const val RESOURCES = "resources"
        private const val REFERENCES = "references"
        private const val LOCATION = "location"
        private const val QUALIFIER_LOCATION = "qualifierLocation"
        private const val RESOURCE_NAME = "resourceName"
        private const val USES_KEY_ALIAS = "usesKeyAlias"

        val ISSUE: Issue =
            Issue.create(
                "CommonPreferenceKeyUsage",
                "Use the appropriate alias for shared string resources",
                "Shared preference keys defined in preferences.xml or preferences_*.xml should use " +
                    "CommonPreferenceKey. Other string resources should not use that alias. " +
                    "Both aliases refer to R.string, so the Kotlin compiler cannot enforce this distinction.",
                Constants.ANKI_CODE_STYLE_CATEGORY,
                Constants.ANKI_CODE_STYLE_PRIORITY,
                Constants.ANKI_CODE_STYLE_SEVERITY,
                Implementation(CommonPreferenceKeyDetector::class.java, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)),
            )
    }
}
