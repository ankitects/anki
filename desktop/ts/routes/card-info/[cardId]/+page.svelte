<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { page } from "$app/state";

    import CardInfo from "../CardInfo.svelte";
    import type { PageData } from "./$types";
    import { goto } from "$app/navigation";

    export let data: PageData;

    const showRevlog = page.url.searchParams.get("revlog") !== "0";

    globalThis.anki ||= {};
    globalThis.anki.updateCard = async (card_id: string): Promise<void> => {
        const path = `/card-info/${card_id}`;
        return goto(path).catch(() => {
            window.location.href = path;
        });
    };
</script>

<CardInfo stats={data.info} {showRevlog} />
