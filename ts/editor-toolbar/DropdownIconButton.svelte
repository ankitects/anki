<script lang="typescript">
    import { onMount } from "svelte";

    export let buttonId = `dropdownMenuButton123`;

    // ${Math.random().toString(36).substring(2)}`
    let dropdownButtonRef: HTMLButtonElement;

    onMount(() => {
        /* Prevent focus on menu activation */
        const noop = () => {};
        Object.defineProperty(dropdownButtonRef, 'focus', { value: noop });

        /* Set custom menu without using .dropdown
         * Rendering the menu here would cause the menu to
         * be displayed outside of the visible area
         */
        const dropdown = new bootstrap.Dropdown(dropdownButtonRef);
        dropdown._menu = document.getElementById(buttonId);
    })
</script>

<button bind:this={dropdownButtonRef} class="dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false" on:mousedown|preventDefault>
  Dropdown button
</button>
