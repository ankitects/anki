<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let buttonRef: HTMLButtonElement | undefined = undefined;

    export let tooltip: string | undefined = undefined;

    export let active = false;
    export let disabled = false;

    $: if (buttonRef && active) {
        setTimeout(() =>
            buttonRef!.scrollIntoView({
                behavior: "smooth",
                block: "nearest",
            }),
        );
    }

    export let tabbable = false;
</script>

<button
    bind:this={buttonRef}
    {id}
    tabindex={tabbable ? 0 : -1}
    class="dropdown-item {className}"
    class:active
    class:disabled
    title={tooltip}
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

        font-size: var(--dropdown-font-size, calc(0.8 * var(--base-font-size)));

        background: none;
        box-shadow: none !important;
        border: none;
        border-radius: 0;
        color: var(--fg);

        &:hover {
            background: var(--highlight-bg);
            color: var(--highlight-fg);
        }
    }
</style>
