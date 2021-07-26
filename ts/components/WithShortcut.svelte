<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onDestroy } from "svelte";
    import { registerShortcut, getPlatformString } from "lib/shortcuts";

    export let shortcut: string;

    const shortcutLabel = getPlatformString(shortcut);

    let deregister: () => void;

    function createShortcut(mounted: HTMLElement): void {
        deregister = registerShortcut((event: KeyboardEvent) => {
            mounted.dispatchEvent(new MouseEvent("click", event));
            event.preventDefault();
        }, shortcut);
    }

    onDestroy(() => deregister());
</script>

<slot {createShortcut} {shortcutLabel} />
