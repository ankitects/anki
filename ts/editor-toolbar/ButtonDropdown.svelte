<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { ToolbarItem } from "./types";
    import ButtonGroup from "./ButtonGroup.svelte";

    export let id: string;
    export let className = "";

    function extendClassName(className: string): string {
        return `dropdown-menu btn-dropdown-menu ${className}`;
    }

    export let items: ToolbarItem[];
</script>

<style>
    :global(ul.btn-dropdown-menu) {
        display: none;
        background-color: var(--window-bg);
        border-color: var(--medium-border);
    }

    :global(ul.btn-dropdown-menu.show) {
        display: flex;
    }
</style>

<ButtonGroup {id} className={extendClassName(className)} {items} />
