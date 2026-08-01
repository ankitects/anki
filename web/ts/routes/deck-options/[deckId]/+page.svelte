<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import { onMount } from "svelte";
    import DeckOptionsPage from "../DeckOptionsPage.svelte";
    import { commitEditing } from "../lib";
    import type { PageData } from "./$types";
    import { deckOptionsRequireClose, deckOptionsReady } from "@generated/backend";

    export let data: PageData;
    let page: DeckOptionsPage;

    globalThis.anki ||= {};
    globalThis.anki.deckOptionsPendingChanges = async (): Promise<void> => {
        await commitEditing();
        if (
            !(await data.state.isModified()) ||
            confirm(tr.cardTemplatesDiscardChanges())
        ) {
            // Either there was no change, or the user accepted to discard the changes.
            deckOptionsRequireClose({});
        }
    };

    onMount(() => {
        globalThis.$deckOptions = new Promise((resolve, _reject) => {
            resolve(page);
        });
        data.state.resolveOriginalConfigs();
        deckOptionsReady({});
    });
</script>

<DeckOptionsPage state={data.state} bind:this={page} />
