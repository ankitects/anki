<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import type { ToolbarItem } from "./types";

    import { onDestroy } from "svelte";
    import { registerShortcut, getPlatformString } from "anki/shortcuts";

    export let button: ToolbarItem;
    export let shortcuts: string[];

    function extend({ ...rest }: DynamicSvelteComponent): DynamicSvelteComponent {
        const shortcutLabel = getPlatformString(shortcuts[0]);

        return {
            shortcutLabel,
            ...rest,
        };
    }

    let deregisters: (() => void)[];

    function createShortcut({ detail }: CustomEvent): void {
        const mounted: HTMLButtonElement = detail.button;
        deregisters = shortcuts.map((shortcut: string): (() => void) =>
            registerShortcut((event) => {
                mounted.dispatchEvent(new MouseEvent("click"));
                event.preventDefault();
            }, shortcut)
        );
    }

    onDestroy(() => deregisters.forEach((dereg: () => void): void => dereg()));
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createShortcut} />
