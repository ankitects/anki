<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { SvelteComponentTyped } from "svelte/internal";
    import WithState from "components/WithState.svelte";

    export let key: string;
    export let update: (event: Event) => boolean;

    export let getComponent: (
        state: unknown,
        updateState: unknown
    ) => SvelteComponentTyped;
</script>

<WithState {key} {update} let:state let:updateState>
    {#each [getComponent(state, updateState)] as data}
        <svelte:component this={data.component} {...data.props} />
    {/each}
</WithState>
