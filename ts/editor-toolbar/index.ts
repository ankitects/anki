import type { SvelteComponent } from "svelte";
import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

import type ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import { writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName } from "anki/i18n";

import { notetypeGroup } from "./notetype";
import { formatGroup } from "./format";
import { colorGroup } from "./color";
import { templateGroup, templateMenus } from "./template";

// @ts-expect-error
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";

const defaultButtons = [notetypeGroup, formatGroup, colorGroup, templateGroup];
const defaultMenus = [...templateMenus];

interface Hideable {
    hidden?: boolean;
}

function searchByIdOrIndex<T extends { id?: string }>(
    values: T[],
    idOrIndex: string | number
): T | undefined {
    return typeof idOrIndex === "string"
        ? values.find((value) => value.id === idOrIndex)
        : values[idOrIndex];
}

function hideComponent(component: Hideable) {
    component.hidden = false;
}

function showComponent(component: Hideable) {
    component.hidden = true;
}

function toggleComponent(component: Hideable) {
    component.hidden = !component.hidden;
}

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;

    buttons = writable(defaultButtons);
    menus = writable(defaultMenus);

    connectedCallback(): void {
        setupI18n({ modules: [ModuleName.EDITING] }).then(() => {
            this.component = new EditorToolbarSvelte({
                target: this,
                props: {
                    buttons: this.buttons,
                    menus: this.menus,
                    nightMode: checkNightMode(),
                },
            });
        });
    }

    updateButtonGroup<T>(
        update: (component: DynamicSvelteComponent<typeof ButtonGroup> & T) => void,
        group: string | number
    ): void {
        this.buttons.update(
            (
                buttonGroups: (DynamicSvelteComponent<typeof ButtonGroup> &
                    ButtonGroupProps)[]
            ) => {
                const foundGroup = searchByIdOrIndex(buttonGroups, group);

                if (foundGroup) {
                    update(
                        foundGroup as DynamicSvelteComponent<typeof ButtonGroup> &
                            ButtonGroupProps &
                            T
                    );
                }

                return buttonGroups;
            }
        );
    }

    showButtonGroup(group: string | number): void {
        this.updateButtonGroup<Hideable>(showComponent, group);
    }

    hideButtonGroup(group: string | number): void {
        this.updateButtonGroup<Hideable>(hideComponent, group);
    }

    toggleButtonGroup(group: string | number): void {
        this.updateButtonGroup<Hideable>(toggleComponent, group);
    }

    updateButton<T>(
        update: (component: DynamicSvelteComponent & T) => void,
        group: string | number,
        button: string | number
    ): void {
        this.updateButtonGroup(
            (
                foundGroup: DynamicSvelteComponent<typeof ButtonGroup> &
                    ButtonGroupProps
            ) => {
                const foundButton = searchByIdOrIndex(
                    foundGroup.buttons as (DynamicSvelteComponent & { id?: string })[],
                    button
                );

                if (foundButton) {
                    update(foundButton as DynamicSvelteComponent & T);
                }
            },
            group
        );
    }

    showButton(group: string | number, button: string | number): void {
        this.updateButton<Hideable>(showComponent, group, button);
    }

    hideButton(group: string | number, button: string | number): void {
        this.updateButton<Hideable>(hideComponent, group, button);
    }

    toggleButton(group: string | number, button: string | number): void {
        this.updateButton<Hideable>(toggleComponent, group, button);
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);

/* Exports for add-ons */
export { default as LabelButton } from "./LabelButton.svelte";
export { default as IconButton } from "./IconButton.svelte";
export { default as CommandIconButton } from "./CommandIconButton.svelte";
export { default as SelectButton } from "./SelectButton.svelte";

export { default as DropdownMenu } from "./DropdownMenu.svelte";
export { default as DropdownItem } from "./DropdownItem.svelte";
export { default as ButtonDropdown } from "./DropdownMenu.svelte";
export { default as WithDropdownMenu } from "./WithDropdownMenu.svelte";
