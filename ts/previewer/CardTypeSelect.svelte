<script lang="typescript">
    export let cardTypeNames = ["CardTypeName"];
    export let currentCardType = 0;

    const previousCardType = () => currentCardType = Math.max(0, currentCardType - 1);
    const nextCardType = () => currentCardType = Math.min(cardTypeNames.length - 1, currentCardType + 1);

    $: currentItem = cardTypeNames[currentCardType];

    const cardTypeChanged = (event: MouseEvent) => {
        currentCardType = event.target.value;
    }
</script>

<style>
    .bi::before {
        font-weight: bold !important;
    }
</style>

<div class="btn-group mx-1">
    <button type="button" class="btn btn-primary" on:click={previousCardType}><i class="bi bi-chevron-left"></i></button>
    <div class="btn-group dropup">
        <button type="button" class="btn btn-outline-primary rounded-0" data-bs-toggle="dropdown">
            <span class="mx-2">{currentItem}</span>
        </button>
        <ul class="dropdown-menu dropdown-menu-end">
            {#each cardTypeNames as cardType, i}
                <li class="dropdown-item" value={i} on:click={cardTypeChanged}>{cardType}</li>
            {/each}
        </ul>
    </div>
    <button type="button" class="btn btn-primary" on:click={nextCardType}><i class="bi bi-chevron-right"></i></button>
</div>
