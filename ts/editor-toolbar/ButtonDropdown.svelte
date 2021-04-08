<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import ButtonGroup from "./ButtonGroup.svelte";

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    function extendClassName(className: string): string {
        return `dropdown-menu bg-transparent border-0 ${className}`;
    }

    export let buttons: DynamicSvelteComponent[];
</script>

<ButtonGroup {id} className={extendClassName(className)} {props} {buttons} />
