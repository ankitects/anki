<script>
  import VirtualList from "@sveltejs/svelte-virtual-list";
  import BrowseRow from "./BrowseRow.svelte";

  export let browser;

  let start, end;

  let rows;
  $: rows = Array.from(Array(browser.rows()).keys());
</script>

<p>showing {start}-{end} of {rows.length} rows</p>

<VirtualList items="{rows}" let:item bind:start bind:end itemHeight="{35}">
  <BrowseRow idx="{item}" {browser} />
</VirtualList>
