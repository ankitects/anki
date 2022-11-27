<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { withCollapsedWhitespace } from "@tslib/i18n";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher, tick } from "svelte";
    
    import DropdownDivider from "../components/DropdownDivider.svelte";
    import DropdownItem from "../components/DropdownItem.svelte";
    import LabelButton from "../components/IconButton.svelte";
    import IconButton from "../components/IconButton.svelte";
    import Popover from "../components/Popover.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import WithFloating from "../components/WithFloating.svelte";
    import { chevronDown } from "./icons";
    import type { DeckOptionsState } from "./lib";

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

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

<LabelButton
    primary
    on:click={() => save(false)}
    tooltip={getPlatformString(saveKeyCombination)}
    --border-left-radius={!rtl ? "var(--border-radius)" : "0"}
    --border-right-radius={rtl ? "var(--border-radius)" : "0"}
>
    <div class="save">{tr.deckConfigSaveButton()}</div>
</LabelButton>
<Shortcut keyCombination={saveKeyCombination} on:action={() => save(false)} />

<WithFloating
    show={showFloating}
    closeOnInsideClick
    inline
    on:close={() => (showFloating = false)}
>
    <IconButton
        class="chevron"
        slot="reference"
        on:click={() => (showFloating = !showFloating)}
        --border-right-radius={!rtl ? "var(--border-radius)" : "0"}
        --border-left-radius={rtl ? "var(--border-radius)" : "0"}
        iconSize={80}
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
        <DropdownItem on:click={removeConfig}>{tr.deckConfigRemoveGroup()}</DropdownItem
        >
        <DropdownDivider />
        <DropdownItem on:click={() => save(true)}>
            {tr.deckConfigSaveToAllSubdecks()}
        </DropdownItem>
    </Popover>
</WithFloating>

<style lang="scss">
    .save {
        margin: 0.2rem 0.75rem;
    }
</style>
