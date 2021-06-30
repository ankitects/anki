<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let id: string | undefined;
    export let value: boolean;
    export let disabled = false;

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<div class="form-check form-switch">
    <input
        {id}
        type="checkbox"
        class="form-check-input"
        class:nightMode
        bind:checked={value}
        {disabled}
    />
</div>

<style lang="scss">
    .form-switch {
        /* bootstrap adds a default 2.5em left pad, which causes */
        /* text to wrap prematurely */
        padding-left: 0.5em;
    }

    .form-check-input {
        -webkit-appearance: none;
        height: 1.6em;
        /* otherwise the switch circle shows slightly off-centered */
        margin-top: 0;

        .form-switch & {
            width: 3em;
            margin-left: 1.5em;
        }
    }

    .nightMode:not(:checked) {
        background-color: var(--frame-bg);
        border-color: var(--border);
    }
</style>
