<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import type { ToolbarItem } from "./types";
    import type { Modifier } from "lib/shortcuts";

    import { onDestroy } from "svelte";
    import { registerShortcut, getPlatformString } from "lib/shortcuts";

    export let button: ToolbarItem;
    export let label: string;
</script>

<!-- svelte-ignore a11y-label-has-associated-control -->

<label>
    {label}
    <svelte:component this={button.component} {...button} />
</label>
