<script lang="typescript">
    import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

    export let button: DynamicSvelteComponent;
    export let menuId: string;

    function extend({ className, props, ...rest }: DynamicSvelteComponent): DynamicSvelteComponent {
        return {
            className: `${className} dropdown-toggle`,
            props: {
                "data-bs-toggle": "dropdown",
                "aria-expanded": "false",
                ...props,
            },
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
        const dropdown = new bootstrap.Dropdown(button);
        const menu = button.getRootNode().getElementById(menuId);

        if (!menu) {
            console.log(`Could not find menu "${menuId}" for dropdown menu.`);
        }

        dropdown._menu = menu;
    }
</script>

<svelte:component
    this={button.component}
    {...extend(button)}
    on:mount={createDropdown} />
