import { bridgeCommand } from "anki/bridgecommand";
import { lazyLoaded } from "anki/lazy";
import * as tr from "anki/i18n";
import LabelButton from "./LabelButton.svelte";

export const fieldsButton = {
    component: LabelButton,
    onClick: () => bridgeCommand("fields"),
    disables: false,
};

lazyLoaded(fieldsButton, {
    label: () => `${tr.editingFields()}...`,
    title: tr.editingCustomizeFields,
});

export const cardsButton = {
    component: LabelButton,
    onClick: () => bridgeCommand("cards"),
    disables: false,
};

lazyLoaded(cardsButton, {
    label: () => `${tr.editingCards()}...`,
    title: tr.editingCustomizeCardTemplatesCtrlandl,
});
