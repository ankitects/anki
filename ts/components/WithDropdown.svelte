<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Dropdown from "bootstrap/js/dist/dropdown";

    import { setContext, onDestroy } from "svelte";
    import { dropdownKey } from "./context-keys";

    setContext(dropdownKey, {
        dropdown: true,
        "data-bs-toggle": "dropdown",
    });

    let dropdown: Dropdown;

    const noop = () => {};
    function createDropdown(toggle: HTMLElement): Dropdown {
        /* avoid focusing element toggle on menu activation */
        toggle.focus = noop;
        dropdown = new Dropdown(toggle, {} as any);

        return dropdown;
    }

    onDestroy(() => dropdown?.dispose());
</script>

<div class="dropdown">
    <slot {createDropdown} />
</div>

<style lang="scss">
    div {
        display: contents;
    }
</style>
