/**
 * @author
 * AnkiDroid Open Source Team
 *
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 *
 * @description
 * uploadI18nFiles() to upload current version of English strings from AnkiDroid/src/main/res/values/ dir to crowdin.
 * It's expected to be called through yarn start upload
 */

import fs from "fs";
import crowdin, { ResponseList, SourceFilesModel } from "@crowdin/crowdin-api-client";
import {
    PROJECT_ID,
    credentialsConst,
    I18N_FILES,
    I18N_FILES_DIR,
    MARKET_DESC_FILE,
} from "./constants";

// initialization of crowdin client
const { uploadStorageApi, sourceFilesApi } = new crowdin(credentialsConst);

/**
 * Upload English source files to Crowdin
 */
export async function uploadI18nFiles() {
    const files = await sourceFilesApi.listProjectFiles(PROJECT_ID);

    try {
        for (const file of I18N_FILES) {
            let I18N_FILE_TARGET_NAME = `${file}.xml`;
            let I18N_FILE_SOURCE_NAME = `${I18N_FILES_DIR}${I18N_FILE_TARGET_NAME}`;

            // special case, the market description is a txt file from different location
            if (file == "14-marketdescription") {
                I18N_FILE_TARGET_NAME = "14-marketdescription.txt";
                I18N_FILE_SOURCE_NAME = MARKET_DESC_FILE;
            }

            if (fs.existsSync(I18N_FILE_SOURCE_NAME)) {
                const data = fs.readFileSync(I18N_FILE_SOURCE_NAME, {
                    encoding: "utf-8",
                });
                if (data) {
                    // if exists then update, else create new file
                    const id = idOfFileOrNull(I18N_FILE_TARGET_NAME, files);
                    if (id != null) {
                        console.log(
                            `Update of Main File ${I18N_FILE_TARGET_NAME} from ${I18N_FILE_SOURCE_NAME}`,
                        );
                        updateFile(id, I18N_FILE_TARGET_NAME, data);
                    } else {
                        console.log(
                            `Create of Main File ${I18N_FILE_TARGET_NAME} from ${I18N_FILE_SOURCE_NAME}`,
                        );
                        createFile(I18N_FILE_TARGET_NAME, data);
                    }
                }
            } else {
                throw `File not exist ${I18N_FILE_SOURCE_NAME}`;
            }
        }
    } catch (error) {
        console.error(error);
    }
}

/**
 * Allow to upload a new file for the first time on crowdin
 *
 * @param fileName name of the file
 * @param fileContent file conten
 */
async function createFile(fileName: string, fileContent: string) {
    const storage = await uploadStorageApi.addStorage(fileName, fileContent);
    const file = await sourceFilesApi.createFile(PROJECT_ID, {
        name: fileName,
        title: fileName,
        storageId: storage.data.id,
        type: "auto",
    });
    console.log(file, storage.data.id);
}

/**
 * Update file with txt, xml content to translate
 *
 * @param id storage id (on Crowdin) for the file
 * @param fileName name of the file
 * @param fileContent file contents
 */
async function updateFile(id: number, fileName: string, fileContent: string) {
    const storage = await uploadStorageApi.addStorage(fileName, fileContent);
    const file = await sourceFilesApi.updateOrRestoreFile(PROJECT_ID, id, {
        storageId: storage.data.id,
    });
    console.log(file, storage.data.id);
}

/**
 * check if file exists on crowdin, if exist then return id
 *
 * @param fileName name of the file
 * @param files list of files for current project on Crowdin with ids, names ...
 * @returns id if filename stored on Crowdin else null
 */
function idOfFileOrNull(fileName: string, files: ResponseList<SourceFilesModel.File>) {
    for (const file of files.data) {
        if (file.data.name === fileName) {
            return file.data.id;
        }
    }
    return null;
}
