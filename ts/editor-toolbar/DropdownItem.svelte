<script lang="typescript">
    export let id = "";
    export let className = "";
    export let tooltip: string;

    export let onClick: (event: MouseEvent) => void;
    export let label: string;
    export let endLabel: string;
</script>

<button
    {id}
    class={`dropdown-item ${className}`}
    title={tooltip}
    {...$$restProps}
    on:click={onClick}
    on:mousedown|preventDefault>
    <span class="float-start">{label}</span>

    {#if endLabel}<span class="float-end">{endLabel}</span>{/if}
</button>
