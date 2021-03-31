<script lang="typescript">
    export let label: string;
    export let value: string;
    export let selected = false;
</script>

<option {selected} {value}>{label}</option>
