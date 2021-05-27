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
    export let theme = "anki";

    function extendClassName(className: string, theme: string): string {
        return `btn ${theme !== "anki" ? `btn-${theme}` : ""}${className}`;
    }

    export let tooltip: string | undefined = undefined;
    export let active = false;
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

<button
    bind:this={buttonRef}
    {id}
    class={extendClassName(className, theme)}
    class:active
    class:dropdown-toggle={dropdownProps.dropdown}
    class:btn-day={theme === "anki" && !nightMode}
    class:btn-night={theme === "anki" && nightMode}
    title={tooltip}
    {...dropdownProps}
    disabled={_disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <slot />
</button>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    button {
        padding: 0 calc(var(--buttons-size) / 3);
        width: auto;
        height: var(--buttons-size);

        @include button.btn-border-radius;
    }

    @include button.btn-day;
    @include button.btn-night;
</style>
