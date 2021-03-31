import { bridgeCommand } from "anki/bridgecommand";
import LabelButton from "./LabelButton.svelte";

export const fieldsButton = {
    component: LabelButton,
    label: "Fields...",
    onClick: () => bridgeCommand("fields"),
    disables: false,
};
export const cardsButton = {
    component: LabelButton,
    label: "Cards...",
    onClick: () => bridgeCommand("cards"),
    disables: false,
};
