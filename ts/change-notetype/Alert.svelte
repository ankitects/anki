<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { slide } from "svelte/transition";

    import Badge from "../components/Badge.svelte";
    import { minusIcon, plusIcon } from "./icons";
    import { exclamationIcon } from "./icons";
    import { MapContext } from "./lib";

    export let unused: string[];
    export let ctx: MapContext;

    let unusedMsg: string;
    $: unusedMsg =
        ctx === MapContext.Field
            ? tr.changeNotetypeWillDiscardContent()
            : tr.changeNotetypeWillDiscardCards();

    const maxItems: number = 3;
    let collapsed: boolean = true;
    $: collapseMsg = collapsed
        ? tr.changeNotetypeExpand()
        : tr.changeNotetypeCollapse();
    $: icon = collapsed ? plusIcon : minusIcon;
</script>

<div class="alert alert-warning" in:slide out:slide>
    {#if unused.length > 0}
        <div class="exclamation-icon">
            <Badge iconSize={80}>
                {@html exclamationIcon}
            </Badge>
        </div>
    {/if}
    {#if unused.length > maxItems}
        <div class="clickable" on:click={() => (collapsed = !collapsed)}>
            <Badge iconSize={80}>
                {@html icon}
            </Badge>
            {collapseMsg}
        </div>
    {/if}
    {`${unusedMsg} `}
    {#if collapsed}
        <span>
            {unused.slice(0, maxItems).join(", ")}
            {#if unused.length > maxItems}
                ... (+{unused.length - maxItems})
            {/if}
        </span>
    {:else}
        <ul>
            {#each unused as entry}
                <li>{entry}</li>
            {/each}
        </ul>
    {/if}
</div>

<style lang="scss">
    .exclamation-icon {
        position: absolute;
        top: 10px;
        :global([dir="ltr"]) & {
            right: 10px;
        }
        :global([dir="rtl"]) & {
            left: 10px;
        }
    }
    .clickable {
        cursor: pointer;
        font-weight: bold;
    }
</style>
