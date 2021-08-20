// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export interface DynamicSvelteComponent {
    component: unknown;
    props: Record<string, unknown>;
}

export const dynamicComponent =
    <T>(component: T) =>
    (props: Record<string, unknown>): DynamicSvelteComponent => {
        return { component, props };
    };
