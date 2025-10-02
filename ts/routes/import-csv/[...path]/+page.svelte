<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ImportCsvPage from "../ImportCsvPage.svelte";
    import ErrorPage from "$lib/components/ErrorPage.svelte";
    import type { PageData } from "./$types";

    export let data: PageData;
</script>

{#if data.initialError}
    <ErrorPage error={data.initialError} />
{:else}
    <ImportCsvPage state={data.state} />
{/if}
