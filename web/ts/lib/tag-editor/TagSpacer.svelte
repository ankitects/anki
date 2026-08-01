<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<!-- on:click allows clicking focus to the tag editor -->
<!-- svelte-ignore a11y-click-events-have-key-events -->
<div class="tag-spacer" on:click role="textbox" tabindex="-1">
    <br />
</div>

<style lang="scss">
    .tag-spacer {
        cursor: text;
        flex-grow: 1;
        align-self: stretch;
        margin-top: 3px;
    }
</style>
