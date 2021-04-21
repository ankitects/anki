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
    export let shortcut: string;

    function extend({ ...rest }: DynamicSvelteComponent): DynamicSvelteComponent {
        return {
            ...rest,
        };
    }

    let deregister;

    function createShortcut({ detail }: CustomEvent): void {
        const button: HTMLButtonElement = detail.button;
        deregister = registerShortcut(() => button.click(), shortcut);
    }

    onDestroy(() => deregister());
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createShortcut} />
