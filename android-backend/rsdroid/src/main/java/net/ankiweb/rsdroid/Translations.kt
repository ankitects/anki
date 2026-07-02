package net.ankiweb.rsdroid

import anki.i18n.GeneratedTranslations
import anki.i18n.TranslateArgMap
import anki.i18n.TranslateStringRequest
import kotlin.Int
import kotlin.String
import anki.generic.String as GenericString

// strip off unicode isolation markers from a translated string
// for testing purposes
fun String.withoutUnicodeIsolation(): String = this.replace("\u2068", "").replace("\u2069", "")

class Translations(
    private val backend: Backend,
) : GeneratedTranslations {
    override fun translate(
        module: Int,
        translation: Int,
        args: TranslateArgMap,
    ): String {
        val request =
            TranslateStringRequest
                .newBuilder()
                .putAllArgs(args)
                .setModuleIndex(module)
                .setMessageIndex(translation)
                .build()
        val output = backend.translateStringRaw(request.toByteArray())
        return GenericString.parseFrom(output).`val`
    }
}
