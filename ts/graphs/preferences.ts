import type pb from "anki/backend_proto";
import { getGraphPreferences, setGraphPreferences } from "./graph-helpers";
import { Writable, writable, get } from "svelte/store";

export interface CustomStore<T> extends Writable<T> {
    subscribe: (getter: (value: T) => void) => () => void;
    set: (value: T) => void;
}

export type PreferenceStore = {
    [K in keyof Omit<pb.BackendProto.GraphsPreferencesOut, "toJSON">]: CustomStore<
        pb.BackendProto.GraphsPreferencesOut[K]
    >;
};

export type PreferencePayload = {
    [K in keyof Omit<
        pb.BackendProto.GraphsPreferencesOut,
        "toJSON"
    >]: pb.BackendProto.GraphsPreferencesOut[K];
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

function preparePreferences(
    graphsPreferences: pb.BackendProto.GraphsPreferencesOut
): PreferenceStore {
    const preferences: Partial<PreferenceStore> = {};

    function constructPreferences(): PreferencePayload {
        const payload: Partial<PreferencePayload> = {};

        for (const key in preferences as PreferenceStore) {
            payload[key] = get(preferences[key]);
        }

        return payload as PreferencePayload;
    }

    function savePreferences(): void {
        setGraphPreferences(constructPreferences());
    }

    for (const [key, value] of Object.entries(graphsPreferences)) {
        preferences[key] = createPreference(value, savePreferences);
    }

    return preferences as PreferenceStore;
}

export async function getPreferences(): Promise<PreferenceStore> {
    const initialPreferences = await getGraphPreferences();
    return preparePreferences(initialPreferences);
}
