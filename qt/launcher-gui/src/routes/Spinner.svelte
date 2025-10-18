<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "$lib/sveltelib/theme";
    import { type Snippet } from "svelte";

    let { label = "", children }: { label?: string; children?: Snippet } = $props();
</script>

<!-- spinner taken from https://loading.io/css/; CC0 -->
<div class="progress">
    <div class="spinner" class:nightMode={$pageTheme.isDark}>
        <div></div>
        <div></div>
        <div></div>
        <div></div>
    </div>
    <div id="label">
        {label}
        {@render children?.()}
    </div>
</div>

<style lang="scss">
    .spinner {
        display: block;
        position: relative;
        width: 80px;
        height: 80px;
        margin: 0 auto;

        div {
            display: block;
            position: absolute;
            width: 32px;
            height: 32px;
            margin: 32px;
            border: 2px solid #000;
            border-radius: 50%;
            animation: spin 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
            border-color: #000 transparent transparent transparent;
        }
        &.nightMode div {
            border-top-color: #fff;
        }
        div:nth-child(1) {
            animation-delay: -0.45s;
        }
        div:nth-child(2) {
            animation-delay: -0.3s;
        }
        div:nth-child(3) {
            animation-delay: -0.15s;
        }
    }

    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }
        100% {
            transform: rotate(360deg);
        }
    }
    #label {
        text-align: center;
    }
</style>
