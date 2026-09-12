<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Switch from "$lib/components/Switch.svelte";
    import type { PreferenceStore } from "$lib/sveltelib/preferences";
    import type { ExperimentalFeatureFlag } from "@generated/anki/config_pb";

    export let title: string;
    export let description = "";
    export let key: ExperimentalFeatureFlag;
    export let labPerfs: PreferenceStore<any>;
    let value = $labPerfs[key];

    function onInput(e: Event) {
        const target = e.target as HTMLInputElement;
        $labPerfs = { ...$labPerfs, [key]: target.checked };
    }
</script>

<label>
    <div class="header">
        <b>{title}</b>
        <div class="switch">
            <Switch id={title} bind:value on:input={onInput}></Switch>
        </div>
    </div>
    {description}
</label>

<style>
    label {
        padding: 1em;
        border: var(--border-subtle) solid 1px;
        border-radius: 0.5em;
    }
    div {
        display: flex;
        justify-content: space-between;
        align-items: start;
        margin-bottom: 0.25em;
    }
    .switch {
        font-size: 1.25rem;
    }
</style>
