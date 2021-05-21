<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onDestroy } from "svelte";
    import { registerShortcut, getPlatformString } from "lib/shortcuts";

    export let shortcut: string[][];
    export let useCode = false;

    const shortcutLabel = getPlatformString(shortcut);

    let deregister: () => void;

    function createShortcut({ detail }: CustomEvent): void {
        const mounted: HTMLButtonElement = detail.button;
        deregister = registerShortcut(
            (event: KeyboardEvent) => {
                mounted.dispatchEvent(new MouseEvent("click", event));
                event.preventDefault();
            },
            shortcut,
            useCode
        );
    }

    onDestroy(() => deregister());
</script>

<slot {createShortcut} {shortcutLabel} />
