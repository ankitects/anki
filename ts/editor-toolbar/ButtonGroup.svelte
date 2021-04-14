<script lang="typescript">
    import type { ToolbarItem } from "./types";

    export let id: string;
    export let className = "";
    export let buttons: ToolbarItem[];

    function filterHidden({ hidden, ...props }) {
        return props;
    }
</script>

<style lang="scss">
    ul {
        display: flex;
        flex-wrap: var(--toolbar-wrap);
        overflow-y: auto;

        padding-inline-start: 0;
        margin-bottom: 0;
    }

    li {
        display: inline-block;
        margin-bottom: calc(var(--toolbar-size) / 15);

        & > :global(button),
        & > :global(select) {
            border-radius: 0;
        }

        &:nth-child(1) {
            margin-left: calc(var(--toolbar-size) / 7.5);

            & > :global(button),
            & > :global(select) {
                /* default 0.25rem */
                border-top-left-radius: calc(var(--toolbar-size) / 7.5);
                border-bottom-left-radius: calc(var(--toolbar-size) / 7.5);
            }
        }

        &:nth-last-child(1) {
            margin-right: calc(var(--toolbar-size) / 7.5);

            & > :global(button),
            & > :global(select) {
                border-top-right-radius: calc(var(--toolbar-size) / 7.5);
                border-bottom-right-radius: calc(var(--toolbar-size) / 7.5);
            }
        }
    }
</style>

<ul {id} class={className}>
    {#each buttons as button}
        {#if !button.hidden}
            <li>
                <svelte:component this={button.component} {...filterHidden(button)} />
            </li>
        {/if}
    {/each}
</ul>
