<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import DeckOptionsPage from "../DeckOptionsPage.svelte";
    import { commitEditing } from "../lib";
    import type { PageData } from "./$types";
    import { bridgeCommand, bridgeCommandsAvailable } from "@tslib/bridgecommand";

    export let data: PageData;
    let page: DeckOptionsPage;

    globalThis.anki ||= {};
    globalThis.anki.deckOptionsPendingChanges = async (): Promise<void> => {
        await commitEditing();
        if (bridgeCommandsAvailable()) {
            if (await data.state.isModified()) {
                bridgeCommand("confirmDiscardChanges");
            } else {
                bridgeCommand("_close");
            }
        }
    };

    onMount(() => {
        globalThis.$deckOptions = new Promise((resolve, _reject) => {
            resolve(page);
        });
        data.state.resolveOriginalConfigs();
        if (bridgeCommandsAvailable()) {
            bridgeCommand("deckOptionsReady");
        }
    });
</script>

<DeckOptionsPage state={data.state} bind:this={page} />
