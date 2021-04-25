<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponents";
    import type { ToolbarItem } from "sveltelib/types";
    import type { Modifier } from "lib/shortcuts";

    import { onDestroy } from "svelte";
    import { registerShortcut, getPlatformString } from "lib/shortcuts";

    export let button: ToolbarItem;
    export let shortcut: string;
    export let optionalModifiers: Modifier[];

    function extend({ ...rest }: DynamicSvelteComponent): DynamicSvelteComponent {
        const shortcutLabel = getPlatformString(shortcut);

        return {
            shortcutLabel,
            ...rest,
        };
    }

    let deregister: () => void;

    function createShortcut({ detail }: CustomEvent): void {
        const mounted: HTMLButtonElement = detail.button;
        deregister = registerShortcut(
            (event: KeyboardEvent) => {
                mounted.dispatchEvent(new KeyboardEvent("click", event));
                event.preventDefault();
            },
            shortcut,
            optionalModifiers
        );
    }

    onDestroy(() => deregister());
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createShortcut} />
