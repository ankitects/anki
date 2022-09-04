<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { setContext } from "svelte";

    import { dropdownKey } from "./context-keys";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let labelledby: string | undefined = undefined;
    export let show = false;

    setContext(dropdownKey, null);
</script>

<div
    {id}
    class="dropdown-menu"
    class:show
    aria-labelledby={labelledby}
    on:mousedown|preventDefault|stopPropagation
>
    <div class="dropdown-content {className}">
        <slot />
    </div>
</div>

<style lang="scss">
    .dropdown-menu {
        border-radius: 5px;
        background-color: var(--canvas-elevated);
        border-color: var(--border-default);
        min-width: 1rem;
    }
</style>
