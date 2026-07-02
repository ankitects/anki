/**
 * @author
 * AnkiDroid Open Source Team
 *
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 *
 * @description
 * updateI18nFiles() to update files in AnkiDroid/src/main/res/values/ with downloaded files from crowdin.
 * The downloaded file needs to extract first with 'yarn start extract'.
 * It's expected to be called through 'yarn start update'.
 */

import fs from "fs";
import path from "path";
import readline from "readline";
import {
    LANGUAGES,
    LOCALIZED_REGIONS,
    TEMP_DIR,
    I18N_FILES,
    XML_LICENSE_HEADER,
    RES_VALUES_LANG_DIR,
    OLD_VER_MARKET_DESC_FILE,
    MARKET_DESC_LANG,
} from "./constants";

let anyError = false;

/**
 * Replace invalid chars in xml files in res/value dir
 * e.g. %1s$ is invalid, %1$s is valid
 *
 * @param fileName name of the file in res/value dir
 * @returns boolean true if any corrections were made, false if no corrections were needed
 */
async function replacechars(fileName: string): Promise<boolean> {
    const newfilename = fileName + ".tmp";

    const fileStream = fs.createReadStream(fileName);

    const rl = readline.createInterface({
        input: fileStream,
        crlfDelay: Infinity,
    });

    for await (let line of rl) {
        if (line.startsWith("<?xml")) {
            line = XML_LICENSE_HEADER;
        } else {
            // remove space before </item> and add new lines to after </item>
            if (line.startsWith("    <item>0 </item>")) {
                line = "    <item>0</item>\n";
            }

            // running prettier will change this line, remove back slash
            line = line.replace(/\'/g, "\\'");
            line = line.replace(/\\\\\'/g, "\\'");
            line = line.replace(/\n\s/g, "\\n");
            line = line.replace(/…/g, "&#8230;");
        }

        fs.appendFileSync(newfilename, line + "\n");
    }

    fs.rename(newfilename, fileName, function (err) {
        if (err) throw err;
        process.stdout.write(".");
    });

    return true;
}

/**
 * Get file extension for the file
 *
 * @param f filename
 * @returns extension string
 */
function fileExtFor(f: string): string {
    if (f == "14-marketdescription") return ".txt";
    else return ".xml";
}

/**
 * Create language resource directory in res/value dir
 *
 * @param directory name of the directory
 */
export function createDirIfNotExisting(directory: string) {
    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory);
    }
}

/**
 * For existing directory update xml files in res/value dir for each languages
 *
 * @param valuesDirectory res/value dir for the language
 * @param translatedContent content of target language xml file
 * @param f txt, xml file name
 * @param fileExt extension of the file
 * @param language language code
 * @returns boolean for successfully replaced invalid string and copied updated files
 */
async function update(
    valuesDirectory: string,
    translatedContent: string,
    f: string,
    fileExt: string,
    language = "",
): Promise<boolean> {
    // we want to upload this, so it's in the constants, but we don't update it after download
    if (f == "12-dont-translate") {
        return true;
    }

    // These are pulled into a special file
    if (f == "14-marketdescription") {
        const newfile = path.join(MARKET_DESC_LANG + language + fileExt);

        fs.writeFileSync(newfile, translatedContent);

        const oldContent = fs.readFileSync(OLD_VER_MARKET_DESC_FILE).toString();
        const newContent = fs.readFileSync(newfile).toString();

        for (let i = 0; i < oldContent.length; i++) {
            if (oldContent[i] != newContent[i]) {
                process.stdout.write(".");
                return true;
            }
        }

        fs.unlinkSync(newfile);
        console.log(
            "File marketdescription is not translated into language " + language,
        );
        return true;
    }

    // Everything else is a regular file to translate into Android resources
    const newfile = valuesDirectory + f + ".xml";
    fs.writeFileSync(newfile, translatedContent);
    return replacechars(newfile);
}

/**
 * Update translated I18n files in res/value dir
 */
export async function updateI18nFiles() {
    for (const language of LANGUAGES) {
        // Language tags are 2- or 3-letters, and regional files need a marker in Android where subtag starts with "r"
        // Note the documentation does not describe what works in practice:
        // https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources -
        // "The language is defined by a two-letter ISO 639-1 language code, optionally followed by a two letter ISO 3166-1-alpha-2 region code (preceded by lowercase r)."
        //
        // In practice - contrary to the official Android documentation above, 3-letter codes as defined by ISO 639-2 definitely work,
        // for example code "fil", which is not found in ISO 639-1 but is in 639-2 https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes
        //
        // It appears that Android will load the correct BCP47 (that is: 2- or 3-letter code) language even if not in a directory with
        // language code preceded by "b-". We should potentially move to formal BCP47 "b-<langtag>" values directories in the future,
        // but this comment hopefully clarifies for the reader that BCP47 / ISO-639-2 3-letter language tags do work in practice.
        //
        // The codes are not case-sensitive; the r prefix is used to distinguish the region portion. You cannot specify a region alone.
        let androidLanguages: string[];
        const languageCode = language.split("-", 1)[0];
        if (LOCALIZED_REGIONS.includes(languageCode)) {
            androidLanguages = [language.replace("-", "-r")]; // zh-CN becomes zh-rCN
        } else {
            androidLanguages = [language.split("-", 1)[0]]; // Example: es-ES becomes es
        }

        // Also update mappings for CrowdinLanguageTag in Crowdin.kt.
        switch (language) {
            case "he":
                // some Android phones use values-heb, some use values-iw - issue 9451
                // the only way for Hebrew to work on all devices is to copy into both possible locations
                androidLanguages = ["heb", "iw"];
                break;

            case "id":
                androidLanguages = ["ind"];
                break;

            case "tl":
                androidLanguages = ["tgl"];
                break;
        }

        androidLanguages.map(async (androidLanguage) => {
            console.log(
                "\nCopying language files from " + language + " to " + androidLanguage,
            );
            const valuesDirectory = path.join(
                RES_VALUES_LANG_DIR + androidLanguage + "/",
            );
            createDirIfNotExisting(valuesDirectory);

            // Copy localization files, mask chars and append gnu/gpl licence
            for (const f of I18N_FILES) {
                const fileExt = fileExtFor(f);
                const translatedContent = fs.readFileSync(
                    TEMP_DIR + "/" + language + "/" + f + fileExt,
                    "utf-8",
                );
                anyError = !(await update(
                    valuesDirectory,
                    translatedContent,
                    f,
                    fileExt,
                    language,
                ));
            }

            if (anyError) {
                console.error(
                    "At least one file of the last handled language contains an error.",
                );
                anyError = true;
            }
        });
    }
}
