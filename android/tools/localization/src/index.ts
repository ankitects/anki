/**
 * @author
 * AnkiDroid Open Source Team
 *
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 *
 * @description
 * For calling functions defined in other files.
 * upload
 *  upload English language source files to crowdin
 *
 * download
 *  build and download target language files from crowdin
 *
 * extract
 *  extract ankidroid.zip to temp_dir
 *
 * update
 *  copy latest file from temp_dir to AnkiDroid/src/main/res/values/ dir
 */

import { uploadI18nFiles } from "./upload";
import { buildAndDownload, extractZip } from "./download";
import { updateI18nFiles } from "./update";
import { TEMP_DIR } from "./constants";

process.argv.forEach(function (value) {
    switch (value) {
        case "upload":
            console.log("uploading source strings to crowdin...");
            uploadI18nFiles();
            break;

        case "download":
            console.log(
                "requesting fresh translation build and downloading from crowdin...",
            );
            buildAndDownload();
            break;

        case "extract":
            console.log(
                "extracting downloaded translation bundle to temporary directory...",
            );
            extractZip("ankidroid.zip", TEMP_DIR);
            break;

        case "update":
            console.log(
                "Copying downloaded translations to android resources directories...",
            );
            updateI18nFiles();
            break;
    }
});
