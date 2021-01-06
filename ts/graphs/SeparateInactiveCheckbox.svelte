<script lang="typescript">
    import type { I18n } from "anki/i18n";

    export let i18n: I18n;
    export let separateInactive: boolean = false;

    const label = i18n.tr(i18n.TR.STATISTICS_COUNTS_SEPARATE_SUSPENDED_BURIED_CARDS);
</script>

<label> <input type="checkbox" bind:checked={separateInactive} /> {label} </label>
