<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { ToolbarItem } from "./types";

    export let id: string;
    export let className = "";

    export let label: string;
    export let button: ToolbarItem;
</script>

<!-- svelte-ignore a11y-label-has-associated-control -->

<label {id} class={className}>
    <span class="me-1">{label}</span>
    <svelte:component this={button.component} {...button} />
</label>

<style lang="scss">
    label {
        display: flex;
        padding: 0 calc(var(--buttons-size) / 10);
    }
</style>
