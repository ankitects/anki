<style>
  /* Style for "active" links; need to mark this :global because the router adds the class directly */
  :global(a.active) {
    color: red;
  }
</style>

<script>
  import DeckDueTree from "./DeckDueTree.svelte";
  import BrowseScreen from "./BrowseScreen.svelte";

  import Router from "svelte-spa-router";

  import { link } from "svelte-spa-router";
  import active from "svelte-spa-router/active";

  const routes = {
    // Exact path
    "/": DeckDueTree,
    "/browse": BrowseScreen
  };
</script>

<body>
  <nav>
    <a href="/" use:link use:active>Decks</a>
    <a href="/browse" use:link use:active>Browse</a>

  </nav>
  <Router {routes} />
</body>
