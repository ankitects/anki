<script>
    import ReviewerBottomOuter from "./reviewer-bottom/ReviewerBottomOuter.svelte";
    import Reviewer from "./reviewer.svelte";
</script>

<div>
    <Reviewer></Reviewer>
    <ReviewerBottomOuter></ReviewerBottomOuter>
</div>

<style>
    div {
        height: calc(100vh - 40px);
        display: flex;
        flex-direction: column;
    }
</style>
