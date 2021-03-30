<script lang="typescript">
    export let onClick: (event: ClickEvent) => void;
</script>

<li>
    <button class="dropdown-item" on:click={onClick} on:mousedown|preventDefault>
        {#if $$slots.default}
            <slot />
        {:else}
            <span class="float-start"><slot name="start" /></span>
            <span class="float-end"><slot name="end" /></span>
        {/if}
    </button>
</li>
