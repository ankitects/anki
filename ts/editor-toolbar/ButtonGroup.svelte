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
        overflow-y: auto;

        padding: calc(var(--toolbar-size) / 10);
        margin: 0;

        > :global(button),
        > :global(select) {
            border-radius: 0;

            &:nth-child(1) {
                border-top-left-radius: calc(var(--toolbar-size) / 7.5);
                border-bottom-left-radius: calc(var(--toolbar-size) / 7.5);
            }

            &:nth-last-child(1) {
                border-top-right-radius: calc(var(--toolbar-size) / 7.5);
                border-bottom-right-radius: calc(var(--toolbar-size) / 7.5);
            }
        }

        &.border-overlap-group {
            :global(button),
            :global(select) {
                margin-left: -1px;
            }
        }

        &.gap-group {
            :global(button),
            :global(select) {
                margin-left: 1px;
            }
        }
    }
</style>

<div
    {id}
    class={`btn-group ${className}`}
    class:border-overlap-group={!nightMode}
    class:gap-group={nightMode}>
    {#each items as button}
        {#if !button.hidden}
            <svelte:component this={button.component} {...filterHidden(button)} />
        {/if}
    {/each}
</div>
