<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Stats } from "../lib/proto";
    import CardStats from "./CardStats.svelte";
    import Revlog from "./Revlog.svelte";

    export let stats: Stats.CardStatsResponse;
</script>

<div class="container">
    <div>
        <CardStats {stats} />
        <Revlog {stats} />
    </div>
</div>

<style>
    .container {
        max-width: 40em;
    }
</style>
