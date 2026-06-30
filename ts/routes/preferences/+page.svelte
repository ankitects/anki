<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import Row from "$lib/components/Row.svelte";
    import "../deck-options/deck-options-base.scss";
    import LabItem from "./LabItem.svelte";
</script>

<div class="container">
    <Row>
        <div class="col-12 alert alert-warning mb-0">
            <b>⚠️ Experimental Features</b>
            <br />
            These features may change, break, or be removed without notice, use at your own
            risk.
        </div>
    </Row>

    <div class="lab-grid">
        <LabItem
            title="Svelte note editor"
            description="Replaces the legacy editor with a new Svelte-based implementation. May affect addon compatibility."
        ></LabItem>
        <LabItem title="Example 2"></LabItem>
        <LabItem
            title="Example 3"
            description="This is a long winded description to help demonstrate the tiling of these elements. They should remain in line because I have used the css grid rather than flex boxes for easy use."
        ></LabItem>
        <LabItem title="Example 4" description="Labs"></LabItem>
    </div>
</div>

<style>
    .lab-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1em;
    }

    .container {
        display: flex;
        flex-direction: column;
        gap: 1em;
        margin: 1em;
        align-items: center;
    }

    :global(body) {
        background-color: var(--canvas-elevated) !important;
        font-size: 13px;
    }
</style>
