<script lang="typescript">
    export let showFront;
</script>

<div class="btn-group">
    <input
        id="front"
        class="btn-check"
        type="radio"
        bind:group={showFront}
        value={true} />
    <label for="front" class="btn btn-outline-primary px-3">Front</label>

    <input
        id="back"
        class="btn-check"
        type="radio"
        bind:group={showFront}
        value={false} />
    <label for="back" class="btn btn-outline-primary px-3">Back</label>
</div>
