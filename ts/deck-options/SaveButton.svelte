<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, tick } from "svelte";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import DropdownDivider from "../components/DropdownDivider.svelte";
    import DropdownItem from "../components/DropdownItem.svelte";
    import IconButton from "../components/IconButton.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Popover from "../components/Popover.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import WithFloating from "../components/WithFloating.svelte";
    import * as tr from "../lib/ftl";
    import { withCollapsedWhitespace } from "../lib/i18n";
    import { getPlatformString } from "../lib/shortcuts";
    import { chevronDown } from "./icons";
    import type { DeckOptionsState } from "./lib";

    const dispatch = createEventDispatcher();

    export let state: DeckOptionsState;

    function commitEditing(): void {
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
    }

    async function removeConfig(): Promise<void> {
        // show pop-up after dropdown has gone away
        await tick();

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
                dispatch("remove");
            } catch (err) {
                alert(err);
            }
        }
    }

    function save(applyToChildDecks: boolean): void {
        commitEditing();
        state.save(applyToChildDecks);
    }

    const saveKeyCombination = "Control+Enter";

    let showFloating = false;
</script>

<ButtonGroup>
    <LabelButton
        theme="primary"
        on:click={() => save(false)}
        tooltip={getPlatformString(saveKeyCombination)}
        >{tr.deckConfigSaveButton()}</LabelButton
    >
    <Shortcut keyCombination={saveKeyCombination} on:action={() => save(false)} />

    <WithFloating
        show={showFloating}
        closeOnInsideClick
        inline
        on:close={() => (showFloating = false)}
    >
        <IconButton
            slot="reference"
            widthMultiplier={0.5}
            iconSize={120}
            on:click={() => (showFloating = !showFloating)}
        >
            {@html chevronDown}
        </IconButton>

        <Popover slot="floating">
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
        </Popover>
    </WithFloating>
</ButtonGroup>
