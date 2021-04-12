<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type pb from "anki/backend_proto";
    import NewOptions from "./NewOptions.svelte";
    import ReviewOptions from "./ReviewOptions.svelte";
    import LapseOptions from "./LapseOptions.svelte";
    import GeneralOptions from "./GeneralOptions.svelte";

    export let config: pb.BackendProto.DeckConfig.Config;
    export let defaults: pb.BackendProto.DeckConfig.Config;
</script>

<style lang="scss">
    :global(h2) {
        margin-top: 1em;
    }

    :global(.warn) {
        background-color: var(--flag1-bg);
    }
</style>

<div>
    <NewOptions bind:config {defaults} />
    <ReviewOptions bind:config {defaults} />
    <LapseOptions bind:config {defaults} />
    <GeneralOptions bind:config {defaults} />
</div>
