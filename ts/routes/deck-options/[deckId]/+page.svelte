<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import DeckOptionsPage from "../DeckOptionsPage.svelte";
    import type { PageData } from "./$types";
    import { bridgeCommand, bridgeCommandsAvailable } from "@tslib/bridgecommand";

    export let data: PageData;
    let page: DeckOptionsPage;

    globalThis.anki ||= {};
    globalThis.anki.deckOptionsPendingChanges = () => {
        if (data.state.isModified()) {
            return true;
        }
        // The state does not know of any modified data.
        // Still, the user may have edited the content of an input field. This would not have updated the state until the focus left the field.
        // Forcing the loss of focus, a.k.a. `blur` requires a change in the DOM, and thus is async.
        // Instead, we'll assume that if any HTMLElement is currently selected, there may have been a change.
        return document.activeElement instanceof HTMLInputElement;
    };
    onMount(() => {
        globalThis.$deckOptions = new Promise((resolve, _reject) => {
            resolve(page);
        });
        if (bridgeCommandsAvailable()) {
            bridgeCommand("deckOptionsReady");
        }
    });
</script>

<DeckOptionsPage state={data.state} bind:this={page} />
