<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { SvelteComponentTyped } from "svelte/internal";
    import WithShortcut from "components/WithShortcut.svelte";

    export let shortcut: string;
    export let getComponent: (
        createShortcut: unknown,
        shortcutLabel: unknown
    ) => SvelteComponentTyped;
</script>

<WithShortcut {shortcut} let:createShortcut let:shortcutLabel>
    {#each [getComponent(createShortcut, shortcutLabel)] as data}
        <svelte:component this={data.component} {...data.props} />
    {/each}
</WithShortcut>
