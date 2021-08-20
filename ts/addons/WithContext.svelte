<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { SvelteComponentTyped } from "svelte/internal";
    import WithContext from "components/WithContext.svelte";

    export let key: string;
    export let getComponent: ({ context }) => SvelteComponentTyped;
</script>

<WithContext {key} let:context>
    {#each [getComponent({ context })] as data}
        <svelte:component this={data.component} {...data.props} />
    {/each}
</WithContext>
