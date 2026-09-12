<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { page } from "$app/state";

    import CardInfo from "../../CardInfo.svelte";
    import type { PageData } from "./$types";
    import { goto, invalidate } from "$app/navigation";

    export let data: PageData;

    const showRevlog = page.url.searchParams.get("revlog") !== "0";
    const showCurve = page.url.searchParams.get("curve") !== "0";

    globalThis.anki ||= {};
    globalThis.anki.updateCardInfos = async (card_id: string): Promise<void> => {
        const path = `/card-info/${card_id}`;
        if (page.params.cardId === card_id && !page.params.previousId) {
            await invalidate("anki:card-info");
        } else {
            await goto(path).catch(() => {
                window.location.href = path;
            });
        }
    };
</script>

<center>
    {#if data.currentInfo}
        <h3>Current</h3>
        <CardInfo stats={data.currentInfo} {showRevlog} {showCurve} />
    {/if}
    {#if data.previousInfo}
        <h3>Previous</h3>
        <CardInfo stats={data.previousInfo} {showRevlog} {showCurve} />
    {/if}
</center>

<style>
    :global(body) {
        font-size: 80%;
    }
</style>
