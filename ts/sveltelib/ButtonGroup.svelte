<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { ToolbarItem } from "sveltelib/types";
    import { getContext } from "svelte";
    import { nightModeKey } from "sveltelib/contextKeys";

    export let id: string | undefined;
    export let className = "";
    export let items: ToolbarItem[];
    export let size: number;

    $: sizeString = size ? `--toolbar-size: ${size}px;` : undefined;

    function filterHidden({ hidden = false, ...props }) {
        return props;
    }

    const nightMode = getContext(nightModeKey);
</script>

<style lang="scss">
    div {
        display: flex;
        justify-items: start;
        flex-wrap: var(--toolbar-wrap);

        padding: calc(var(--toolbar-size) / 10);
        margin: 0;

        /* :is() would be better suited here */
        > :global(button),
        > :global(select) {
            border-radius: calc(var(--toolbar-size) / 7.5);

            &:not(:nth-of-type(1)) {
                border-top-left-radius: 0;
                border-bottom-left-radius: 0;
            }

            &:not(:nth-last-of-type(1)) {
                border-top-right-radius: 0;
                border-bottom-right-radius: 0;
            }
        }

        &.border-overlap-group {
            > :global(button),
            > :global(select) {
                margin-left: -1px !important;
            }
        }

        &.gap-group {
            > :global(button),
            > :global(select) {
                margin-left: 1px !important;
            }
        }
    }
</style>

<div
    {id}
    class={`btn-group ${className}`}
    class:border-overlap-group={!nightMode}
    class:gap-group={nightMode}
    style={sizeString}
    dir="ltr">
    {#each items as button}
        {#if !button.hidden}
            <svelte:component this={button.component} {...filterHidden(button)} />
        {/if}
    {/each}
</div>
