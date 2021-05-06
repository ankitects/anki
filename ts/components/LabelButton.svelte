<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey, nightModeKey, dropdownKey } from "./contextKeys";
    import type { DropdownProps } from "./dropdown";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let tooltip: string | undefined;
    export let disables = true;
    export let tabbable = false;

    const disabled = getContext<Readable<boolean>>(disabledKey);
    $: _disabled = disables && $disabled;

    const nightMode = getContext<boolean>(nightModeKey);
    const dropdownProps = getContext<DropdownProps>(dropdownKey) ?? { dropdown: false };

    let buttonRef: HTMLButtonElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    button {
        padding: 0 calc(var(--toolbar-size) / 3);
        font-size: calc(var(--toolbar-size) / 2.3);
        width: auto;
        height: var(--toolbar-size);

        @include button.btn-border-radius;
    }

    @include button.btn-day;
    @include button.btn-night;
</style>

<button
    bind:this={buttonRef}
    {id}
    class={`btn ${className}`}
    class:dropdown-toggle={dropdownProps.dropdown}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    {...dropdownProps}
    disabled={_disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault>
    <slot />
</button>
