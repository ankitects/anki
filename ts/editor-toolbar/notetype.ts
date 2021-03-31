import { bridgeCommand } from "anki/bridgecommand";
import { lazyProperties } from "anki/lazy";
import * as tr from "anki/i18n";
import LabelButton from "./LabelButton.svelte";

const fieldsButton = {
    component: LabelButton,
    onClick: () => bridgeCommand("fields"),
    disables: false,
};

lazyProperties(fieldsButton, {
    label: () => `${tr.editingFields()}...`,
    title: tr.editingCustomizeFields,
});

const cardsButton = {
    component: LabelButton,
    onClick: () => bridgeCommand("cards"),
    disables: false,
};

lazyProperties(cardsButton, {
    label: () => `${tr.editingCards()}...`,
    title: tr.editingCustomizeCardTemplatesCtrlandl,
});

export const notetypeButtons = [fieldsButton, cardsButton];
