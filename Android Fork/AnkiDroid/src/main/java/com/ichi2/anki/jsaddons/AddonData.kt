// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani <infinyte01@gmail.com>

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
 * @param description commonly absent from real package.json files; absence never invalidates the addon
 * @param author commonly absent from real package.json files; absence never invalidates the addon
 * @param license commonly absent from real package.json files; absence never invalidates the addon
 * @param dist only exists in the npm registry API response, not in the package.json inside a tarball;
 *   absence never invalidates the addon
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

/** Parsing contract for package.json: unknown fields must never fail parsing */
private val json = Json { ignoreUnknownKeys = true }

/**
 * Result of validating a manifest ([AddonData]) as an AnkiDroid addon
 */
sealed interface AddonValidationResult {
    /** The manifest maps to a usable [AddonModel] */
    data class Valid(
        val addonModel: AddonModel,
    ) : AddonValidationResult

    /** The manifest is not a valid AnkiDroid addon, for the reasons in [errors] */
    data class Invalid(
        val errors: List<String>,
    ) : AddonValidationResult
}

/**
 * Check if npm package is valid or not by fields ankidroidJsApi, keywords (ankidroid-js-addon) and
 * addon_type (reviewer or note editor) in addonData
 *
 * Unreadable and unparseable files are reported as [AddonValidationResult.Invalid]: a caller
 * listing an addons directory must not be aborted by one corrupt addon
 *
 * @param packageJson package.json file
 * @return [AddonValidationResult.Valid] with the mapped model,
 *   or [AddonValidationResult.Invalid] with the errors related to the failed checks
 */
fun getAddonModelFromJson(packageJson: File): AddonValidationResult =
    try {
        val data = packageJson.readBytes().decodeToString()
        getAddonModelFromAddonData(json.decodeFromString(data))
    } catch (exc: SerializationException) {
        AddonValidationResult.Invalid(listOf("Unable to parse manifest: $exc"))
    } catch (exc: IOException) {
        AddonValidationResult.Invalid(listOf("Unable to read manifest: $exc"))
    }

/**
 * Get addonModel from addonData
 *
 * @param addonData
 * @return [AddonValidationResult.Valid] with the mapped model,
 *   or [AddonValidationResult.Invalid] with the errors related to the failed checks
 */
fun getAddonModelFromAddonData(addonData: AddonData): AddonValidationResult {
    // either fields not present in package.json or failed to parse the fields
    if (addonData.name.isNullOrBlank() ||
        addonData.addonTitle.isNullOrBlank() ||
        addonData.main.isNullOrBlank() ||
        addonData.version.isNullOrBlank() ||
        addonData.ankidroidJsApi.isNullOrBlank() ||
        addonData.addonType.isNullOrBlank() ||
        addonData.homepage.isNullOrBlank() ||
        addonData.keywords.isNullOrEmpty()
    ) {
        // the checks below dereference these fields, so they cannot run safely
        return AddonValidationResult.Invalid(listOf("Invalid addon package: fields in package.json are empty or null"))
    }

    val errorList: MutableList<String> = ArrayList()

    // check if name is safe and valid
    if (!validateName(addonData.name)) {
        errorList.add("Invalid addon package: package name failed validation")
    }

    if (addonData.addonType != REVIEWER_ADDON && addonData.addonType != NOTE_EDITOR_ADDON) {
        errorList.add(
            "Invalid addon package: ${addonData.addonType} is not valid addon type, " +
                "package.json must have 'addonType' fields of 'reviewer' or 'note-editor'",
        )
    }

    // if addon type is note editor then it must have icon
    if (addonData.addonType == NOTE_EDITOR_ADDON && addonData.icon.isNullOrBlank()) {
        errorList.add("Invalid addon package: note editor addon must have 'icon' fields in package.json")
    }

    // check if ankidroid-js-addon present or not in mapped addonData
    val jsAddonKeywordsPresent = addonData.keywords.any { it == ANKIDROID_JS_ADDON_KEYWORDS }
    if (!jsAddonKeywordsPresent) {
        errorList.add("Invalid addon package: package.json does not have 'ankidroid-js-addon' in ${addonData.keywords} keywords")
    }

    // Check supplied api and current api
    if (addonData.ankidroidJsApi != CURRENT_JS_API_VERSION) {
        errorList.add(
            "Invalid addon package: supplied js api version ${addonData.ankidroidJsApi} must " +
                "be equal to current js api version $CURRENT_JS_API_VERSION",
        )
    }

    // there are errors in package.json so return the errors list
    if (errorList.isNotEmpty()) {
        return AddonValidationResult.Invalid(errorList)
    }

    val icon = if (addonData.addonType == NOTE_EDITOR_ADDON) addonData.icon!! else ""

    // return addon model, because it is validated
    val addonModel =
        AddonModel(
            name = addonData.name,
            addonTitle = addonData.addonTitle,
            icon = icon,
            version = addonData.version,
            description = addonData.description.orEmpty(),
            main = addonData.main,
            ankidroidJsApi = addonData.ankidroidJsApi,
            addonType = addonData.addonType,
            keywords = addonData.keywords,
            author = addonData.author ?: emptyMap(),
            license = addonData.license.orEmpty(),
            homepage = addonData.homepage,
            dist = addonData.dist,
        )

    return AddonValidationResult.Valid(addonModel)
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
    val addonsData = json.decodeFromString<List<AddonData>>(urlData)
    val addonsModelList = mutableListOf<AddonModel>()
    for (addon in addonsData) {
        when (val result = getAddonModelFromAddonData(addon)) {
            is AddonValidationResult.Valid -> addonsModelList.add(result.addonModel)
            is AddonValidationResult.Invalid -> {
                Timber.i("Not a valid addon for AnkiDroid, the errors for the addon:\n %s", result.errors)
                errorList.addAll(result.errors)
            }
        }
    }

    return Pair(addonsModelList, errorList.toList())
}
