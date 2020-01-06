<script>
  import { createEventDispatcher } from "svelte";

  const dispatch = createEventDispatcher();

  let searchText;

  function onKeyUp(e) {
    if (e.keyCode === 13) {
      console.log(`on key up: e.target.keycode`);
      dispatch("enter", { searchText });
    }
  }
</script>

<input
  bind:value="{searchText}"
  placeholder="hit enter to search"
  on:keyup="{onKeyUp}"
  autofocus
/>
