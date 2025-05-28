// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getProfileConfigJson, setProfileConfigJson } from "@generated/backend";

export async function getProfileConfig(key: string): Promise<any> {
    const decoder = new TextDecoder();
    const json = decoder.decode((await getProfileConfigJson({ val: key })).json);
    return JSON.parse(json);
}

export async function setProfileConfig(key: string, value: any): Promise<void> {
    const encoder = new TextEncoder();
    const json = JSON.stringify(value);
    await setProfileConfigJson({ key: key, valueJson: encoder.encode(json) });
}
