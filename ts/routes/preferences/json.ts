// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getConfigJson, setConfigJson } from "@generated/backend";

const encoder = new TextEncoder();
const decoder = new TextDecoder();

export async function getConfigJsonObject(key: string) {
    const resp = await getConfigJson({ val: key });
    try {
        const json_string = decoder.decode(resp.json);
        if (json_string.length > 0) {
            return JSON.parse(json_string);
        } else {
            return {};
        }
    } catch (e) {
        console.error("Resetting experiment config due to error: ", e);
        return {};
    }
}

export async function setConfigJsonObject(key: string, value: any, undoable = false) {
    value = JSON.stringify(value);
    return await setConfigJson({ key, valueJson: encoder.encode(value), undoable });
}
