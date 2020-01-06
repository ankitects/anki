<script>
  import BrowseTable from "./BrowseTable";
  import BrowseSearchInput from "./BrowseSearchInput";
  import { Browser } from "anki/dist/browser";

  let browser = new Browser();

  async function onSearchEnter(evt) {
    console.log(`got ${evt.detail.searchText}`);
    browser = new Browser();

    const b = new Browser();
    await b.search(evt.detail.searchText);
    browser = b;
  }
</script>

<div>
  <BrowseSearchInput on:enter="{onSearchEnter}" />
</div>
<BrowseTable {browser} />
