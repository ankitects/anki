<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { createEventDispatcher } from "svelte";
    import type { DeckOptionsState } from "./lib";
    import type Dropdown from "bootstrap/js/dist/dropdown";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    import LabelButton from "components/LabelButton.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import DropdownDivider from "components/DropdownDivider.svelte";
    import WithDropdown from "components/WithDropdown.svelte";
    import WithShortcut from "components/WithShortcut.svelte";

    const dispatch = createEventDispatcher();

    export let state: DeckOptionsState;

    function commitEditing(): void {
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
    }

    function removeConfig(): void {
        // show pop-up after dropdown has gone away
        setTimeout(() => {
            if (state.defaultConfigSelected()) {
                alert(tr.schedulingTheDefaultConfigurationCantBeRemoved());
                return;
            }
            const msg =
                (state.removalWilLForceFullSync()
                    ? tr.deckConfigWillRequireFullSync() + " "
                    : "") +
                tr.deckConfigConfirmRemoveName({ name: state.getCurrentName() });
            if (confirm(tr.i18n.withCollapsedWhitespace(msg))) {
                try {
                    state.removeCurrentConfig();
                } catch (err) {
                    alert(err);
                }
            }
        }, 100);
    }

    function save(applyToChildDecks: boolean): void {
        commitEditing();
        state.save(applyToChildDecks);
    }

    let dropdown: Dropdown;
</script>

<ButtonGroup>
    <ButtonGroupItem>
        <WithShortcut shortcut={"Control+Enter"} let:createShortcut let:shortcutLabel>
            <LabelButton
                theme="primary"
                on:click={() => save(false)}
                tooltip={shortcutLabel}
                on:mount={(event) => createShortcut(event.detail.button)}
                >{tr.deckConfigSaveButton()}</LabelButton
            >
        </WithShortcut>
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdown let:createDropdown>
            <LabelButton
                on:mount={(event) => (dropdown = createDropdown(event.detail.button))}
                on:click={() => dropdown.toggle()}
            />
            <DropdownMenu>
                <DropdownItem on:click={() => dispatch("add")}
                    >{tr.deckConfigAddGroup()}</DropdownItem
                >
                <DropdownItem on:click={() => dispatch("clone")}
                    >{tr.deckConfigCloneGroup()}</DropdownItem
                >
                <DropdownItem on:click={() => dispatch("rename")}>
                    {tr.deckConfigRenameGroup()}
                </DropdownItem>
                <DropdownItem on:click={removeConfig}
                    >{tr.deckConfigRemoveGroup()}</DropdownItem
                >
                <DropdownDivider />
                <DropdownItem on:click={() => save(true)}>
                    {tr.deckConfigSaveToAllSubdecks()}
                </DropdownItem>
            </DropdownMenu>
        </WithDropdown>
    </ButtonGroupItem>
</ButtonGroup>
