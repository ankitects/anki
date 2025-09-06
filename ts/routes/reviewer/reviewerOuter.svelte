<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import { setupReviewer } from "./reviewer";
    import Reviewer from "./reviewer.svelte";

    import "../../reviewer/reviewer.scss";

    let reviewerInfo: null | ReturnType<typeof setupReviewer> = null;

    onMount(() => {
        reviewerInfo = setupReviewer();
    });
</script>

{#if reviewerInfo}
    <Reviewer {...reviewerInfo}></Reviewer>
{/if}
