import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

export interface DropdownMenuProps {
    id: string;
    menuItems: DynamicSvelteComponent[];
}
