<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    /* This component accepts an array of tabs and a value. Whenever a tab is
    activated, its last used value is applied to its provided setter and the
    component's value. Whenever it's deactivated, its setter is called with its
    disabledValue. */
    import type { ValueTab } from "./lib";

    export let tabs: ValueTab[];
    export let value: number;

    let activeTab = lastSetTab();
    $: onTabChanged(activeTab);
    $: value = tabs[activeTab].value ?? 0;
    $: tabs[activeTab].setValue(value);

    function lastSetTab(): number {
        const revIdx = tabs
            .slice()
            .reverse()
            .findIndex((tab) => tab.value !== null);
        return revIdx === -1 ? 0 : tabs.length - revIdx - 1;
    }

    function onTabChanged(newTab: number) {
        for (const [idx, tab] of tabs.entries()) {
            if (newTab === idx) {
                tab.enable(value);
            } else if (newTab > idx) {
                /* antecedent tabs are obscured, so we can preserve their original values */
                tab.reset();
            } else {
                /* but subsequent tabs would obscure, so they must be nulled */
                tab.disable();
            }
        }
    }

    const handleClick = (tabValue: number) => () => (activeTab = tabValue);
</script>

<ul>
    {#each tabs as tab, idx}
        <li class={activeTab === idx ? "active" : ""}>
            <span on:click={handleClick(idx)}>{tab.title}</span>
        </li>
    {/each}
</ul>

<style lang="scss">
    ul {
        display: flex;
        flex-wrap: wrap;
        padding-left: 0;
        margin-top: 1rem;
        margin-bottom: 0.5rem;
        list-style: none;
        border-bottom: 1px solid var(--border);
    }

    span {
        border: 1px solid transparent;
        border-top-left-radius: 0.25rem;
        border-top-right-radius: 0.25rem;
        display: block;
        padding: 0.25rem 1rem;
        cursor: pointer;
        margin: 0 8px -1px 0;
        color: var(--slightly-grey-text);
    }

    li.active > span {
        border-color: var(--border) var(--border) var(--window-bg);
        color: var(--text-fg);
    }

    span:hover {
        color: var(--text-fg);
    }
</style>
