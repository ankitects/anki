<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { bridgeCommand } from "@tslib/bridgecommand";
</script>

<span class="duplicate-link-container">
    <a class="duplicate-link" href="/#" on:click={() => bridgeCommand("dupes")}>
        {tr.editingShowDuplicates()}
    </a>
</span>

<style lang="scss">
    .duplicate-link-container {
        text-align: center;
        flex-grow: 1;
    }

    .duplicate-link {
        color: var(--highlight-color);
    }
</style>
