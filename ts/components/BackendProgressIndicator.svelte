<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Progress } from "@tslib/anki/collection_pb";
    import { runWithBackendProgress } from "@tslib/progress";

    import { refreshIcon } from "./icons";

    export let task: () => Promise<unknown>;
    export let result: unknown | undefined = undefined;
    let label: string = "";

    function onUpdate(progress: Progress) {
        if (progress.value.value && label !== progress.value.value) {
            label = progress.value.value.toString();
        }
    }
    $: (async () => {
        if (!result) {
            result = await runWithBackendProgress(task, onUpdate);
        }
    })();
</script>

{#if !result}
    <div class="progress">
        <div class="spinner">
            {@html refreshIcon}
        </div>
        <div id="label">{label}</div>
    </div>
{/if}

<style lang="scss">
    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }
        100% {
            transform: rotate(360deg);
        }
    }

    .progress {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
    }

    .spinner {
        display: block;
        margin: 0 auto;
        width: 128px;
        animation: spin;
        animation-duration: 2s;
        animation-iteration-count: infinite;
        animation-timing-function: linear;
    }

    #label {
        text-align: center;
    }
</style>
