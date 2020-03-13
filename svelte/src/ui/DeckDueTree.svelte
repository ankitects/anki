<style>
  .dueTable {
    max-width: 50em;
    margin-left: auto;
    margin-right: auto;
  }
</style>

<script>
  import DeckDueNode from "./DeckDueNode.svelte";
  import { onMount } from "svelte";
  import { deckTree } from "anki/dist/backend";

  $: records = [];

  onMount(async () => {
    records = await deckTree();
  });
</script>

<div class="outer">
  <div class="dueTable">
    {#each records as record}
      <DeckDueNode {record} />
    {/each}
  </div>
</div>
