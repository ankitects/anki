import LabelButton from "./LabelButton.svelte";

import { bridgeCommand } from "anki/bridgecommand";
import { withLazyProperties } from "anki/lazy";
import * as tr from "anki/i18n";

const fieldsButton = withLazyProperties(
    {
        component: LabelButton,
        onClick: () => bridgeCommand("fields"),
        disables: false,
    },
    {
        label: () => `${tr.editingFields()}...`,
        title: tr.editingCustomizeFields,
    }
);

const cardsButton = withLazyProperties(
    {
        component: LabelButton,
        onClick: () => bridgeCommand("cards"),
        disables: false,
    },
    {
        label: () => `${tr.editingCards()}...`,
        title: tr.editingCustomizeCardTemplatesCtrlandl,
    }
);

export const notetypeButtons = [fieldsButton, cardsButton];
