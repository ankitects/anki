<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { dropdownKey } from "./contextKeys";

    import ButtonGroup from "./ButtonGroup.svelte";

    export let id: string | undefined;
    let className = "";
    export { className as class };

    export let api = {};

    setContext(dropdownKey, null);
</script>

<style>
    :global(.dropdown-menu.btn-dropdown-menu) {
        display: none;
        background-color: var(--window-bg);
        border-color: var(--medium-border);
    }

    :global(.dropdown-menu.btn-dropdown-menu.show) {
        display: flex;
    }
</style>

<ButtonGroup {id} class={`dropdown-menu btn-dropdown-menu ${className}`} {api}>
    <slot />
</ButtonGroup>
