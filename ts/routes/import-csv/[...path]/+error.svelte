<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import { page } from "$app/state";
    import ErrorPage from "$lib/components/ErrorPage.svelte";

    $: error = new Error(page.error?.message ?? "Unknown error");
</script>

<ErrorPage {error} />
