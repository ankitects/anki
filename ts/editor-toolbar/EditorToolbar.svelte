<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";

    import ButtonGroup from "./ButtonGroup.svelte";
    import type { Buttons } from "./ButtonGroup.svelte";

    export let buttons: Buttons = [];
    export let nightMode: boolean;
    export let disabled: Readable<boolean> = false;

    setContext(disabledKey, disabled);
    setContext(nightModeKey, nightMode);
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
