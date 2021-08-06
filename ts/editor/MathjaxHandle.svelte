<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import HandleSelection from "./HandleSelection.svelte";
    import HandleBackground from "./HandleBackground.svelte";
    import HandleControl from "./HandleControl.svelte";
    import MathjaxHandleInlineBlock from "./MathjaxHandleInlineBlock.svelte";

    export let activeImage: HTMLImageElement | null = null;
    export let container: HTMLElement;
    export let isRtl: boolean;

    let updateSelection: () => void;
</script>

{#if activeImage}
    <HandleSelection
        image={activeImage}
        {container}
        offsetX={2}
        offsetY={2}
        bind:updateSelection
    >
        <HandleBackground />

        <div
            class="mathjax-handle-inline-block"
            class:is-rtl={isRtl}
            on:click={updateSelection}
        >
            <MathjaxHandleInlineBlock {activeImage} {isRtl} />
        </div>

        <HandleControl />
    </HandleSelection>
{/if}

<style lang="scss">
    div {
        position: absolute;
    }

    .mathjax-handle-inline-block {
        left: 3px;
        top: 3px;

        &.is-rtl {
            left: auto;
            right: 3px;
        }
    }
</style>
