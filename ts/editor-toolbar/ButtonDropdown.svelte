<script lang="typescript">
    import type { ToolbarItem } from "./types";
    import ButtonGroup from "./ButtonGroup.svelte";

    export let id = "";
    export let className = "";

    function extendClassName(className: string): string {
        return `dropdown-menu bg-transparent border-0 ${className}`;
    }

    export let buttons: ToolbarItem[];
</script>

<ButtonGroup {id} className={extendClassName(className)} {buttons} {...$$restProps} />
