import * as tr from "lib/i18n";
import { textInputModal } from "./textInputModal";
import type { DeckOptionsState, ConfigListEntry } from "./lib";
import {
    DynamicSvelteComponent,
    labelButton,
    buttonGroup,
    dropdownMenu,
    dropdownItem,
    dropdownDivider,
    selectButton,
} from "sveltelib/dynamicComponents";

function configLabel(entry: ConfigListEntry): string {
    const count = tr.deckConfigUsedByDecks({ decks: entry.useCount });
    return `${entry.name} (${count})`;
}

export function getOptionsDropdown(
    state: DeckOptionsState,
    configList: ConfigListEntry[]
): DynamicSvelteComponent {
    function addConfig(): void {
        textInputModal({
            title: "Add Config",
            prompt: "Name:",
            onOk: (text: string) => {
                const trimmed = text.trim();
                if (trimmed.length) {
                    state.addConfig(trimmed);
                }
            },
        });
    }

    function renameConfig(): void {
        textInputModal({
            title: "Rename Config",
            prompt: "Name:",
            startingValue: state.getCurrentName(),
            onOk: (text: string) => {
                state.setCurrentName(text);
            },
        });
    }

    function removeConfig(): void {
        // show pop-up after dropdown has gone away
        setTimeout(() => {
            if (state.defaultConfigSelected()) {
                alert(tr.schedulingTheDefaultConfigurationCantBeRemoved());
                return;
            }
            // fixme: move tr.qt_misc schema mod msg into core
            // fixme: include name of deck in msg
            const msg = state.removalWilLForceFullSync()
                ? "This will require a one-way sync. Are you sure?"
                : "Are you sure?";
            if (confirm(msg)) {
                try {
                    state.removeCurrentConfig();
                } catch (err) {
                    alert(err);
                }
            }
        }, 100);
    }

    function save(applyToChildDecks: boolean): void {
        state.save(applyToChildDecks);
    }

    function blur(this: HTMLSelectElement) {
        state.setCurrentIndex(parseInt(this.value));
    }

    const options = configList.map((entry) => ({
        value: entry.idx,
        selected: entry.current,
        label: configLabel(entry),
    }));

    return buttonGroup({
        id: "configSelector",
        className: "justify-content-between",
        size: 35,
        items: [
            buttonGroup({
                className: "flex-basis-75",
                items: [
                    selectButton({
                        options,
                        className: "flex-basis-100",
                        onChange: blur,
                    }),
                ],
            }),
            buttonGroup({
                id: "optionsDropdown",
                items: [
                    labelButton({
                        label: "Save",
                        theme: "primary",
                        onClick: () => save(false),
                    }),
                    labelButton({
                        dropdownToggle: true,
                    }),
                    dropdownMenu({
                        items: [
                            dropdownItem({
                                label: "Add Config",
                                onClick: addConfig,
                            }),
                            dropdownItem({
                                label: "Rename Config",
                                onClick: renameConfig,
                            }),
                            dropdownItem({
                                label: "Remove Config",
                                onClick: removeConfig,
                            }),
                            dropdownDivider({}),
                            dropdownItem({
                                label: "Save to All Children",
                                onClick: () => save(true),
                            }),
                        ],
                    }),
                ],
            }),
        ],
    });
}
