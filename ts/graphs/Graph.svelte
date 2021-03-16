<script lang="typescript">
    export let title: string;
    export let subtitle: string | null = null;
</script>

<style lang="scss">
    .graph {
        margin-left: auto;
        margin-right: auto;
        max-width: 60em;
        page-break-inside: avoid;
    }

    h1 {
        text-align: center;
        margin-bottom: 0.25em;
        margin-top: 1.5em;
    }

    .subtitle {
        text-align: center;
        margin-bottom: 1em;
    }
</style>

<div class="graph">
    <h1>{title}</h1>

    {#if subtitle}
        <div class="subtitle">{subtitle}</div>
    {/if}

    <slot />
</div>
