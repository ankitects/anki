<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { textInputModal } from "./textInputModal";
    import type { DeckOptionsState } from "./lib";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    import LabelButton from "components/LabelButton.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import DropdownDivider from "components/DropdownDivider.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";

    export let state: DeckOptionsState;

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
</script>

<ButtonGroup>
    <ButtonGroupItem>
        <LabelButton theme="primary" on:click={() => save(false)}>Save</LabelButton>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdownMenu let:createDropdown let:menuId>
            <LabelButton on:mount={createDropdown} />
            <DropdownMenu id={menuId}>
                <DropdownItem on:click={addConfig}>Add Config</DropdownItem>
                <DropdownItem on:click={renameConfig}>Rename Config</DropdownItem>
                <DropdownItem on:click={removeConfig}>Remove Config</DropdownItem>
                <DropdownDivider />
                <DropdownItem on:click={() => save(true)}>
                    Save to All Children
                </DropdownItem>
            </DropdownMenu>
        </WithDropdownMenu>
    </ButtonGroupItem>
</ButtonGroup>
