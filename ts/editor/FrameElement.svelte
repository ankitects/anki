<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { FrameStart, FrameEnd, FrameElement } from "../editable/frame-element";

    customElements.define(FrameStart.tagName, FrameStart);
    customElements.define(FrameEnd.tagName, FrameEnd);
    customElements.define(FrameElement.tagName, FrameElement);

    import { BLOCK_ELEMENTS } from "../lib/dom";

    /* This will ensure that they are not targeted by surrounding algorithms */
    BLOCK_ELEMENTS.push(FrameStart.tagName.toUpperCase());
    BLOCK_ELEMENTS.push(FrameEnd.tagName.toUpperCase());
</script>
