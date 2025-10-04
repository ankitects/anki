<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import { ReviewerState } from "./reviewer";
    import ReviewerBottom from "./reviewer-bottom/ReviewerBottom.svelte";
    import Reviewer from "./Reviewer.svelte";

    const state = new ReviewerState();
</script>

<div>
    <Reviewer {state}></Reviewer>
    <ReviewerBottom {state}></ReviewerBottom>
</div>

<style>
    div {
        height: 100vh;
        display: flex;
        flex-direction: column;
    }
</style>
