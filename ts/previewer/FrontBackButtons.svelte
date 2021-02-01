<script lang="typescript">
    export let showFront;
</script>

<div class="btn-group">
    <input id="front" class="btn-check" type=radio bind:group={showFront} value={true}>
    <label for="front" class="btn btn-outline-primary">Front</label>

    <input id="back" class="btn-check" type=radio bind:group={showFront} value={false}>
    <label for="back" class="btn btn-outline-primary">Back</label>
</div>
