/*
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>
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

package com.ichi2.anki.jsaddons

import com.ichi2.anki.AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION
import com.ichi2.anki.jsaddons.AddonsConst.ANKIDROID_JS_ADDON_KEYWORDS
import com.ichi2.anki.jsaddons.AddonsConst.NOTE_EDITOR_ADDON
import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import com.ichi2.anki.jsaddons.NpmUtils.validateName
import com.ichi2.anki.web.HttpFetcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URL
import kotlin.jvm.Throws

/**
 * When package.json fetched from https://registry.npmjs.org/some-addon/latest,
 * all the required fields in package.json mapped to AddonModel in this class.
 * The most important fields in package.json are
 * ankiDroidJsApi, addonType and keywords, these fields distinguish other npm packages
 *
 * @param name name of npm package, it unique for each package listed on npm
 * @param addonTitle  for showing in AnkiDroid
 * @param icon only required for note editor (single character recommended)
 */

@Serializable
class AddonData(
    val name: String? = null,
    val addonTitle: String? = null,
    val icon: String? = null,
    val version: String? = null,
    val description: String? = null,
    val main: String? = null,
    val ankidroidJsApi: String? = null,
    val addonType: String? = null,
    val keywords: List<String>? = null,
    val author: Map<String, String>? = null,
    val license: String? = null,
    val homepage: String? = null,
    val dist: DistInfo? = null,
)

@Serializable
data class DistInfo(
    val tarball: String,
)

/**
 * Check if npm package is valid or not by fields ankidroidJsApi, keywords (ankidroid-js-addon) and
 * addon_type (reviewer or note editor) in addonData
 *
 * For valid addon the error list will be empty,
 * for not valid addon the error list will contain the error related to the checks
 *
 * @param packageJsonPath package.json file path
 * @return Pair with addonModel and error list
 */
@Throws(IOException::class)
fun getAddonModelFromJson(packageJsonPath: String): Pair<AddonModel?, List<String>> {
    val data = File(packageJsonPath).readBytes().decodeToString()
    return try {
        val json = Json { ignoreUnknownKeys = true }
        getAddonModelFromAddonData(json.decodeFromString(data))
    } catch (exc: SerializationException) {
        return Pair(null, listOf("Unable to parse manifest: $exc"))
    }
}

/**
 * Get addonModel from addonData
 *
 * @param addonData
 * @return pair of valid addon model and errors list
 */
fun getAddonModelFromAddonData(addonData: AddonData): Pair<AddonModel?, List<String>> {
    var errorStr: String
    val errorList: MutableList<String> = ArrayList()

    // either fields not present in package.json or failed to parse the fields
    if (addonData.name.isNullOrBlank() ||
        addonData.addonTitle.isNullOrBlank() ||
        addonData.main.isNullOrBlank() ||
        addonData.ankidroidJsApi.isNullOrBlank() ||
        addonData.addonType.isNullOrBlank() ||
        addonData.homepage.isNullOrBlank() ||
        addonData.keywords.isNullOrEmpty()
    ) {
        errorStr = "Invalid addon package: fields in package.json are empty or null"
        errorList.add(errorStr)
    }

    // check if name is safe and valid
    if (!validateName(addonData.name!!)) {
        errorStr = "Invalid addon package: package name failed validation"
        errorList.add(errorStr)
    }

    if (addonData.addonType != REVIEWER_ADDON && addonData.addonType != NOTE_EDITOR_ADDON) {
        errorStr = "Invalid addon package: ${addonData.addonType} is not valid addon type, " +
            "package.json must have 'addonType' fields of 'reviewer' or 'note-editor'"
        errorList.add(errorStr)
    }

    // if addon type is note editor then it must have icon
    if (addonData.addonType == NOTE_EDITOR_ADDON && addonData.icon.isNullOrBlank()) {
        errorStr = "Invalid addon package: note editor addon must have 'icon' fields in package.json"
        errorList.add(errorStr)
    }

    // check if ankidroid-js-addon present or not in mapped addonData
    val jsAddonKeywordsPresent = addonData.keywords?.any { it == ANKIDROID_JS_ADDON_KEYWORDS }
    if (!jsAddonKeywordsPresent!!) {
        errorStr = "Invalid addon package: package.json does not have 'ankidroid-js-addon' in ${addonData.keywords} keywords"
        errorList.add(errorStr)
    }

    // Check supplied api and current api
    if (addonData.ankidroidJsApi != CURRENT_JS_API_VERSION) {
        errorStr = "Invalid addon package: supplied js api version ${addonData.ankidroidJsApi} must " +
            "be equal to current js api version $CURRENT_JS_API_VERSION"
        errorList.add(errorStr)
    }

    val immutableList: List<String> = ArrayList(errorList)

    // there are errors in package.json so return null and errors list
    if (errorList.isNotEmpty()) {
        return Pair(null, immutableList)
    }

    val icon = if (addonData.addonType == NOTE_EDITOR_ADDON) addonData.icon!! else ""

    // return addon model, because it is validated
    val addonModel =
        AddonModel(
            name = addonData.name,
            addonTitle = addonData.addonTitle!!,
            icon = icon,
            version = addonData.version!!,
            description = addonData.description!!,
            main = addonData.main!!,
            ankidroidJsApi = addonData.ankidroidJsApi!!,
            addonType = addonData.addonType!!,
            keywords = addonData.keywords,
            author = addonData.author!!,
            license = addonData.license!!,
            homepage = addonData.homepage!!,
            dist = addonData.dist!!,
        )

    return Pair(addonModel, immutableList)
}

/**
 * Get list of AddonModel from json containing arrays of addons package info from network
 *
 * @param packageJsonUrl package json url containing arrays of addon packages info
 * @return Pair with list of valid addonModel and error list
 */
@Throws(IOException::class)
fun getAddonModelListFromJson(packageJsonUrl: URL): Pair<List<AddonModel>, List<String>> {
    val urlData =
        if (packageJsonUrl.protocol == "file") {
            File(packageJsonUrl.path).readBytes().decodeToString()
        } else {
            HttpFetcher.fetchThroughHttp(packageJsonUrl.toString())
        }
    val errorList: MutableList<String> = ArrayList()
    val json = Json { ignoreUnknownKeys = true }
    val addonsData = json.decodeFromString<List<AddonData>>(urlData)
    val addonsModelList = mutableListOf<AddonModel>()
    for (addon in addonsData) {
        val result = getAddonModelFromAddonData(addon)

        if (result.first == null) {
            Timber.i("Not a valid addon for AnkiDroid, the errors for the addon:\n %s", result.second)
            errorList.addAll(result.second)
            continue
        }
        addonsModelList.add(result.first!!)
    }

    return Pair(addonsModelList, errorList.toList())
}
