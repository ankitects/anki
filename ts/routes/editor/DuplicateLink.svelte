<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { searchInBrowser } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import type { Note } from "@generated/anki/notes_pb";

    export let note: Note | null = null;

    function showDupes() {
        if (note) {
            searchInBrowser({
                filter: {
                    case: "dupe",
                    value: {
                        notetypeId: note.notetypeId,
                        firstField: note.fields[0],
                    },
                },
            });
        }
    }
</script>

<span class="duplicate-link-container">
    <a class="duplicate-link" href="/#" on:click={showDupes}>
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
