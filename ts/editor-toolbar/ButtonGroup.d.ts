import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

export interface ButtonGroupProps {
    id: string;
    className?: string;
    buttons: DynamicSvelteComponent[];
}
