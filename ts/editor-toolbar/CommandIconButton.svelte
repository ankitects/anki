<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript" context="module">
    import { writable } from "svelte/store";

    type UpdateMap = Map<string, (event: Event) => boolean>;
    type ActiveMap = Map<string, boolean>;

    const updateMap = new Map() as UpdateMap;
    const activeMap = writable(new Map() as ActiveMap);

    function updateButton(key: string, event: MouseEvent): void {
        activeMap.update(
            (map: ActiveMap): ActiveMap =>
                new Map([...map, [key, updateMap.get(key)(event)]])
        );
    }

    function updateButtons(callback: (key: string) => boolean): void {
        activeMap.update(
            (map: ActiveMap): ActiveMap => {
                const newMap = new Map() as ActiveMap;

                for (const key of map.keys()) {
                    newMap.set(key, callback(key));
                }

                return newMap;
            }
        );
    }

    export function updateActiveButtons(event: Event) {
        updateButtons((key: string): boolean => updateMap.get(key)(event));
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
    export let onClick = () => {
        document.execCommand(command);
    };

    function onClickWrapped(event: MouseEvent): void {
        onClick(event);
        updateButton(command, event);
    }

    export let activatable = true;
    export let onUpdate = (_event: Event) => document.queryCommandState(command);

    updateMap.set(command, onUpdate);

    let active = false;

    if (activatable) {
        activeMap.subscribe((map: ActiveMap): (() => void) => {
            active = Boolean(map.get(command));
            return () => map.delete(command);
        });
    }

    export let disables = true;
    export let dropdownToggle = false;
</script>

<SquareButton
    {id}
    {className}
    {tooltip}
    {active}
    {disables}
    {dropdownToggle}
    onClick={onClickWrapped}
    on:mount>
    {@html icon}
</SquareButton>
