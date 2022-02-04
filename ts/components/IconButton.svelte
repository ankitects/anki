<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, getContext, onMount } from "svelte";

    import { pageTheme } from "../sveltelib/theme";
    import { dropdownKey } from "./context-keys";
    import type { DropdownProps } from "./dropdown";
    import IconConstrain from "./IconConstrain.svelte";

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
    class:btn-day={!$pageTheme.isDark}
    class:btn-night={$pageTheme.isDark}
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
