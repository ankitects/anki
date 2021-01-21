import { writable } from "svelte/store";

function createPreference(initialValue: unknown) {
    const { subscribe, set } = writable(initialValue);

    return {
        subscribe,
        set: (v: unknown) => {
            set(v);
        },
    };
}

export const calendarFirstDayOfWeek = createPreference(0);
export const cardCountsSeparateInactive = createPreference(false);
