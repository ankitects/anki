<script lang="typescript">
    export const cardTypeNames = ["CardTypeName", "CardTypeName2"];
    export let currentCardType = 0;

    let previousDisabled = false;
    let nextDisabled = false;

    $: currentItem = cardTypeNames[currentCardType];

    const cardTypeChanged = (n: number): void => {
        currentCardType = n;
        previousDisabled = n === 0;
        nextDisabled = n === cardTypeNames.length - 1;
    };

    const previousCardType = () => cardTypeChanged(Math.max(0, currentCardType - 1));
    const nextCardType = () =>
        cardTypeChanged(Math.min(cardTypeNames.length - 1, currentCardType + 1));

    cardTypeChanged(currentCardType);

    const cardTypeChangedHandler = (event: MouseEvent) =>
        cardTypeChanged(event.target.value);
</script>

<style>
    .bi::before {
        font-weight: bold !important;
    }
</style>

<div class="btn-group mx-1">
    <button
        type="button"
        class="btn btn-primary"
        on:click={previousCardType}
        disabled={previousDisabled}><i class="bi bi-chevron-left" /></button>
    <div class="btn-group dropup">
        <button
            type="button"
            class="btn btn-outline-primary rounded-0"
            data-bs-toggle="dropdown">
            <span class="mx-2">{currentItem}</span>
        </button>
        <ul class="dropdown-menu dropdown-menu-end">
            {#each cardTypeNames as cardType, i}
                <li class="dropdown-item" value={i} on:click={cardTypeChangedHandler}>
                    {cardType}
                </li>
            {/each}
        </ul>
    </div>
    <button
        type="button"
        class="btn btn-primary"
        on:click={nextCardType}
        disabled={nextDisabled}><i class="bi bi-chevron-right" /></button>
</div>
