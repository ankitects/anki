<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type Dropdown from "bootstrap/js/dist/dropdown";
    import { createEventDispatcher } from "svelte";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import DropdownDivider from "../components/DropdownDivider.svelte";
    import DropdownItem from "../components/DropdownItem.svelte";
    import DropdownMenu from "../components/DropdownMenu.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import WithDropdown from "../components/WithDropdown.svelte";
    import * as tr from "../lib/ftl";
    import { withCollapsedWhitespace } from "../lib/i18n";
    import { getPlatformString } from "../lib/shortcuts";
    import type { DeckOptionsState } from "./lib";

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
            if (confirm(withCollapsedWhitespace(msg))) {
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
    const saveKeyCombination = "Control+Enter";
</script>

<ButtonGroup>
    <LabelButton
        theme="primary"
        on:click={() => save(false)}
        tooltip={getPlatformString(saveKeyCombination)}
        --border-left-radius="5px">{tr.deckConfigSaveButton()}</LabelButton
    >
    <Shortcut keyCombination={saveKeyCombination} on:click={() => save(false)} />

    <WithDropdown let:createDropdown --border-right-radius="5px">
        <LabelButton
            on:click={() => dropdown.toggle()}
            on:mount={(event) => (dropdown = createDropdown(event.detail.button))}
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
</ButtonGroup>
