<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { NotetypeNameId } from "@generated/anki/notetypes_pb";

    import { mdiNewspaper } from "./icons";
    import { getNotetypeNames } from "@generated/backend";
    import ItemChooser from "./ItemChooser.svelte";

    let notetypes: NotetypeNameId[] = $state([]);
    let selectedNotetype: NotetypeNameId | null = $state(null);

    $effect(() => {
        getNotetypeNames({}).then((response) => {
            notetypes = response.entries;
        });
    });
</script>

<ItemChooser
    title="Choose Note Type"
    searchPlaceholder="Search note types..."
    bind:selectedItem={selectedNotetype}
    items={notetypes}
    icon={mdiNewspaper}
/>
