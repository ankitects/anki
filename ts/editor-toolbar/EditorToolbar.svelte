<script lang="typescript">
    import ButtonGroup from "./ButtonGroup.svelte";
    import type { Buttons } from "./ButtonGroup.svelte";

    export let buttons: Buttons = [];
    export let nightMode: boolean;

    console.log(nightMode);
</script>

<style lang="scss">
    div {
        position: sticky;
        top: 0;
        left: 0;
        z-index: 10;

        background: var(--bg-color);
        border-bottom: 1px solid var(--border);

        /* Remove most outer marigns */
        & > :global(ul) {
            & > :global(li:nth-child(1)) {
                margin-left: 0;
            }

            & > :global(li:nth-last-child(1)) {
                margin-right: 0;
            }
        }
    }
</style>

<div>
    <ButtonGroup {buttons} />
</div>
