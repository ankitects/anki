<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { createEventDispatcher } from "svelte";
    import type { DeckOptionsState } from "./lib";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";

    import LabelButton from "components/LabelButton.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import DropdownItem from "components/DropdownItem.svelte";
    import DropdownDivider from "components/DropdownDivider.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";

    const dispatch = createEventDispatcher();

    export let state: DeckOptionsState;

    function removeConfig(): void {
        // show pop-up after dropdown has gone away
        setTimeout(() => {
            if (state.defaultConfigSelected()) {
                alert(tr.schedulingTheDefaultConfigurationCantBeRemoved());
                return;
            }
            // fixme: move tr.qt_misc schema mod msg into core
            // fixme: include name of deck in msg
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
        state.save(applyToChildDecks);
    }
</script>

<ButtonGroup>
    <ButtonGroupItem>
        <LabelButton theme="primary" on:click={() => save(false)}
            >{tr.deckConfigSaveButton()}</LabelButton
        >
    </ButtonGroupItem>

    <ButtonGroupItem>
        <WithDropdownMenu let:createDropdown let:activateDropdown let:menuId>
            <LabelButton on:mount={createDropdown} on:click={activateDropdown} />
            <DropdownMenu id={menuId}>
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
        </WithDropdownMenu>
    </ButtonGroupItem>
</ButtonGroup>
