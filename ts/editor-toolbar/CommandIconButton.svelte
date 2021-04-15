<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript" context="module">
    import { writable } from "svelte/store";

    const commandMap = writable(new Map<string, boolean>());

    function updateButton(key: string): void {
        commandMap.update(
            (map: Map<string, boolean>): Map<string, boolean> =>
                new Map([...map, [key, document.queryCommandState(key)]])
        );
    }

    function updateButtons(callback: (key: string) => boolean): void {
        commandMap.update(
            (map: Map<string, boolean>): Map<string, boolean> => {
                const newMap = new Map<string, boolean>();

                for (const key of map.keys()) {
                    newMap.set(key, callback(key));
                }

                return newMap;
            }
        );
    }

    export function updateActiveButtons() {
        updateButtons((key: string): boolean => document.queryCommandState(key));
    }

    export function clearActiveButtons() {
        updateButtons((): boolean => false);
    }
</script>

<script lang="typescript">
    import SquareButton from "./SquareButton.svelte";

    export let id: string;
    export let className = "";
    export let tooltip: string;

    export let icon: string;
    export let command: string;
    export let activatable = true;
    export let disables = true;
    export let dropdownToggle = false;

    let active = false;

    if (activatable) {
        updateButton(command);

        commandMap.subscribe((map: Map<string, boolean>): (() => void) => {
            active = map.get(command);
            return () => map.delete(command);
        });
    }

    function onClick(): void {
        document.execCommand(command);
        updateButton(command);
    }
</script>

<SquareButton
    {id}
    {className}
    {tooltip}
    {active}
    {disables}
    {dropdownToggle}
    {onClick}
    on:mount>
    {@html icon}
</SquareButton>
