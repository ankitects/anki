<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";

    export let id: string;
    export let className = "";

    export let label: string;
    export let tooltip: string;
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

    function extendClassName(className: string): string {
        return `btn ${className}`;
    }

    const disabled = getContext(disabledKey);
    $: _disabled = disables && $disabled;

    const nightMode = getContext(nightModeKey);

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

    .btn-light {
        @include button.light-hover-active;
    }
</style>

<button
    bind:this={buttonRef}
    {id}
    class={extendClassName(className)}
    class:dropdown-toggle={dropdownToggle}
    class:btn-light={!nightMode}
    class:btn-secondary={nightMode}
    tabindex="-1"
    disabled={_disabled}
    title={tooltip}
    {...extraProps}
    on:click={onClick}
    on:mousedown|preventDefault>
    {label}
</button>
