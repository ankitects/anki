<style>
  button {
    border: none;
    background: none;
    width: 1em;
  }

  .nodeOuter {
    border-bottom: 1px solid #eee;
    margin-bottom: 5px;
  }

  .nodeInner {
    padding: 5px;
  }

  .counts {
    float: right;
    font-size: smaller;
    text-align: right;
  }

  .new {
    color: #000099;
  }
  .due {
    color: #007700;
  }
</style>

<script>
  import { slide } from "svelte/transition";
  import { pb } from "anki/dist/backend";

  export let record = new pb.DeckTreeNode();

  $: deck = record.names[record.names.length - 1];
  $: dueCount = record.reviewCount + record.learnCount;
  $: collapsed = record.collapsed;
  $: indent = record.names.length > 1 ? 1 : 0;

  function onToggleRow() {
    collapsed = !collapsed;
  }
</script>

<div class="nodeOuter" style="margin-left: {indent * 15}px;">
  <div class="nodeInner">
    {#if record.children.length > 0}
      <button on:click="{onToggleRow}">
        {#if collapsed}+{:else}-{/if}
      </button>
    {:else}
      <button></button>
    {/if}
    {deck}
    <div class="counts">
      <span class="due">{dueCount}</span>
      <br />
      <span class="new">{record.newCount}</span>
    </div>
  </div>
  {#if !collapsed}
    <div transition:slide|local>
      {#each record.children as child, idx (child.deckId)}
        <svelte:self record="{child}" />
      {/each}
    </div>
  {/if}
</div>
