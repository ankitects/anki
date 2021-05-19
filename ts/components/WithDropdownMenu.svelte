<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Dropdown from "bootstrap/js/dist/dropdown";

    import { setContext } from "svelte";
    import { dropdownKey } from "./contextKeys";

    setContext(dropdownKey, {
        dropdown: true,
        "data-bs-toggle": "dropdown",
        "aria-expanded": "false",
    });

    const menuId = Math.random().toString(36).substring(2);
    let dropdown: Dropdown;

    function activateDropdown(_event: MouseEvent): void {
        dropdown.toggle();
    }

    /* Normally dropdown and trigger are associated with a
    /* common ancestor with .dropdown class */
    function createDropdown(event: CustomEvent): void {
        const button: HTMLButtonElement = event.detail.button;

        /* Prevent focus on menu activation */
        const noop = () => {};
        Object.defineProperty(button, "focus", { value: noop });

        const menu = (button.getRootNode() as Document) /* or shadow root */
            .getElementById(menuId);

        if (!menu) {
            console.log(`Could not find menu "${menuId}" for dropdown menu.`);
        } else {
            dropdown = new Dropdown(button);

            /* Set custom menu without using common element with .dropdown */
            (dropdown as any)._menu = menu;
        }
    }
</script>

<slot {createDropdown} {activateDropdown} {menuId} />
