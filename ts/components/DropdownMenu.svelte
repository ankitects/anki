<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { setContext } from "svelte";
    import { dropdownKey } from "./context-keys";

    export let id: string | undefined = undefined;

    setContext(dropdownKey, null);
</script>

<div {id} class="dropdown-menu">
    <slot />
</div>

<style lang="scss">
    div {
        background-color: var(--frame-bg);
        border-color: var(--medium-border);
    }
</style>
