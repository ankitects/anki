<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { HelpPage } from "@tslib/help-page";
    import ActionButton from "./ActionButton.svelte";
    import { getPlatformString } from "@tslib/shortcuts";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import { openLink } from "@generated/backend";

    const helpKeyCombination = "F1";

    function onClick() {
        openLink({ val: HelpPage.Editing.adding });
    }
</script>

<ActionButton tooltip={getPlatformString(helpKeyCombination)} {onClick}>
    {tr.actionsHelp()}
    <Shortcut keyCombination={helpKeyCombination} on:action={onClick} />
</ActionButton>
