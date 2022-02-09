<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, getContext, onMount } from "svelte";

    import { pageTheme } from "../sveltelib/theme";
    import { dropdownKey } from "./context-keys";
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
    export let disabled = false;
    export let tabbable = false;

    const dropdownProps = getContext<DropdownProps>(dropdownKey) ?? { dropdown: false };

    let buttonRef: HTMLButtonElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<button
    bind:this={buttonRef}
    {id}
    class="label-button {extendClassName(className, theme)}"
    class:active
    class:dropdown-toggle={dropdownProps.dropdown}
    class:btn-day={theme === "anki" && !$pageTheme.isDark}
    class:btn-night={theme === "anki" && $pageTheme.isDark}
    title={tooltip}
    {...dropdownProps}
    {disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <slot />
</button>

<style lang="scss">
    @use "sass/button-mixins" as button;

    button {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        padding: 0 calc(var(--buttons-size) / 3);
        font-size: var(--base-font-size);
        width: auto;
        height: var(--buttons-size);

        @include button.btn-border-radius;
    }

    @include button.btn-day;
    @include button.btn-night;
</style>
