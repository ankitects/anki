<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { convertMathjax } from "./mathjax";

    export let mathjax: string;
    export let type: "inline" | "block" | "chemistry";

    let edit = false;
    $: delimiters = type === "inline" ? ["\\(", "\\)"] : ["\\[", "\\]"];
    $: converted = convertMathjax(`${delimiters[0]}${mathjax}${delimiters[1]}`);

    function autofocus(element: HTMLElement): void {
        element.focus();
    }
</script>

{#if edit}
    {#if type === "block"}
        <div
            contenteditable="true"
            bind:innerHTML={mathjax}
            on:blur={() => (edit = false)}
            use:autofocus
        />
    {:else}
        <span
            contenteditable="true"
            bind:innerHTML={mathjax}
            on:blur={() => (edit = false)}
            use:autofocus
        />
    {/if}
{:else}
    <div class="d-contents" on:click={() => (edit = true)}>
        {@html converted}
    </div>
{/if}

<style lang="scss">
    .d-contents {
        display: contents;
    }
</style>
