<script lang="typescript">
    import { getContext, onMount, createEventDispatcher } from "svelte";
    import type { Readable } from "svelte/store";
    import { disabledKey } from "./contextKeys";

    export let id: string;
    export let className = "";
    export let tooltip: string;

    export let onClick: (event: MouseEvent) => void;
    export let active = false;
    export let disables = true;
    export let dropdownToggle = false;

    $: extraProps = dropdownToggle
        ? {
              "data-bs-toggle": "dropdown",
              "aria-expanded": "false",
          }
        : {};

    let buttonRef: HTMLButtonElement;

    const disabled = getContext(disabledKey);
    $: _disabled = disables && $disabled;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    button {
        display: inline-block;
        padding: 0;

        background-color: white;

        &:hover {
            box-shadow: 0 0 calc(var(--toolbar-size) / 2.5)
                calc(var(--toolbar-size) / 7.5) rgb(255 255 255 / 0.5);
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

        @include button.disabled {
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

    .dropdown-toggle::after {
        margin-right: 0.25rem;
    }
</style>

<button
    bind:this={buttonRef}
    {id}
    class={className}
    class:active
    class:dropdown-toggle={dropdownToggle}
    tabindex="-1"
    title={tooltip}
    disabled={_disabled}
    {...extraProps}
    on:click={onClick}
    on:mousedown|preventDefault>
    <span class="p-1"><slot /></span>
</button>
