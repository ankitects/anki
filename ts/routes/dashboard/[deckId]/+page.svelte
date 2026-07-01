<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DashboardView from "../DashboardView.svelte";
    import type { PageData } from "./$types";

    export let data: PageData;
</script>

<DashboardView deckId={data.deckId} />
