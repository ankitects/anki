<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Dropdown from "bootstrap/js/dist/dropdown";

    import { setContext } from "svelte";
    import { dropdownKey } from "./contextKeys";

    setContext(dropdownKey, {
        getDropdownTriggerProps: () => ({
            "data-bs-toggle": "dropdown",
            "aria-expanded": "false",
        }),
    });

    const menuId = Math.random().toString(36).substring(2);

    /* Normally dropdown and trigger are associated with a
    /* common ancestor with .dropdown class */
    function createDropdown(button: HTMLElement): void {
        /* Prevent focus on menu activation */
        const noop = () => {};
        Object.defineProperty(button, "focus", { value: noop });

        /* Set custom menu without using .dropdown
         * Rendering the menu here would cause the menu to
         * be displayed outside of the visible area */

        const dropdown = new Dropdown(button);

        const menu = (button.getRootNode() as Document) /* or shadow root */
            .getElementById(menuId);

        if (!menu) {
            console.log(`Could not find menu "${menuId}" for dropdown menu.`);
        }

        (dropdown as any)._menu = menu;
    }
</script>

<slot {createDropdown} {menuId} />
