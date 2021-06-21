<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Dropdown from "bootstrap/js/dist/dropdown";

    import { setContext } from "svelte";
    import { dropdownKey } from "./contextKeys";

    export let disabled = false;

    setContext(dropdownKey, {
        dropdown: true,
        "data-bs-toggle": "dropdown",
        "aria-expanded": "false",
    });

    const menuId = Math.random().toString(36).substring(2);
    let dropdown: Dropdown;

    function activateDropdown(): void {
        if (!disabled) {
            dropdown.toggle();
        }
    }

    /* Normally dropdown and trigger are associated with a
    /* common ancestor with .dropdown class */
    function createDropdown(element: HTMLElement): void {
        /* Prevent focus on menu activation */
        const noop = () => {};
        Object.defineProperty(element, "focus", { value: noop });

        const menu = (element.getRootNode() as Document) /* or shadow root */
            .getElementById(menuId);

        if (!menu) {
            console.log(`Could not find menu "${menuId}" for dropdown menu.`);
        } else {
            dropdown = new Dropdown(element);

            /* Set custom menu without using common element with .dropdown */
            (dropdown as any)._menu = menu;
        }
    }
</script>

<slot {createDropdown} {activateDropdown} {menuId} />
