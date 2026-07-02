<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ConfigInput from "$lib/components/ConfigInput.svelte";
    import RevertButton from "$lib/components/RevertButton.svelte";

    import GlobalLabel from "./GlobalLabel.svelte";

    export let value: string;
    export let title: string;
</script>

<div class="m-2">
    <ConfigInput>
        <RevertButton slot="revert" bind:value defaultValue="" />
        <details>
            <summary><GlobalLabel {title} /></summary>
            <div class="text">
                <textarea
                    class="card-state-customizer form-control"
                    bind:value
                    spellcheck="false"
                    autocapitalize="none"
                ></textarea>
            </div>
        </details>
    </ConfigInput>
</div>

<style lang="scss">
    .text {
        width: 100%;
        min-height: 2em;
    }

    .card-state-customizer {
        background-color: var(--canvas-code);
        border: 1px solid var(--border-subtle);
        width: 100%;
        height: 10em;
        font-family: monospace;
    }

    @supports (-webkit-touch-callout: none) {
        // mobile compat
        .card-state-customizer {
            font-size: 16px;
            overflow-x: hidden;
        }
    }
</style>
