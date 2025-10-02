<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    declare function pycmd(cmd: string): void;
</script>

<script lang="ts">
    export let error: Error;

    function normalizeErrorMessage(message: string): string {
        return message.replace(/^\d{3}:\s*/, "");
    }

    function closeWindow() {
        try {
            pycmd("close");
        } catch {
            history.back();
        }
    }
</script>

<div class="message">
    {normalizeErrorMessage(error.message)}
</div>

<div class="actions">
    <button on:click={closeWindow}>Close</button>
</div>

<style lang="scss">
    .message {
        text-align: center;
        margin: 50px 0 0;
    }

    .actions {
        text-align: center;
        margin-top: 1rem;

        button {
            padding: 0.5rem 1rem;
            border: none;
            border-radius: 4px;
            background: var(--anki-button-bg, #007acc);
            color: white;
            cursor: pointer;
        }
    }
</style>
