<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { dropdownKey } from "./context-keys";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let labelledby: string | undefined = undefined;
    export let show = false;

    setContext(dropdownKey, null);
</script>

<div {id} class="dropdown-menu" class:show aria-labelledby={labelledby}>
    <div class="dropdown-content {className}">
        <slot />
    </div>
</div>

<style lang="scss">
    .dropdown-menu {
        background-color: var(--frame-bg);
        border-color: var(--medium-border);
        min-width: 1rem;
    }
</style>
