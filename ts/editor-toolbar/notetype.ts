import LabelButton from "./LabelButton.svelte";
import type { LabelButtonProps } from "./LabelButton";

import { dynamicComponent } from "sveltelib/dynamicComponent";
import { bridgeCommand } from "anki/bridgecommand";
import * as tr from "anki/i18n";

const labelButton = dynamicComponent(LabelButton);
const fieldsButton = labelButton<LabelButtonProps, "label" | "tooltip">(
    {
        onClick: () => bridgeCommand("fields"),
        disables: false,
    },
    {
        label: () => `${tr.editingFields()}...`,
        tooltip: tr.editingCustomizeFields,
    }
);

const cardsButton = labelButton<LabelButtonProps, "label" | "tooltip">(
    {
        onClick: () => bridgeCommand("cards"),
        disables: false,
    },
    {
        label: () => `${tr.editingCards()}...`,
        tooltip: tr.editingCustomizeCardTemplatesCtrlandl,
    }
);

export const notetypeButtons = [fieldsButton, cardsButton];
