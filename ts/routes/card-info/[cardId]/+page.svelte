<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { page } from "$app/stores";

    import CardInfo from "../CardInfo.svelte";
    import type { PageData } from "./$types";
    import { goto } from "$app/navigation";

    export let data: PageData;

    const showRevlog = $page.url.searchParams.get("revlog") !== "0";

    function updateCardId(evt: MessageEvent) {
        goto(`/card-info/${evt.data}`);
    }
</script>

<!-- used by CardInfoDialog.update_card -->
<svelte:window on:message={updateCardId} />

<CardInfo stats={data.info} {showRevlog} />
