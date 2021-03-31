<script lang="typescript">
    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};
    export let title: string;

    export let onClick: (event: ClickEvent) => void;
    export let label: string;
    export let endLabel: string;
</script>

<button
    {id}
    class={`dropdown-item ${className}`}
    {...props}
    {title}
    on:click={onClick}
    on:mousedown|preventDefault>
    <span class="float-start">{label}</span>

    {#if endLabel}<span class="float-end">{endLabel}</span>{/if}
</button>
