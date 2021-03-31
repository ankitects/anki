<script lang="typescript" context="module">
    import { writable } from "svelte/store";

    export const commandMap = writable(new Map<string, boolean>());

    function initializeButton(key: string): void {
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

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};
    export let title: string;

    export let icon = "";
    export let command: string;
    export let activatable = true;

    let active = false;

    if (activatable) {
        initializeButton(command);

        commandMap.subscribe((map: Record<string, boolean>): void => {
            active = map.get(command);
            return () => map.delete(command);
        });
    }

    function onClick(event: ClickEvent): void {
        document.execCommand(command);
    }
</script>

<SquareButton {id} {className} {props} {title} {active} {onClick} on:mount>
    {@html icon}
</SquareButton>
