<script lang="typescript">
    interface DropdownItem {
        label: string;
        endLabel: string;
        onClick: (event: ClickEvent) => void;
    }

    export let id: string;
    export let menuItems: DropdownItem[];
</script>

<ul class="dropdown-menu" {id}>
    {#each menuItems as menuItem}
        <li>
            <button
                class="dropdown-item"
                on:click={menuItem.onClick}
                on:mousedown|preventDefault>
                <span class="float-start">{menuItem.label}</span>
                {#if menuItem.endLabel}
                    <span class="float-end">{menuItem.endLabel}</span>
                {/if}
            </button>
        </li>
    {/each}
</ul>
