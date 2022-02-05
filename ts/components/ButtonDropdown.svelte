<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { setContext } from "svelte";

    import ButtonToolbar from "./ButtonToolbar.svelte";
    import { dropdownKey } from "./context-keys";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    setContext(dropdownKey, null);
</script>

<ButtonToolbar {id} class="dropdown-menu btn-dropdown-menu {className}" wrap={false}>
    <div on:mousedown|preventDefault|stopPropagation on:click>
        <slot />
    </div>
</ButtonToolbar>

<style lang="scss">
    div {
        display: contents;
    }

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
