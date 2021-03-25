<script lang="typescript">
    import useAsync from "sveltelib/async";
    import { setupI18n } from "anki/i18n";
    import { checkNightMode } from "anki/nightmode";

    const nightMode = checkNightMode();

    const { loading, value: i18n } = useAsync(() => setupI18n());
</script>

{#if !$loading}
    <slot i18n={$i18n} {nightMode} />
{/if}
