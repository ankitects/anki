<script lang="typescript" context="module">
    import { writable } from "svelte/store";

    export const commandMap = writable(new Map<string, boolean>());

    function updateButton(key: string): void {
        commandMap.update(
            (map: Map<string, boolean>): Map<string, boolean> =>
                new Map([...map, [key, document.queryCommandState(key)]])
        );
    }

    export function updateButtons() {
        commandMap.update(
            (map: Map<string, boolean>): Map<string, boolean> => {
                const newMap = new Map<string, boolean>();

                for (const key of map.keys()) {
                    newMap.set(key, document.queryCommandState(key));
                }

                return newMap;
            }
        );
    }
</script>

<script lang="typescript">
    import InnerButton from "./InnerButton.svelte";

    export let className = "";
    export let icon = "";
    export let command: string;
    export let activatable = true;

    let active = false;

    if (activatable) {
        updateButton(command);

        commandMap.subscribe((map: Record<string, boolean>): void => {
            active = map.get(command);
            return () => map.delete(command);
        });
    }

    function onClick(event: ClickEvent): void {
        document.execCommand(command);
    }
</script>

<InnerButton {className} {active} {onClick}>
    {@html icon}
</InnerButton>
