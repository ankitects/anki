<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    declare function pycmd(cmd: string);
</script>

<script lang="ts">
    export let error: Error;

    function closePage() {
        try {
            pycmd("close");
        } catch {
            history.back();
        }
    }
</script>

<div class="error-box">
    <p class="error-text">{error.message}</p>
    <button class="btn btn-primary" on:click={closePage}>Got it</button>
</div>

<style lang="scss">
    .error-box {
        background-color: var(--window-bg);
        border: 1px solid var(--error-fg);
        color: var(--fg);
        padding: 1rem;
        border-radius: 0.5rem;
        max-width: 400px;
        margin: 1rem auto;
        text-align: center;
        box-shadow: 0 0 6px rgba(0, 0, 0, 0.15);
    }
    .error-text {
        color: var(--error-fg);
        font-weight: 600;
        margin-bottom: 0.75rem;
    }
</style>
