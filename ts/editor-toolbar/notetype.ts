import LabelButton from "./LabelButton.svelte";
import type { LabelButtonProps } from "./LabelButton";
import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import { dynamicComponent } from "sveltelib/dynamicComponent";
import { bridgeCommand } from "anki/bridgecommand";
import * as tr from "anki/i18n";

const labelButton = dynamicComponent<typeof LabelButton, LabelButtonProps>(LabelButton);
const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);

export function getNotetypeGroup() {
    const fieldsButton = labelButton({
        onClick: () => bridgeCommand("fields"),
        disables: false,
        label: `${tr.editingFields()}...`,
        tooltip: tr.editingCustomizeFields(),
    });

    const cardsButton = labelButton({
        onClick: () => bridgeCommand("cards"),
        disables: false,
        label: `${tr.editingCards()}...`,
        tooltip: tr.editingCustomizeCardTemplatesCtrlandl(),
    });

    return buttonGroup({
        id: "notetype",
        buttons: [fieldsButton, cardsButton],
    });
}
