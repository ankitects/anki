<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { getContext, onMount, createEventDispatcher } from "svelte";
    import { nightModeKey, dropdownKey } from "./context-keys";
    import type { DropdownProps } from "./dropdown";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let active = false;
    export let disabled = false;
    export let tabbable = false;

    export let iconSize: number = 75;
    export let widthMultiplier: number = 1;

    let buttonRef: HTMLButtonElement;

    const nightMode = getContext<boolean>(nightModeKey);
    const dropdownProps = getContext<DropdownProps>(dropdownKey) ?? { dropdown: false };

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<button
    bind:this={buttonRef}
    {id}
    class={`btn ${className}`}
    class:active
    class:dropdown-toggle={dropdownProps.dropdown}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    style={`--icon-size: ${iconSize}%`}
    title={tooltip}
    {...dropdownProps}
    {disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <span style={`--width-multiplier: ${widthMultiplier};`}> <slot /> </span>
</button>

<style lang="scss">
    @use "ts/sass/button-mixins" as button;

    button {
        padding: 0;
        @include button.btn-border-radius;
    }

    @include button.btn-day;
    @include button.btn-night;

    span {
        display: inline-block;
        position: relative;
        vertical-align: middle;

        /* constrain icon */
        width: calc((var(--buttons-size) - 2px) * var(--width-multiplier));
        height: calc(var(--buttons-size) - 2px);

        & > :global(svg),
        & > :global(img) {
            position: absolute;
            width: var(--icon-size);
            height: var(--icon-size);
            top: calc((100% - var(--icon-size)) / 2);
            bottom: calc((100% - var(--icon-size)) / 2);
            left: calc((100% - var(--icon-size)) / 2);
            right: calc((100% - var(--icon-size)) / 2);

            fill: currentColor;
            vertical-align: unset;
        }
    }

    .dropdown-toggle::after {
        margin-right: 0.25rem;
    }
</style>
