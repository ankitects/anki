// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getMetaJson, getProfileConfigJson, setMetaJson, setProfileConfigJson } from "@generated/backend";

async function getSettingJson(key: string, backendGetter: (key: string) => Promise<any>): Promise<any> {
    const decoder = new TextDecoder();
    const json = decoder.decode((await backendGetter(key)).json);
    return JSON.parse(json);
}

async function setSettingJson(
    key: string,
    value: any,
    backendSetter: (key: string, value: any) => Promise<any>,
): Promise<void> {
    const encoder = new TextEncoder();
    const json = JSON.stringify(value);
    await backendSetter(key, encoder.encode(json));
}

export async function getProfileConfig(key: string): Promise<any> {
    return getSettingJson(key, async (k) => await getProfileConfigJson({ val: k }));
}

export async function setProfileConfig(key: string, value: any): Promise<void> {
    return await setSettingJson(key, value, async (k, v) => await setProfileConfigJson({ key: k, valueJson: v }));
}

export async function getMeta(key: string): Promise<any> {
    return getSettingJson(key, async (k) => await getMetaJson({ val: k }));
}

export async function setMeta(key: string, value: any): Promise<void> {
    return await setSettingJson(key, value, async (k, v) => await setMetaJson({ key: k, valueJson: v }));
}
