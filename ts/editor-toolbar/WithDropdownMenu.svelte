<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";
    import type { ToolbarItem } from "./types";

    import Dropdown from "bootstrap/js/dist/dropdown";

    /* Bootstrap dropdown are normally declared alongside the associated button
     * However we cannot do that, as the menus cannot be declared in sticky-positioned elements
     */
    export let button: ToolbarItem;
    export let menuId: string;

    function extend({
        className,
        ...rest
    }: DynamicSvelteComponent): DynamicSvelteComponent {
        return {
            dropdownToggle: true,
            ...rest,
        };
    }

    function createDropdown({ detail }: CustomEvent): void {
        const button: HTMLButtonElement = detail.button;

        /* Prevent focus on menu activation */
        const noop = () => {};
        Object.defineProperty(button, "focus", { value: noop });

        /* Set custom menu without using .dropdown
         * Rendering the menu here would cause the menu to
         * be displayed outside of the visible area
         */
        const dropdown = new Dropdown(button);
        const menu = (button.getRootNode() as Document) /* or shadow root */
            .getElementById(menuId);

        if (!menu) {
            console.log(`Could not find menu "${menuId}" for dropdown menu.`);
        }

        (dropdown as any)._menu = menu;
    }
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createDropdown} />
