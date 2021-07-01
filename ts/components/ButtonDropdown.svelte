<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { dropdownKey } from "./context-keys";

    import ButtonToolbar from "./ButtonToolbar.svelte";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let api: Record<string, unknown> | undefined = undefined;

    setContext(dropdownKey, null);
</script>

<ButtonToolbar
    {id}
    class={`dropdown-menu btn-dropdown-menu ${className}`}
    wrap={false}
    {api}
>
    <slot />
</ButtonToolbar>

<style lang="scss">
    :global(.dropdown-menu.btn-dropdown-menu) {
        display: none;
        min-width: 0;
        padding: calc(var(--buttons-size) / 10) 0;

        background-color: var(--window-bg);
        border-color: var(--medium-border);

        :global(.btn-group) {
            position: static;
        }
    }

    :global(.dropdown-menu.btn-dropdown-menu.show) {
        display: flex;
    }
</style>
