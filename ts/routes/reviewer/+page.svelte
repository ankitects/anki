<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import ReviewerBottomOuter from "./reviewer-bottom/ReviewerBottomOuter.svelte";
    import Reviewer from "./Reviewer.svelte";
</script>

<div>
    <Reviewer></Reviewer>
    <ReviewerBottomOuter></ReviewerBottomOuter>
</div>

<style>
    div {
        height: 100vh;
        display: flex;
        flex-direction: column;
    }
</style>
