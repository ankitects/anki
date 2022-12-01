<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import type { ChangeNotetypeState } from "./lib";

    export let state: ChangeNotetypeState;

    function save(): void {
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
        state.save();
    }

    const keyCombination = "Control+Enter";
</script>

<ButtonGroup>
    <LabelButton
        primary
        tooltip={getPlatformString(keyCombination)}
        on:click={save}
        --border-left-radius="5px"
        --border-right-radius="5px"
    >
        <div class="save">{tr.actionsSave()}</div>
    </LabelButton>
    <Shortcut {keyCombination} on:action={save} />
</ButtonGroup>

<style lang="scss">
    .save {
        margin: 0.15rem 0.75rem;
    }
</style>
