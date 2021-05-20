<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Modifier } from "lib/shortcuts";

    import { onDestroy } from "svelte";
    import { registerShortcut, getPlatformString } from "lib/shortcuts";

    export let shortcut: string[][];
    export let optionalModifiers: Modifier[] | undefined = [];

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
            optionalModifiers
        );
    }

    onDestroy(() => deregister());
</script>

<slot {createShortcut} {shortcutLabel} />
