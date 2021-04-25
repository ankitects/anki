import * as tr from "lib/i18n";
import { textInputModal } from "./textInputModal";
import type { DeckOptionsState } from "./lib";
import {
    DynamicSvelteComponent,
    labelButton,
    buttonGroup,
    dropdownMenu,
    dropdownItem,
    dropdownDivider,
} from "sveltelib/dynamicComponents";

export function getOptionsDropdown(state: DeckOptionsState): DynamicSvelteComponent {
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

    return buttonGroup({
        id: "optionsDropdown",
        size: 35,
        items: [
            labelButton({
                label: "Save",
                className: "btn-primary",
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
    });
}
