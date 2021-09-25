<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DropdownProps } from "./dropdown";
    import { dropdownKey } from "./context-keys";
    import { onMount, createEventDispatcher, getContext } from "svelte";

    let className = "";
    export { className as class };
    export let tooltip: string | undefined = undefined;

    const dispatch = createEventDispatcher();

    let spanRef: HTMLSpanElement;

    const dropdownProps = getContext<DropdownProps>(dropdownKey) ?? { dropdown: false };

    onMount(() => {
        dispatch("mount", { span: spanRef });
    });
</script>

<span
    bind:this={spanRef}
    title={tooltip}
    class={`badge ${className}`}
    class:dropdown-toggle={dropdownProps.dropdown}
    {...dropdownProps}
    on:click
    on:mouseenter
    on:mouseleave
>
    <slot />
</span>

<style>
    .badge {
        color: inherit;
    }

    .dropdown-toggle::after {
        display: none;
    }

    span :global(svg) {
        border-radius: inherit;
        vertical-align: var(--badge-align, -0.125rem);
    }
</style>
