<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DropdownProps } from "components/dropdown";
    import { dropdownKey } from "components/context-keys";
    import { onMount, createEventDispatcher, getContext } from "svelte";

    let className = "";
    export { className as class };

    const dispatch = createEventDispatcher();

    let spanRef: HTMLSpanElement;

    const dropdownProps = getContext<DropdownProps>(dropdownKey) ?? { dropdown: false };

    onMount(() => {
        dispatch("mount", { span: spanRef });
    });
</script>

<span
    bind:this={spanRef}
    class={`badge ${className}`}
    class:dropdown-toggle={dropdownProps.dropdown}
    {...dropdownProps}
    on:click
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
        vertical-align: -0.125rem;
    }
</style>
