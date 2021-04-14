import type { SvelteComponentDev } from "svelte/internal";
import type { ToolbarItem } from "./types";

import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import { dynamicComponent } from "sveltelib/dynamicComponent";
import { Writable, writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import { setupI18n, ModuleName } from "anki/i18n";

import "./bootstrap.css";

import { getNotetypeGroup } from "./notetype";
import { getFormatGroup } from "./format";
import { getColorGroup } from "./color";
import { getTemplateGroup, getTemplateMenus } from "./template";
import { Identifiable, search, add, insert } from "./identifiable";

interface Hideable {
    hidden?: boolean;
}

function showComponent(component: Hideable) {
    component.hidden = false;
}

function hideComponent(component: Hideable) {
    component.hidden = true;
}

function toggleComponent(component: Hideable) {
    component.hidden = !component.hidden;
}

const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);

class EditorToolbar extends HTMLElement {
    component?: SvelteComponentDev;

    buttons?: Writable<(ToolbarItem<typeof ButtonGroup> & ButtonGroupProps)[]>;
    menus?: Writable<ToolbarItem[]>;

    connectedCallback(): void {
        setupI18n({ modules: [ModuleName.EDITING] }).then(() => {
            this.buttons = writable([
                getNotetypeGroup(),
                getFormatGroup(),
                getColorGroup(),
                getTemplateGroup(),
            ]);
            this.menus = writable([...getTemplateMenus()]);

            this.component = new EditorToolbarSvelte({
                target: this,
                props: {
                    buttons: this.buttons,
                    menus: this.menus,
                    nightMode: document.documentElement.classList.contains("night-mode"),

                },
            });
        });
    }

    updateButtonGroup<T>(
        update: (
            component: ToolbarItem<typeof ButtonGroup> & ButtonGroupProps & T
        ) => void,
        group: string | number
    ): void {
        this.buttons?.update((buttonGroups) => {
            const foundGroup = search(buttonGroups, group);

            if (foundGroup) {
                update(
                    foundGroup as ToolbarItem<typeof ButtonGroup> & ButtonGroupProps & T
                );
            }

            return buttonGroups;
        });
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

    insertButtonGroup(newGroup: ButtonGroupProps, group: string | number = 0) {
        this.buttons?.update((buttonGroups) => {
            const newButtonGroup = buttonGroup(newGroup);
            return insert(buttonGroups, newButtonGroup, group);
        });
    }

    addButtonGroup(newGroup: ButtonGroupProps, group: string | number = -1) {
        this.buttons?.update((buttonGroups) => {
            const newButtonGroup = buttonGroup(newGroup);
            return add(buttonGroups, newButtonGroup, group);
        });
    }

    updateButton(
        update: (component: ToolbarItem) => void,
        group: string | number,
        button: string | number
    ): void {
        this.updateButtonGroup((foundGroup) => {
            const foundButton = search(foundGroup.buttons, button);

            if (foundButton) {
                update(foundButton);
            }
        }, group);
    }

    showButton(group: string | number, button: string | number): void {
        this.updateButton(showComponent, group, button);
    }

    hideButton(group: string | number, button: string | number): void {
        this.updateButton(hideComponent, group, button);
    }

    toggleButton(group: string | number, button: string | number): void {
        this.updateButton(toggleComponent, group, button);
    }

    insertButton(
        newButton: ToolbarItem & Identifiable,
        group: string | number,
        button: string | number = 0
    ) {
        this.updateButtonGroup((component) => {
            component.buttons = insert(
                component.buttons as (ToolbarItem & Identifiable)[],
                newButton,
                button
            );
        }, group);
    }

    addButton(
        newButton: ToolbarItem & Identifiable,
        group: string | number,
        button: string | number = -1
    ) {
        this.updateButtonGroup((component) => {
            component.buttons = add(
                component.buttons as (ToolbarItem & Identifiable)[],
                newButton,
                button
            );
        }, group);
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);

/* Exports for editor
 * @ts-expect-error */
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";

/* Exports for add-ons */
export { default as RawButton } from "./RawButton.svelte";
export { default as LabelButton } from "./LabelButton.svelte";
export { default as IconButton } from "./IconButton.svelte";
export { default as CommandIconButton } from "./CommandIconButton.svelte";
export { default as SelectButton } from "./SelectButton.svelte";

export { default as DropdownMenu } from "./DropdownMenu.svelte";
export { default as DropdownItem } from "./DropdownItem.svelte";
export { default as ButtonDropdown } from "./DropdownMenu.svelte";
export { default as WithDropdownMenu } from "./WithDropdownMenu.svelte";
