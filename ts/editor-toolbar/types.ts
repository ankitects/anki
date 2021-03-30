import type { SvelteComponent } from "svelte/internal";

export interface ButtonDefinition {
    component: SvelteComponent;
    id?: string;
    className?: string;
    props?: Record<string, string>;
    button: HTMLButtonElement;
    [arg: string]: unknown;
}

export type Buttons = ButtonDefinition | Buttons[];
