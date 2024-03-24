<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ImageOcclusionPage from "../ImageOcclusionPage.svelte";
    import type { PageData } from "./$types";

    export let data: PageData;
</script>

<ImageOcclusionPage mode={data.mode} />
