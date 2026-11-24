// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>

package com.ichi2.anki.backend

import androidx.core.text.HtmlCompat
import java.util.regex.Matcher
import java.util.regex.Pattern

// Regex pattern used in removing tags from text before diff
private val commentPattern = Pattern.compile("(?s)<!--.*?-->")
private val stylePattern = Pattern.compile("(?si)<style.*?>.*?</style>")
private val scriptPattern = Pattern.compile("(?si)<script.*?>.*?</script>")
private val tagPattern = Pattern.compile("(?s)<.*?>")
private val typePattern = Pattern.compile("(?s)\\[\\[type:.+?]]")
private val avRefPattern = Pattern.compile("(?s)\\[anki:play:.:\\d+?]")
private val htmlEntitiesPattern = Pattern.compile("&#?\\w+;")

/**
 * Strip special fields like `[[type:...]]` and `[anki:play...]` from a string.
 * @param input The text to be cleaned.
 * @return The text without special fields.
 */
fun stripSpecialFields(input: String): String {
    val s = typePattern.matcher(input).replaceAll("")
    return avRefPattern.matcher(s).replaceAll("")
}

/**
 * Strip HTML and special fields from a string.
 * @param input The text to be cleaned.
 * @return The text without HTML and special fields.
 */
fun stripHTMLAndSpecialFields(input: String): String {
    val s = stripHTML(input)
    return stripSpecialFields(s)
}

/**
 * Strips a text from <style>...</style>, <script>...</script> and <_any_tag_> HTML tags.
 * @param inputParam The HTML text to be cleaned.
 * @return The text without the aforementioned tags.
 */
fun stripHTML(inputParam: String): String {
    var s = commentPattern.matcher(inputParam).replaceAll("")
    s = stripHTMLScriptAndStyleTags(s)
    s = tagPattern.matcher(s).replaceAll("")
    return entsToTxt(s)
}

/**
 * Strips <style>...</style> and <script>...</script> HTML tags and content from a string.
 * @param inputParam The HTML text to be cleaned.
 * @return The text without the aforementioned tags.
 */
fun stripHTMLScriptAndStyleTags(inputParam: String): String {
    var htmlMatcher = stylePattern.matcher(inputParam)
    val s = htmlMatcher.replaceAll("")
    htmlMatcher = scriptPattern.matcher(s)
    return htmlMatcher.replaceAll("")
}

/**
 * Takes a string and replaces all the HTML symbols in it with their unescaped representation.
 * This should only affect substrings of the form `&something;` and not tags.
 * Internet rumour says that Html.fromHtml() doesn't cover all cases, but it doesn't get less
 * vague than that.
 * @param htmlInput The HTML escaped text
 * @return The text with its HTML entities unescaped.
 */
private fun entsToTxt(htmlInput: String): String {
    // entitydefs defines nbsp as \xa0 instead of a standard space, so we
    // replace it first
    val html = htmlInput.replace("&nbsp;", " ")
    val htmlEntities = htmlEntitiesPattern.matcher(html)
    val sb = StringBuffer()
    while (htmlEntities.find()) {
        val spanned =
            HtmlCompat.fromHtml(htmlEntities.group(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        val replacement = Matcher.quoteReplacement(spanned.toString())
        htmlEntities.appendReplacement(sb, replacement)
    }
    htmlEntities.appendTail(sb)
    return sb.toString()
}
