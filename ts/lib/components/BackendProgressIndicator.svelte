<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { OpChanges, Progress } from "@generated/anki/collection_pb";
    import { runWithBackendProgress } from "@tslib/progress";

    import { pageTheme } from "$lib/sveltelib/theme";

    type ResultWithChanges = OpChanges | { changes?: OpChanges };

    export let task: () => Promise<ResultWithChanges | undefined>;
    export let result: ResultWithChanges | undefined;
    export let error: Error | undefined;
    let label: string = "";

    function onUpdate(progress: Progress) {
        if (
            progress.value.value &&
            progress.value.case !== "none" &&
            label !== progress.value.value.toString()
        ) {
            label = progress.value.value.toString();
        }
    }
    $: (async () => {
        if (!result && !error) {
            try {
                result = await runWithBackendProgress(task, onUpdate);
            } catch (err) {
                if (err instanceof Error) {
                    error = err;
                } else {
                    throw err;
                }
            }
        }
    })();
</script>

<!-- spinner taken from https://loading.io/css/; CC0 -->
{#if !result}
    <div class="progress">
        <div class="spinner" class:nightMode={$pageTheme.isDark}>
            <div></div>
            <div></div>
            <div></div>
            <div></div>
        </div>
        <div id="label">{label}</div>
    </div>
{/if}

<style lang="scss">
    .progress {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
    }

    .spinner {
        display: block;
        position: relative;
        width: 80px;
        height: 80px;
        margin: 0 auto;

        div {
            display: block;
            position: absolute;
            width: 64px;
            height: 64px;
            margin: 8px;
            border: 8px solid #000;
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
