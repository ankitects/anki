<script lang="typescript">
    const previousCard = () => {
        // select previous card in browser
    };

    const nextCard = () => {
        // select next card in browser
    };
</script>

<style>
    .bi::before {
        font-weight: bold !important;
    }
</style>

<div class="btn-group mx-1">
    <button type="button" class="btn btn-primary" on:click={previousCard}><i
            class="bi bi-chevron-up" /></button>
    <button type="button" class="btn btn-primary" on:click={nextCard}><i
            class="bi bi-chevron-down" /></button>
</div>
