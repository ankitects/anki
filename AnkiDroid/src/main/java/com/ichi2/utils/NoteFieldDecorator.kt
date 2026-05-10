/*
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("SpellCheckingInspection")

package com.ichi2.utils

import java.util.Random

object NoteFieldDecorator {
    private val random = Random()
    private val huevoDecorations =
        arrayOf(
            "\uD83D\uDC8C",
            "\uD83D\uDE3B",
            "\uD83D\uDC96",
            "\uD83D\uDC97",
            "\uD83D\uDC93",
            "\uD83D\uDC9E",
            "\uD83D\uDC95",
            "\uD83D\uDC9F",
            "\uD83D\uDCAF",
            "\uD83D\uDE03",
            "\uD83D\uDE0D",
        )
    private val huevoOpciones =
        arrayOf(
            "qnr",
            "gvzenr",
            "aboantb",
            "avpbynf-enbhy",
            "Neguhe-Zvypuvbe",
            "zvxruneql",
            "qnivq-nyyvfba",
            "vavwh",
            "uffz",
            "syreqn",
            "rqh-mnzben",
            "ntehraroret",
            "bfcnyu",
            "znaqer",
            "qnavry-fineq",
            "vasvalgr7",
            "Oynvfbeoynqr",
            "genfuphggre",
            "qzvgel-gvzbsrri",
            "inabfgra",
            "unacvatpuvarfr",
            "jro5atnl",
            "FuevquneTbry",
            "Nxfunl0701",
            "ivpvbhfNrtvf",
            "gurqebvqqvi",
            "OenlnaQFB",
            "pevgvpnyNL",
            "fnawnlfnetnz",
            "krabaaa4j",
            "revpyv3690",
            "Unm3-wbyg",
            "fabjgvzrtynff",
            "hfre1823",
            "MbeaUnqAbPubvpr",
        )

    fun aplicaHuevo(fieldText: String?): String? {
        val revuelto = huevoRevuelto(fieldText)
        for (huevo in huevoOpciones) {
            if (huevo.equals(revuelto, ignoreCase = true)) {
                val decoration = huevoDecorations[getRandomIndex(huevoDecorations.size)]
                return "$decoration$decoration $fieldText $decoration$decoration"
            }
        }
        return fieldText
    }

    private fun getRandomIndex(max: Int): Int = random.nextInt(max)

    private fun huevoRevuelto(huevo: String?): String? {
        if (huevo.isNullOrEmpty()) {
            return huevo
        }
        return buildString {
            for (element in huevo) {
                var revuelto = element
                when (revuelto) {
                    in 'a'..'m' -> revuelto += 13
                    in 'A'..'M' -> revuelto += 13
                    in 'n'..'z' -> revuelto -= 13
                    in 'N'..'Z' -> revuelto -= 13
                }
                append(revuelto)
            }
        }
    }
}
