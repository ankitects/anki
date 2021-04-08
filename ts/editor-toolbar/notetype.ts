import LabelButton from "./LabelButton.svelte";
import type { LabelButtonProps } from "./LabelButton";

import { dynamicComponent } from "sveltelib/dynamicComponent";
import { bridgeCommand } from "anki/bridgecommand";
import * as tr from "anki/i18n";

const labelButton = dynamicComponent(LabelButton);
const fieldsButton = labelButton<LabelButtonProps, "label" | "title">(
    {
        onClick: () => bridgeCommand("fields"),
        disables: false,
    },
    {
        label: () => `${tr.editingFields()}...`,
        title: tr.editingCustomizeFields,
    }
);

const cardsButton = labelButton<LabelButtonProps, "label" | "title">(
    {
        onClick: () => bridgeCommand("cards"),
        disables: false,
    },
    {
        label: () => `${tr.editingCards()}...`,
        title: tr.editingCustomizeCardTemplatesCtrlandl,
    }
);

export const notetypeButtons = [fieldsButton, cardsButton];
