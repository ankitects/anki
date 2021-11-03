<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "./IconConstrain.svelte";
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

    export let iconSize = 75;
    export let widthMultiplier = 1;
    export let flipX = false;

    let buttonRef: HTMLButtonElement;

    const nightMode = getContext<boolean>(nightModeKey);
    const dropdownProps = getContext<DropdownProps>(dropdownKey) ?? { dropdown: false };

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<button
    bind:this={buttonRef}
    {id}
    class="icon-button btn {className}"
    class:active
    class:dropdown-toggle={dropdownProps.dropdown}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    {...dropdownProps}
    {disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <IconConstrain {flipX} {widthMultiplier} {iconSize}>
        <slot />
    </IconConstrain>
</button>

<style lang="scss">
    @use "sass/button-mixins" as button;

    .icon-button {
        padding: 0;
        font-size: var(--base-font-size);
        height: var(--buttons-size);

        @include button.btn-border-radius;
    }

    @include button.btn-day;
    @include button.btn-night;

    .dropdown-toggle::after {
        margin-right: 0.25rem;
    }
</style>
