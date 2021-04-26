<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";
    import { mergeTooltipAndShortcut } from "./helpers";

    export let id: string;
    export let className = "";
    export let theme = "anki";
    export let label = "";

    export let tooltip: string | undefined;
    export let shortcutLabel: string | undefined;

    $: title = mergeTooltipAndShortcut(tooltip, shortcutLabel);

    export let onClick: (event: MouseEvent) => void;
    export let disables = true;
    export let dropdownToggle = false;

    $: extraProps = dropdownToggle
        ? {
              "data-bs-toggle": "dropdown",
              "aria-expanded": "false",
          }
        : {};

    let buttonRef: HTMLButtonElement;

    function extendClassName(className: string, theme): string {
        return `btn ${theme !== "anki" ? `btn-${theme}` : ""}${className}`;
    }

    const disabled = getContext<Readable<boolean>>(disabledKey);
    $: _disabled = disables && $disabled;

    const nightMode = getContext<boolean>(nightModeKey);

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
    }

    @include button.btn-day;
    @include button.btn-night;
</style>

<button
    bind:this={buttonRef}
    {id}
    class={extendClassName(className, theme)}
    class:dropdown-toggle={dropdownToggle}
    class:btn-day={theme === 'anki' && !nightMode}
    class:btn-night={theme === 'anki' && nightMode}
    tabindex="-1"
    disabled={_disabled}
    {title}
    {...extraProps}
    on:click={onClick}
    on:mousedown|preventDefault>
    {label}
</button>
