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
    let dropdown: Dropdown | undefined;

    function activateDropdown(): void {
        if (dropdown && !disabled) {
            dropdown.toggle();
        }
    }

    function isVisible(): boolean {
        return (dropdown as any)._menu.classList.contains("show");
    }

    /* Normally dropdown and trigger are associated with a
    /* common ancestor with .dropdown class */
    function createDropdown(element: HTMLElement): Dropdown {
        /* Prevent focus on menu activation */
        const noop = () => {};
        Object.defineProperty(element, "focus", { value: noop, configurable: true });

        const menu = (element.getRootNode() as Document | ShadowRoot).getElementById(
            menuId
        );

        if (!menu) {
            console.log(`Could not find menu "${menuId}" for dropdown menu.`);
        } else {
            dropdown = new Dropdown(element);

            /* Set custom menu without using common element with .dropdown */
            (dropdown as any)._menu = menu;
            Object.defineProperty(dropdown, "isVisible", { value: isVisible });
        }

        return dropdown as Dropdown;
    }
</script>

<slot {createDropdown} {activateDropdown} {menuId} />
