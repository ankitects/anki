<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";
    import type { ToolbarItem } from "./types";
    import ButtonGroup from "./ButtonGroup.svelte";

    export let id: string;
    export let className = "";

    function extendClassName(className: string): string {
        return `dropdown-menu py-1 mb-0 ${
            nightMode ? "btn-dropdown-menu--night-mode " : ""
        }${className}`;
    }

    export let buttons: ToolbarItem[];

    const nightMode = getContext(nightModeKey);
</script>

<style>
    :global(.btn-dropdown-menu--night-mode) {
        background-color: var(--window-bg);
        border-color: var(--medium-border);
    }
</style>

<ButtonGroup {id} className={extendClassName(className)} {buttons} />
