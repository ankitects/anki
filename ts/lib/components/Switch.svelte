<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "$lib/sveltelib/theme";

    export let id: string | undefined;
    export let value: boolean;
    export let disabled = false;

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";
</script>

<div class="form-check form-switch" class:rtl>
    <input
        {id}
        type="checkbox"
        class="form-check-input"
        class:nightMode={$pageTheme.isDark}
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
        border-color: var(--border);
        -webkit-appearance: none;
        appearance: none;
        height: 1.5em;
        /* otherwise the switch circle shows slightly off-centered */
        margin-top: 0;

        .form-switch & {
            width: 3em;
            margin-left: 1.5em;
            cursor: pointer;
        }
    }

    .nightMode:not(:checked) {
        background-color: var(--canvas-elevated);
        border-color: var(--border);
    }

    .form-switch.rtl {
        padding-left: 0;
        padding-right: 0.5em;
        .form-check-input {
            margin-left: 0;
            margin-right: 1.5em;
        }
    }
</style>
