<script lang="typescript">
    import type { I18n } from "anki/i18n";
    import { CardCountMethod } from "./graph-helpers";

    export let i18n: I18n;
    export let cardCountMethod: CardCountMethod;

    const byType = "By card type";
    const byQueue = "By scheduling Queue";
</script>

<label>
    <input type="radio" bind:group={cardCountMethod} value={CardCountMethod.ByType} />
    {byType}
</label>
<label>
    <input type="radio" bind:group={cardCountMethod} value={CardCountMethod.ByQueue} />
    {byQueue}
</label>
