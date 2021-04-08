import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

export interface WithDropdownMenuProps {
    button: DynamicSvelteComponent;
    menuId: string;
}
