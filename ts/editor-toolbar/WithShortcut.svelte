<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import type { ToolbarItem } from "./types";

    export let button: ToolbarItem;
    /* export let event: string; */

    function extend({
        className,
        ...rest
    }: DynamicSvelteComponent): DynamicSvelteComponent {
        return {
            ...rest,
        };
    }

    function createShortcut({ detail }: CustomEvent): void {
        const button: HTMLButtonElement = detail.button;

        document.addEventListener("click", () => {
            const event = new MouseEvent("click");

            if (code === "KeyP") {
                button.dispatchEvent(event);
            }
        });
    }
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createShortcut} />
