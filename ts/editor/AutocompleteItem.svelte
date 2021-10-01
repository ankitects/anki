<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "../components/context-keys";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let selected = false;
    export let active = false;

    const nightMode = getContext<boolean>(nightModeKey);
    const dispatch = createEventDispatcher();

    let buttonRef: HTMLButtonElement;

    export function scroll() {
        /* TODO will not work on Gecko */
        (buttonRef as any)?.scrollIntoViewIfNeeded(false);

        /* buttonRef.scrollIntoView({ behavior: "smooth", block: "start" }); */
    }

    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<button
    bind:this={buttonRef}
    {id}
    tabindex="-1"
    class="autocomplete-item btn {className}"
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    class:selected
    class:active
    on:mousedown|preventDefault
    on:mouseup
    on:mouseenter
    on:mouseleave
>
    <slot />
</button>

<style lang="scss">
    @use "button-mixins" as button;

    .autocomplete-item {
        padding: 1px 7px 2px;

        text-align: start;
        white-space: nowrap;
    }

    button {
        display: flex;
        justify-content: space-between;

        font-size: calc(var(--buttons-size) / 2.3);

        background: none;
        box-shadow: none !important;
        border: none;

        &.active {
            background-color: button.$focus-color !important;
            color: white !important;
        }
    }

    /* reset global CSS from buttons.scss */
    :global(.nightMode) button:hover {
        background-color: inherit;
    }

    /* extra specificity bc of global CSS reset above */
    button.btn-day {
        color: black;

        &.selected {
            background-color: #e9ecef;
            border-color: #e9ecef;
        }
    }

    button.btn-night {
        color: white;

        &.selected {
            @include button.btn-night-base;
        }
    }
</style>
