<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import ReviewerBottom from "./ReviewerBottom.svelte";
    import "./index.scss";
    import { setupBottomBar } from "./reviewer-bottom";

    let reviewerInfo: null | ReturnType<typeof setupBottomBar> = null;

    onMount(() => {
        reviewerInfo = setupBottomBar();
    });
</script>

{#if reviewerInfo}
    <ReviewerBottom {...reviewerInfo}></ReviewerBottom>
{/if}
