<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Switch from "$lib/components/Switch.svelte";

    export let title: string;
    export let description = "";
</script>

<label>
    <div class="header">
        <b>{title}</b>
        <Switch id={title} value={false}></Switch>
    </div>
    {description}
</label>

<style>
    label {
        padding: 1em;
        background-color: var(--bs-secondary-bg);
        border-radius: 1em;
    }
    div {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }
</style>
