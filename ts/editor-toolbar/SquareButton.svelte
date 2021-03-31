<script lang="typescript">
    import { getContext, onMount, createEventDispatcher } from "svelte";
    import type { Readable } from "svelte/store";
    import { disabledKey } from "./contextKeys";

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    export let onClick: (event: ClickEvent) => void;
    export let active = false;

    let buttonRef: HTMLButtonElement;

    const disabledStore = getContext(disabledKey);
    $: disabled = $disabledStore;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<style lang="scss">
    button {
        display: inline-block;
        padding: 0;

        background-color: white;

        &:hover {
            background-color: #eee;
        }

        &:active,
        &.active {
            box-shadow: inset 0 0 calc(var(--toolbar-size) / 2.5)
                calc(var(--toolbar-size) / 7.5) rgb(0 0 0 / 30%);
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

    span {
        display: inline-block;
        vertical-align: middle;

        /* constrain icon */
        width: calc(var(--toolbar-size) - 2px);
        height: calc(var(--toolbar-size) - 2px);

        & > :global(svg),
        & > :global(img) {
            fill: currentColor;
            vertical-align: unset;
            width: 100%;
            height: 100%;
        }
    }

    /* for DropdownMenu */
    :global(.dropdown-toggle)::after {
        margin-right: 0.25rem;
    }
</style>

<button
    bind:this={buttonRef}
    {id}
    class={className}
    {...props}
    class:active
    tabindex="-1"
    {disabled}
    on:click={onClick}
    on:mousedown|preventDefault>
    <span class="p-1"><slot /></span>
</button>
