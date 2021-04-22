<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import type { ToolbarItem } from "./types";

    import { onDestroy } from "svelte";
    import { registerShortcut } from "anki/shortcuts";

    export let button: ToolbarItem;
    export let shortcuts: string[];

    function extend({ ...rest }: DynamicSvelteComponent): DynamicSvelteComponent {
        return {
            ...rest,
        };
    }

    let deregister: (() => void)[];

    function createShortcut({ detail }: CustomEvent): void {
        const mounted: HTMLButtonElement = detail.button;
        deregisters = shortcuts.map((shortcut: string): (() => void) =>
            registerShortcut((event) => {
                mounted.dispatchEvent(new MouseEvent("click"));
                event.preventDefault();
            }, shortcut)
        );
    }

    onDestroy(() => deregisters.map((dereg) => dereg()));
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createShortcut} />
