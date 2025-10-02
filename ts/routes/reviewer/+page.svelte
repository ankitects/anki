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
