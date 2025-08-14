<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import NotetypeChooser from "$lib/components/NotetypeChooser.svelte";
    import DeckChooser from "$lib/components/DeckChooser.svelte";
</script>

<div class="top-bar">
    <p>Type</p>
    <div class="notetype-chooser">
        <NotetypeChooser />
    </div>
    <p>Deck</p>
    <div class="deck-chooser">
        <DeckChooser />
    </div>
</div>

<style>
    .top-bar {
        display: flex;
        gap: 0.5rem;
        margin-bottom: 0.5rem;
    }

    .notetype-chooser,
    .deck-chooser {
        flex: 1;
    }
</style>
