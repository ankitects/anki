<script lang="typescript">
    import { bridgeCommand } from "anki/bridgecommand";

    export let search: Readable<string>;
    export let query: string;

    $: {
        bridgeCommand(`browserSearch: ${$search} ${query}`);
    }
</script>
