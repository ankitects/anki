// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>

package com.ichi2.anki.utils

/**
 * Detects Han Unification issues in note field content.
 *
 * Han Unification is a Unicode feature where visually distinct Chinese, Japanese, and Korean
 * characters are assigned the same code point. This can cause Japanese kanji to render as
 * Chinese hanzi (or vice versa) depending on the system font, unless the HTML explicitly
 * specifies the language via the `lang` attribute.
 *
 * Example: U+9AA8 (骨) renders differently in Japanese vs Chinese without lang="ja" or lang="zh"
 *
 * This detector is used during note creation/editing to warn users about potential rendering
 * issues before they encounter them during review.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Han_unification">Han Unification on Wikipedia</a>
 */

object HanUnificationDetector {
    /**
     * Common CJK characters known to have significant visual differences between
     * Japanese and Chinese rendering. This is a curated list for detection for now.
     *
     * Source: https://github.com/ankidroid/Anki-Android/issues/19431
     */
    private val KNOWN_PROBLEMATIC_CHARS =
        setOf(
            '\u9AA8', // 骨
            '\u76F4', // 直
            '\u76F8', // 相
            '\u79C1', // 私
            '\u8FBC', // 込
            '\u904E', // 過
            '\u98DF', // 食
            '\u6700', // 最
            '\u5408', // 合
            '\u5009', // 倉
            '\u5C64', // 層
            '\u66FE', // 曾
            '\u671D', // 朝
            '\u6CBB', // 治
            '\u7384', // 玄
            '\u7DF4', // 練
            '\u7F72', // 署
            '\u89D2', // 角
            '\u8AAA', // 說
            '\u8D08', // 贈
            '\u9038', // 逸
            '\u9244', // 鉄
            '\u96C6', // 集
        )

    /**
     * Detects potential Han Unification issues in text content.
     *
     * Checks if the text contains known CJK characters that render differently
     * in Japanese vs Chinese contexts without proper language specification.
     *
     * @param text The text content to analyze (plain text, not HTML)
     * @return true if any problematic characters are found, false otherwise
     */
    fun detectIssue(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }

        // Early exit on first problematic character found
        return text.any { char -> KNOWN_PROBLEMATIC_CHARS.contains(char) }
    }
}
