<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { page } from "$app/stores";
    import { enhance } from "$app/forms";

    import CardInfo from "../CardInfo.svelte";
    import type { PageData } from "./$types";
    import { goto } from "$app/navigation";

    export let data: PageData;

    const showRevlog = $page.url.searchParams.get("revlog") !== "0";
</script>

<CardInfo stats={data.info} {showRevlog} />

<!-- used by CardInfoDialog.update_card -->
<form
    id="update_card_id"
    style="display: none;"
    method="POST"
    use:enhance={({ formElement }) => {
        const id = formElement.dataset?.id;
        if (id != null) {
            return async () => goto(`/card-info/${id}`);
        }
    }}
></form>
