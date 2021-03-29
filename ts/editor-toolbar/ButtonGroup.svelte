<script lang="typescript" context="module">
    export type Buttons =
        | { component: SvelteComponent; [...arg: string]: unknown }
        | Buttons[];
</script>

<script lang="typescript">
    export let buttons: Buttons;
</script>

<style lang="scss">
    ul {
        display: inline-block;

        padding-inline-start: 0;
        margin-bottom: 0;

        & :global(button) {
            margin-left: -1px;
        }
    }

    li {
        display: inline-block;
        margin-bottom: 2px;

        &:nth-child(1) {
            margin-left: 0.25rem;

            & > :global(button) {
                border-top-left-radius: 0.25rem;
                border-bottom-left-radius: 0.25rem;
            }
        }

        &:nth-last-child(1) {
            margin-right: 0.25rem;

            & > :global(button) {
                border-top-right-radius: 0.25rem;
                border-bottom-right-radius: 0.25rem;
            }
        }
    }
</style>

<ul>
    {#each buttons as button}
        <li>
            {#if Array.isArray(button)}
                <svelte:self buttons={button} />
            {:else}
                <svelte:component this={button.component} {...button} />
            {/if}
        </li>
    {/each}
</ul>
