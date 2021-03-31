import type { SvelteComponent } from "svelte/internal";

export interface SvelteComponentDefinition {
    component: SvelteComponent;
    [arg: string]: unknown;
}

export interface ButtonDefinition extends SvelteComponentDefinition {
    id?: string;
    className?: string;
    props?: Record<string, string>;
    button: HTMLButtonElement;
}

export type Buttons = ButtonDefinition | Buttons[];
