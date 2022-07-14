<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let tabs: string[];
    export let activeTab: number;

    const handleClick = (tabValue: number) => () => (activeTab = tabValue);
</script>

<ul>
    {#each tabs as tab, idx}
        <li class={activeTab === idx ? "active" : ""}>
            <span on:click={handleClick(idx)}>{tab}</span>
        </li>
    {/each}
</ul>
<div class="content">
    <slot />
</div>

<style lang="scss">
    div.content {
        border: 1px solid var(--border);
        border-radius: 0.25rem;
        margin-top: 0;
        padding: 0.5rem;
        z-index: -1;
    }

    ul {
        display: flex;
        flex-wrap: wrap;
        margin-top: 1rem;
        margin-bottom: 0.5rem;
        list-style: none;
    }

    span {
        border: 1px solid transparent;
        border-top-left-radius: 0.25rem;
        border-top-right-radius: 0.25rem;
        display: block;
        padding: 0.25rem 1rem;
        cursor: pointer;
        margin: 0 8px -7px 0;
        color: var(--disabled);
    }

    li.active > span {
        border-color: var(--border) var(--border) var(--window-bg);
        color: var(--text-fg);
    }

    span:hover {
        color: var(--text-fg);
    }
</style>
