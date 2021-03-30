<script lang="typescript">
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";
    import { disabledKey } from "./contextKeys";

    export let className: string = "";
    export let onClick: (event: ClickEvent) => void;
    export let active = false;

    const disabledStore = getContext(disabledKey)
    $: disabled = $disabledStore;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: 28px;
        height: 28px;

        background-color: white;

        & > :global(svg),
        & > :global(img) {
            fill: currentColor;
            vertical-align: unset;
            width: 100%;
            height: 100%;
        }

        &:hover {
            background-color: #eee;
        }

        &:active,
        &.active {
            box-shadow: inset 0 0 12px 4px rgb(0 0 0 / 30%);
            border-color: #aaa;
        }

        &.active:active {
            box-shadow: none;
            border-color: var(--border);
        }

        &[disabled] {
            opacity: 0.4;
            cursor: not-allowed;

            &:hover {
                background-color: white;
            }

            &:active,
            &.active {
                box-shadow: none;
            }
        }
    }
</style>

<button
    class="p-1 {className}"
    class:active
    tabindex="-1"
    {disabled}
    on:click={onClick}
    on:mousedown|preventDefault>
    <slot />
</button>
