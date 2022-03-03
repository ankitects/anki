<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import PanesLoading from "./PanesLoading.svelte";
    import type { PaneInput } from "./utils";
    import VerticalSplit from "./VerticalSplit.svelte";

    type T = any;

    export let panes: PaneInput<T>[];
</script>

{#if panes.length === 0}
    <PanesLoading />
{:else}
    <VerticalSplit
        root
        {panes}
        on:panefocus
        on:panehsplit
        on:panevsplit
        let:id={innerId}
        let:data={innerData}
    >
        <slot name="header" slot="header" id={innerId} data={innerData} />
        <slot name="content" slot="content" id={innerId} data={innerData} />
    </VerticalSplit>
{/if}
