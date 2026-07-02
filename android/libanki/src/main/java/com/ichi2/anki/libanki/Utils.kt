/*
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
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
package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.Consts.FIELD_SEPARATOR
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

// TODO switch to standalone functions and properties and remove Utils container
object Utils {
    // Used to format doubles with English's decimal separator system
    val ENGLISH_LOCALE: Locale = Locale.US

    /*
     * IDs
     * ***********************************************************************************************
     */

    /** Given a list of integers, return a string '(int1,int2,...)'.  */
    fun ids2str(ids: IntArray?): String =
        StringBuilder()
            .apply {
                append("(")
                if (ids != null) {
                    val s = ids.contentToString()
                    append(s.substring(1, s.length - 1))
                }
                append(")")
            }.toString()

    /** Given a list of integers, return a string '(int1,int2,...)'.  */
    fun ids2str(ids: LongArray?): String =
        StringBuilder()
            .apply {
                append("(")
                if (ids != null) {
                    val s = ids.contentToString()
                    append(s.substring(1, s.length - 1))
                }
                append(")")
            }.toString()

    /** Given a list of integers, return a string '(int1,int2,...)', in order given by the iterator.  */
    fun <T> ids2str(ids: Iterable<T>): String =
        StringBuilder(512)
            .apply {
                append("(")
                for ((index, id) in ids.withIndex()) {
                    if (index != 0) {
                        append(", ")
                    }
                    append(id)
                }
                append(")")
            }.toString()

    /**
     * Fields
     * ***********************************************************************************************
     */
    fun joinFields(list: Array<String>): String {
        val result = StringBuilder(128)
        for (i in 0 until list.size - 1) {
            result.append(list[i]).append("\u001f")
        }
        if (list.isNotEmpty()) {
            result.append(list[list.size - 1])
        }
        return result.toString()
    }

    // TODO ensure manual conversion is correct
    fun splitFields(fields: String): MutableList<String> {
        // -1 ensures that we don't drop empty fields at the ends
        return fields.split(FIELD_SEPARATOR).toMutableList()
    }

    /*
     * Checksums
     * ***********************************************************************************************
     */

    /**
     * SHA1 checksum.
     * Equivalent to python sha1.hexdigest()
     *
     * @param data the string to generate hash from
     * @return A string of length 40 containing the hexadecimal representation of the MD5 checksum of data.
     */
    fun checksum(data: String?): String {
        if (data == null) {
            return ""
        }
        val md: MessageDigest
        var digest: ByteArray? = null
        try {
            md = MessageDigest.getInstance("SHA1")
            digest = md.digest(data.toByteArray(charset("UTF-8")))
        } catch (e: NoSuchAlgorithmException) {
            Timber.e(e, "Utils.checksum: No such algorithm.")
            throw RuntimeException(e)
        } catch (e: UnsupportedEncodingException) {
            Timber.e(e, "Utils.checksum :: UnsupportedEncodingException")
        }
        val biginteger = BigInteger(1, digest)
        var result = biginteger.toString(16)

        // pad with zeros to length of 40 This method used to pad
        // to the length of 32. As it turns out, sha1 has a digest
        // size of 160 bits, leading to a hex digest size of 40,
        // not 32.
        if (result.length < 40) {
            val zeroes = "0000000000000000000000000000000000000000"
            result = zeroes.take(zeroes.length - result.length) + result
        }
        return result
    }
}
