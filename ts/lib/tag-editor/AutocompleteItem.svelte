<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let selected = false;
    export let active = false;
    export let suggestion: string; // used by add-ons to target individual suggestions

    let buttonRef: HTMLElement;

    $: if (selected && buttonRef) {
        /* buttonRef.scrollIntoView({ behavior: "smooth", block: "start" }); */
        /* TODO will not work on Gecko */
        (buttonRef as any).scrollIntoViewIfNeeded({
            behavior: "smooth",
            block: "start",
        });
    }
</script>

<div
    bind:this={buttonRef}
    tabindex="-1"
    class="autocomplete-item"
    class:selected
    class:active
    data-addon-suggestion={suggestion}
    on:mousedown|preventDefault
    on:mouseup
    on:mouseenter
    on:mouseleave
    role="button"
>
    <slot />
</div>

<style lang="scss">
    @use "../sass/button-mixins" as button;

    .autocomplete-item {
        padding: 4px 8px;

        text-align: start;
        white-space: nowrap;
        flex-grow: 1;
        border-radius: 0;
        border: 1px solid transparent;
        &:not(:first-child) {
            border-top-color: var(--border-subtle);
        }

        &:hover {
            @include button.base($with-disabled: false, $active-class: active);
        }
        &.selected {
            @include button.base(
                $primary: true,
                $with-disabled: false,
                $active-class: active
            );
        }
    }
</style>
