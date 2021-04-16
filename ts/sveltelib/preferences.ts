// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Writable, writable, get } from "svelte/store";

export interface CustomStore<T> extends Writable<T> {
    subscribe: (getter: (value: T) => void) => () => void;
    set: (value: T) => void;
}

export type PreferenceStore<T> = {
    [K in keyof Omit<T, "toJSON">]: CustomStore<T[K]>;
};

export type PreferencePayload<T> = {
    [K in keyof Omit<T, "toJSON">]: T[K];
};

export type PreferenceRaw<T> = {
    [K in keyof T]: T[K];
};

function createPreference<T>(
    initialValue: T,
    savePreferences: () => void
): CustomStore<T> {
    const { subscribe, set, update } = writable(initialValue);

    return {
        subscribe,
        set: (value: T): void => {
            set(value);
            savePreferences();
        },
        update: (updater: (value: T) => T): void => {
            update(updater);
            savePreferences();
        },
    };
}

function preparePreferences<T>(
    Preferences: T,
    setter: (payload: PreferencePayload<T>) => Promise<void>,
    toObject: (preferences: T, options: { defaults: boolean }) => PreferenceRaw<T>
): PreferenceStore<T> {
    const preferences: Partial<PreferenceStore<T>> = {};

    function constructPreferences(): PreferencePayload<T> {
        const payload: Partial<PreferencePayload<T>> = {};

        for (const key in preferences as PreferenceStore<T>) {
            payload[key] = get(preferences[key]);
        }

        return payload as PreferencePayload<T>;
    }

    function savePreferences(): void {
        setter(constructPreferences());
    }

    for (const [key, value] of Object.entries(
        toObject(Preferences, { defaults: true })
    )) {
        preferences[key] = createPreference(value, savePreferences);
    }

    return preferences as PreferenceStore<T>;
}

export async function getPreferences<T>(
    getter: () => Promise<T>,
    setter: (payload: PreferencePayload<T>) => Promise<void>,
    toObject: (preferences: T, options: { defaults: boolean }) => PreferenceRaw<T>
): Promise<PreferenceStore<T>> {
    const initialPreferences = await getter();
    return preparePreferences(initialPreferences, setter, toObject);
}
