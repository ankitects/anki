<script lang="typescript">
    import type { I18n } from "anki/i18n";

    export let i18n: I18n;
    export let separateInactive: bool = false;

    const label = "Separate suspended/buried cards";
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
</script>

<label>
    <input type="checkbox" bind:checked={separateInactive} />
    {label}
</label>
