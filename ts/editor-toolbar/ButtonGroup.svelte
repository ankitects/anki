<script lang="typescript">
    import type { Buttons } from "./types";

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    export let buttons: Buttons;
    export let wraps: boolean;
</script>

<style lang="scss">
    ul {
        display: flex;
        overflow-y: auto;

        padding-inline-start: 0;
        margin-bottom: 0;

        & :global(button),
        & :global(select) {
            margin-left: -1px;
        }
    }

    .wraps {
        flex-flow: wrap;
    }

    li {
        display: inline-block;
        margin-bottom: calc(var(--toolbar-size) / 15);

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

<ul {id} class={className} {...props} class:wraps>
    {#each buttons as button}
        <li>
            {#if Array.isArray(button)}
                <svelte:self buttons={button} {wraps} />
            {:else}
                <svelte:component this={button.component} {...button} />
            {/if}
        </li>
    {/each}
</ul>
