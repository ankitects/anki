<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import Badge from "../components/Badge.svelte";
    import { MapContext } from "./lib";
    import { plusIcon, minusIcon } from "./icons";
    import { slide } from "svelte/transition";

    export let unused: string[];
    export let ctx: MapContext;

    let unusedMsg: string;
    $: unusedMsg =
        ctx === MapContext.Field
            ? tr.changeNotetypeWillDiscardContent()
            : tr.changeNotetypeWillDiscardCards();

    let maxItems: number = 3;
    let collapsed: boolean = true;
    $: collapseMsg = collapsed
        ? tr.changeNotetypeExpand()
        : tr.changeNotetypeCollapse();
    $: icon = collapsed ? plusIcon : minusIcon;
</script>

<div class="alert alert-warning" in:slide out:slide>
    {#if unused.length > maxItems}
        <div class="pe-auto" on:click={() => (collapsed = !collapsed)}>
            <Badge iconSize={80}>
                {@html icon}
            </Badge>
            {collapseMsg}
        </div>
    {/if}
    {unusedMsg}
    {#if collapsed}
        <div>
            {unused.slice(0, maxItems).join(", ")}
            {#if unused.length > maxItems}
                ... (+{unused.length - maxItems})
            {/if}
        </div>
    {:else}
        <ul>
            {#each unused as entry}
                <li>{entry}</li>
            {/each}
        </ul>
    {/if}
</div>
