<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { ToolbarItem } from "sveltelib/types";
    import ButtonGroup from "sveltelib/ButtonGroup.svelte";

    export let id: string;
    export let className = "";

    function extendClassName(className: string): string {
        return `dropdown-menu btn-dropdown-menu ${className}`;
    }

    export let items: ToolbarItem[];
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

<ButtonGroup {id} className={extendClassName(className)} {items} />
