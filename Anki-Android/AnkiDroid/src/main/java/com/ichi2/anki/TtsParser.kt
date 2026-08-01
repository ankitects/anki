//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.template.TemplateFilters
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.ArrayList

/**
 * Parse card sides, extracting text snippets that should be read using a text-to-speech engine.
 */
object TtsParser {
    /**
     * Returns the list of text snippets contained in the given HTML fragment that should be read
     * using the Android text-to-speech engine, together with the languages they are in.
     *
     *
     * Each returned LocalisedText object contains the text extracted from a `<tts>` element
     * whose 'service' attribute is set to 'android', and the localeCode taken from the 'voice'
     * attribute of that element. This holds unless the HTML fragment contains no such `<tts>`
     * elements; in that case the function returns a single LocalisedText object containing the
     * text extracted from the whole HTML fragment, with the localeCode set to an empty string.
     */
    fun getTextsToRead(
        html: String,
        clozeReplacement: String,
    ): List<TTSTag> {
        val textsToRead: MutableList<TTSTag> = ArrayList()
        val elem = Jsoup.parseBodyFragment(html).body()
        parseTtsElements(elem, textsToRead, clozeReplacement)
        if (textsToRead.isEmpty()) {
            // No <tts service="android"> elements found: return the text of the whole HTML fragment
            textsToRead.add(readWholeCard(elem.text().replace(TemplateFilters.CLOZE_DELETION_REPLACEMENT, clozeReplacement)))
        }
        return textsToRead
    }

    private fun parseTtsElements(
        element: Element,
        textsToRead: MutableList<TTSTag>,
        clozeReplacement: String,
    ) {
        if ("tts".equals(element.tagName(), ignoreCase = true) &&
            "android".equals(element.attr("service"), ignoreCase = true)
        ) {
            textsToRead.add(
                localisedText(element.text().replace(TemplateFilters.CLOZE_DELETION_REPLACEMENT, clozeReplacement), element.attr("voice")),
            )
            return // ignore any children
        }
        for (child in element.children()) {
            parseTtsElements(child, textsToRead, clozeReplacement)
        }
    }

    /** If reading the whole card, a language cannot be determined */
    private fun readWholeCard(cardText: String) = localisedText(cardText, "")

    private fun localisedText(
        text: String,
        locale: String,
    ): TTSTag = TTSTag(text, locale, emptyList(), 1.0f, emptyList())
}
