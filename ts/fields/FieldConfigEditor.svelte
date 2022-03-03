<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Checkbox from "../components/CheckBox.svelte";
    import type { Notetypes } from "../lib/proto";

    export let config: Notetypes.Notetype.Field.Config;

    $: ({ description, fontName, fontSize } = config);
</script>

<div>Description: {description}</div>
<div>EditingFont: {fontName}</div>
<div>FontSize: {fontSize}</div>

<div>Reverse text direction</div>
<Checkbox value={config.rtl} />
<div>Sort by this field in browser</div>
<Checkbox value={false} />

<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
<div>Enough lines to scroll...</div>
