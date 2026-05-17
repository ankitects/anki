<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import ActionButton from "./ActionButton.svelte";
    import type { HistoryEntry } from "./types";
    import Icon from "$lib/components/Icon.svelte";
    import { caretDownFill } from "$lib/components/icons";
    import { isApplePlatform } from "@tslib/platform";

    export let onHistory: () => void;
    export let history: HistoryEntry[] = [];
    const historyKeyCombination = isApplePlatform() ? "Control+Shift+H" : "Control+H";
</script>

<ActionButton
    onClick={onHistory}
    tooltip={getPlatformString(historyKeyCombination)}
    disabled={history.length === 0}
>
    {tr.addingHistory()}
    <Icon icon={caretDownFill} />
    <Shortcut keyCombination={historyKeyCombination} on:action={onHistory} />
</ActionButton>
