/*
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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

package com.ichi2.anki.web
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.utils.VersionUtils.pkgVersionName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

const val CONN_TIMEOUT = 30000

/**
 * Helper class for downloads
 *
 * Used for Addon downloads
 */
object HttpFetcher {
    /**
     * Get an OkHttpClient configured with correct timeouts and headers
     *
     * @param fakeUserAgent true if we should issue "fake" User-Agent header 'Mozilla/5.0' for compatibility
     * @return OkHttpClient.Builder ready for use or further configuration
     */
    fun getOkHttpBuilder(fakeUserAgent: Boolean): OkHttpClient.Builder {
        val clientBuilder = OkHttpClient.Builder()
        clientBuilder
            .connectTimeout(CONN_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(CONN_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(CONN_TIMEOUT.toLong(), TimeUnit.SECONDS)
        if (fakeUserAgent) {
            clientBuilder.addNetworkInterceptor(
                Interceptor { chain: Interceptor.Chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Referer", "com.ichi2.anki")
                            .header("User-Agent", "Mozilla/5.0 ( compatible ) ")
                            .header("Accept", "*/*")
                            .build(),
                    )
                },
            )
        } else {
            clientBuilder.addNetworkInterceptor(
                Interceptor { chain: Interceptor.Chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("User-Agent", "AnkiDroid-$pkgVersionName")
                            .build(),
                    )
                },
            )
        }
        return clientBuilder
    }

    fun fetchThroughHttp(
        address: String?,
        encoding: String? = "utf-8",
    ): String {
        Timber.d("fetching %s", address)
        return try {
            val requestBuilder = Request.Builder()
            requestBuilder.url(address!!).get()
            val httpGet: Request = requestBuilder.build()
            val client: OkHttpClient = getOkHttpBuilder(true).build()
            client.newCall(httpGet).execute().use { response ->
                if (response.code != 200) {
                    Timber.d("Response code was %s, returning failure", response.code)
                    return "FAILED"
                }
                val reader =
                    BufferedReader(
                        InputStreamReader(
                            response.body.byteStream(),
                            Charset.forName(encoding),
                        ),
                    )

                val stringBuilder = StringBuilder()
                var line: String?
                @KotlinCleanup("it's strange")
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
                stringBuilder.toString()
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed with an exception")
            "FAILED with exception: " + e.message
        }
    }
}
