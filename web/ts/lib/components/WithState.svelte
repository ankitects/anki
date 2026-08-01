<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts" context="module">
    import { writable } from "svelte/store";

    type KeyType = symbol | string;
    type UpdaterMap = Map<KeyType, (event: Event) => Promise<boolean>>;
    type StateMap = Map<KeyType, Promise<boolean>>;

    const updaterMap: UpdaterMap = new Map();
    const stateMap: StateMap = new Map();
    const stateStore = writable(stateMap);

    function updateAllStateWithCallback(
        callback: (key: KeyType) => Promise<boolean>,
    ): void {
        stateStore.update((map: StateMap): StateMap => {
            const newMap: StateMap = new Map();

            for (const key of map.keys()) {
                newMap.set(key, callback(key));
            }

            return newMap;
        });
    }

    export function updateAllState(event: Event): void {
        updateAllStateWithCallback(
            (key: KeyType): Promise<boolean> => updaterMap.get(key)!(event),
        );
    }

    export function resetAllState(state: boolean): void {
        updateAllStateWithCallback((): Promise<boolean> => Promise.resolve(state));
    }

    export function updateStateByKey(key: KeyType, event: Event): void {
        stateStore.update((map: StateMap): StateMap => {
            map.set(key, updaterMap.get(key)!(event));
            return map;
        });
    }
</script>

<script lang="ts">
    export let key: KeyType;
    export let update: (event: Event) => Promise<boolean>;

    let state: boolean = false;

    updaterMap.set(key, update);

    stateStore.subscribe((map: StateMap): (() => void) => {
        if (map.has(key)) {
            const stateValue = map.get(key)!;

            if (stateValue instanceof Promise) {
                stateValue.then((value: boolean): void => {
                    state = value;
                });
            } else {
                state = stateValue;
            }
        } else {
            state = false;
        }
        return () => map.delete(key);
    });

    stateMap.set(key, Promise.resolve(state));

    function updateState(event: Event): void {
        updateStateByKey(key, event);
    }
</script>

<slot {state} {updateState} />
