import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

export interface ButtonDefinition extends DynamicSvelteComponent {
    id?: string;
    className?: string;
}

export type Buttons = ButtonDefinition | Buttons[];
