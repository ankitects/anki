<script>
    import ReviewerBottomOuter from "./reviewer-bottom/ReviewerBottomOuter.svelte";
    import ReviewerOuter from "./reviewerOuter.svelte";
</script>

<div>
    <ReviewerOuter></ReviewerOuter>
    <ReviewerBottomOuter></ReviewerBottomOuter>
</div>

<style>
    div {
        height: calc(100vh - 40px);
        display: flex;
        flex-direction: column;
    }
</style>
