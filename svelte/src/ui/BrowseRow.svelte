<style>
  .row {
    /* ensures text is center-aligned */
    line-height: 35px;
    height: 35px;

    overflow: hidden;

    background-color: white;

    padding-left: 0.5em;
    padding-right: 0.5em;
  }

  .rowAlt {
    background-color: #eee;
  }

  .rowSelected {
    background-color: #77f;
  }
</style>

<script>
  import { Browser } from "anki/dist/browser";
  import { onMount, onDestroy } from "svelte";

  export let idx = 0;
  export let browser = new Browser();

  $: cardData = null;

  onMount(() => {
    browser.getRowData(idx, data => (cardData = data));
  });
  onDestroy(() => {
    browser.cancelRequest(idx);
  });
</script>

<div class="row" class:rowAlt="{idx % 2 === 0}" class:rowSelected="{false}">
  {#if cardData !== null}{idx}: {cardData}{/if}
</div>
