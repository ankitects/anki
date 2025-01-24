<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { page } from "$app/stores";

    import CardInfo from "../../CardInfo.svelte";
    import type { PageData } from "./$types";
    import { goto } from "$app/navigation";

    export let data: PageData;

    const showRevlog = $page.url.searchParams.get("revlog") !== "0";

    globalThis.anki ||= {};
    globalThis.anki.updateCardInfos = async (card_id: string): Promise<void> => {
        const path = `/card-info/${card_id}`;
        return goto(path).catch(() => {
            window.location.href = path;
        });
    };
</script>

<center>
    {#if data.currentInfo}
        <h3>Current</h3>
        <CardInfo stats={data.currentInfo} {showRevlog} />
    {/if}
    {#if data.previousInfo}
        <h3>Previous</h3>
        <CardInfo stats={data.previousInfo} {showRevlog} />
    {/if}
</center>

<style>
    :global(body) {
        font-size: 80%;
    }
</style>
