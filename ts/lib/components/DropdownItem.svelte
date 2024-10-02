<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let id: string | undefined = undefined;
    export let role: string | undefined = undefined;
    export let selected = false;
    let className = "";
    export { className as class };

    export let buttonRef: HTMLButtonElement | undefined = undefined;

    export let tooltip: string | undefined = undefined;

    export let active = false;
    export let disabled = false;

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    $: if (buttonRef && active) {
        buttonRef!.scrollIntoView({
            behavior: "smooth",
            block: "nearest",
        });
    }

    export let tabbable = false;
</script>

<button
    bind:this={buttonRef}
    {id}
    {role}
    aria-selected={selected}
    tabindex={tabbable ? 0 : -1}
    class="dropdown-item {className}"
    class:active
    class:rtl
    title={tooltip}
    {disabled}
    on:mouseenter
    on:focus
    on:keydown
    on:click
    on:mousedown|preventDefault
>
    <slot />
</button>

<style lang="scss">
    button {
        display: flex;
        justify-content: start;
        width: 100%;
        padding: 0.25rem 1rem;
        white-space: nowrap;
        font-size: var(--dropdown-font-size, small);

        background: none;
        box-shadow: none !important;
        border: none;
        border-radius: 0;
        color: var(--fg);

        &:hover {
            border: none;
        }

        &:hover:not([disabled]) {
            background: var(--highlight-bg);
            color: var(--highlight-fg);
        }

        &.focus {
            // TODO this is subtly different from hovering with the mouse for some reason
            @extend button, :hover;
        }

        &[disabled] {
            cursor: default;
            color: var(--fg-disabled);
        }

        /* selection highlight */
        &:not(.rtl) {
            border-left: 3px solid transparent;
        }
        &.rtl {
            border-right: 3px solid transparent;
        }
        &.active {
            &:not(.rtl) {
                border-left-color: var(--border-focus);
            }
            &.rtl {
                border-right-color: var(--border-focus);
            }
        }
    }
</style>
