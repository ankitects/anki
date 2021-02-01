<script lang="typescript">
    export const maximumCloze = 999;
    export let clozeNumber = 1;

    let decrementDisabled = false;
    let incrementDisabled = false;

    const clozeChanged = () => {
        clozeNumber = Math.min(maximumCloze, Math.max(1, clozeNumber));
        decrementDisabled = clozeNumber === 1;
        incrementDisabled = clozeNumber === maximumCloze;
    };

    const decrementCloze = () => {
        clozeNumber--;
        clozeChanged();
    };

    const incrementCloze = () => {
        clozeNumber++;
        clozeChanged();
    };

    clozeChanged();
</script>

<style>
    input[type="number"]::-webkit-inner-spin-button,
    input[type="number"]::-webkit-outer-spin-button {
        -webkit-appearance: none;
        margin: 0;
    }

    .form-control {
        width: 3.5rem;
    }

    .form-control:focus {
        box-shadow: 0 0.25rem 0 rgba(13, 110, 253, 0.25),
            0 -0.25rem 0 0 rgba(13, 110, 253, 0.25);
    }

    .bi::before {
        font-weight: bold !important;
    }
</style>

<div class="btn-group mx-1">
    <button
        type="button"
        class="btn btn-primary"
        on:click={decrementCloze}
        disabled={decrementDisabled}><i class="bi bi-dash" /></button>
    <span class="input-group-text user-select-none rounded-0">Cloze</span>
    <input
        class="form-control rounded-0"
        id="number"
        type="number"
        min="1"
        max={maximumCloze}
        on:change={clozeChanged}
        bind:value={clozeNumber} />
    <button
        type="button"
        class="btn btn-primary"
        on:click={incrementCloze}
        disabled={incrementDisabled}><i class="bi bi-plus" /></button>
</div>
