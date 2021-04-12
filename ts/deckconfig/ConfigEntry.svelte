<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import RevertIcon from "./RevertIcon.svelte";

    export let label: string;
    export let subLabel = "";
    export let value: any;
    export let defaultValue: any;
</script>

<style lang="scss">
    .outer {
        margin-top: 1em;

        & > * {
            margin-top: 0.3em;
        }
    }

    .label {
        font-weight: bold;
    }
</style>

<div class="outer">
    <span class="label">{label}</span>
    <RevertIcon bind:value {defaultValue} on:revert />
    <div class="sub">{subLabel}</div>

    <div class="inner">
        <slot />
    </div>
</div>
