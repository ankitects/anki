<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let error: Error;
</script>

<div class="message">
    {error.message}
</div>

<style lang="scss">
    .message {
        text-align: center;
        margin: 50px 0 0;
    }
</style>
