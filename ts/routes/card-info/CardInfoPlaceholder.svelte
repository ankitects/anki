<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
</script>

<div class="card-info-placeholder">{tr.cardStatsNoCard()}</div>

<style lang="scss">
    .card-info-placeholder {
        margin: 0;
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        color: grey;
    }
</style>
