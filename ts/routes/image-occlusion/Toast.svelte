<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { mdiClose } from "$lib/components/icons";
    import type { ToastProps } from "./types";

    let props: ToastProps = $props();
    let shown = $state(false);

    export function hide() {
        shown = false;
    }

    export function show() {
        shown = true;
    }

    $effect(() => {
        if (props.timeout) {
            setTimeout(hide, props.timeout);
        }
    });
</script>

{#if shown}
    <div class="toast-container">
        <div class="toast {props.type === 'success' ? 'success' : 'error'}">
            {props.message}
            <IconButton iconSize={96} on:click={hide} class="toast-icon">
                <Icon icon={mdiClose} />
            </IconButton>
        </div>
    </div>
{/if}

<style>
    .toast-container {
        position: fixed;
        bottom: 3rem;
        z-index: 100;
        width: 100%;
        text-align: center;
        display: flex;
        justify-content: center;
    }
    .toast {
        display: flex;
        align-items: center;
        padding: 1rem;
        background-color: #fff;
        border-radius: 0.5rem;
        box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.1);
        width: 60%;
        justify-content: space-between;
    }
    .success {
        background: #66bb6a;
        color: white;
    }
    .error {
        background: #ef5350;
        color: white;
    }
    :global(.toast-icon) {
        background: unset !important;
        color: white !important;
        border: none !important;
    }
</style>
