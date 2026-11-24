// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.utils.ext

import com.intellij.psi.PsiField
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.toUElementOfType

/**
 * Resolves a Kotlin property to its backing field, including access through its getter.
 *
 * [UReferenceExpression.resolve] can return a getter method for an ordinary Kotlin `val`.
 *
 * This unwraps it, so it works the same as a `const val`.
 */
internal fun UExpression.resolveToField(): PsiField? {
    val reference = (this as? UQualifiedReferenceExpression)?.selector ?: this
    val resolved = (reference as? UReferenceExpression)?.resolve() ?: return null
    val property = resolved.unwrapped as? KtProperty ?: return null
    return property.toUElementOfType<UField>()?.javaPsi as? PsiField
}
