<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { NotetypeNameId } from "@generated/anki/notetypes_pb";

    import { mdiNewspaper } from "./icons";
    import { getNotetypeNames } from "@generated/backend";
    import ItemChooser from "./ItemChooser.svelte";
    import * as tr from "@generated/ftl";

    interface Props {
        selectedNotetype: NotetypeNameId | null;
        onChange?: (notetype: NotetypeNameId) => void;
    }
    let { selectedNotetype = $bindable(null), onChange }: Props = $props();
    let notetypes: NotetypeNameId[] = $state([]);

    $effect(() => {
        getNotetypeNames({}).then((response) => {
            notetypes = response.entries;
        });
    });
</script>

<ItemChooser
    title={tr.qtMiscChooseNoteType()}
    bind:selectedItem={selectedNotetype}
    {onChange}
    items={notetypes}
    icon={mdiNewspaper}
    keyCombination="Control+N"
    tooltip={tr.qtMiscChangeNoteTypeCtrlandn()}
/>
