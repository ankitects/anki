<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Dropdown from "bootstrap/js/dist/dropdown";

    import { setContext, onDestroy } from "svelte";
    import { dropdownKey } from "./context-keys";

    export let autoOpen = false;
    export let autoClose: boolean | "inside" | "outside" = true;

    export let placement = "bottom-start";

    setContext(dropdownKey, {
        dropdown: true,
        "data-bs-toggle": "dropdown",
    });

    let dropdown: Dropdown;
    let dropdownObject: Dropdown;

    const noop = () => {};
    function createDropdown(toggle: HTMLElement): Dropdown {
        /* avoid focusing element toggle on menu activation */
        toggle.focus = noop;
        dropdown = new Dropdown(toggle, {
            autoClose,
            popperConfig: (defaultConfig: Record<string, any>) => ({
                ...defaultConfig,
                placement,
            }),
        } as any);

        if (autoOpen) {
            dropdown.show();
        }

        dropdownObject = {
            show: dropdown.show.bind(dropdown),
            toggle: dropdown.toggle.bind(dropdown),
            hide: dropdown.hide.bind(dropdown),
            update: dropdown.update.bind(dropdown),
            dispose: dropdown.dispose.bind(dropdown),
        };

        return dropdownObject;
    }

    onDestroy(() => dropdown?.dispose());
</script>

<div class="dropdown">
    <slot {createDropdown} {dropdownObject} />
</div>

<style lang="scss">
    div {
        display: contents;
    }
</style>
