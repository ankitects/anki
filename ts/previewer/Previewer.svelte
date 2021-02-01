<script>
    import CardDisplay from "./CardDisplay.svelte";
    import Navbar from "./Navbar.svelte";

    import CardTypeSelect from "./CardTypeSelect.svelte"
</script>

<CardDisplay />
<Navbar extraControls={CardTypeSelect} />
