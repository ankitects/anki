<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { QueuedCards_Queue } from "@generated/anki/scheduler_pb";
    import type { ReviewerState } from "../reviewer";
    import RemainingNumber from "./RemainingNumber.svelte";

    export let state: ReviewerState;
    const cardData = state.cardData;
    $: queue = $cardData?.queue;
    $: underlined = queue?.cards[0].queue;
</script>

<span>
    <RemainingNumber cls="new-count" underlined={underlined === QueuedCards_Queue.NEW}>
        {queue?.newCount}
    </RemainingNumber> +
    <RemainingNumber
        cls="learn-count"
        underlined={underlined === QueuedCards_Queue.LEARNING}
    >
        {queue?.learningCount}
    </RemainingNumber> +
    <RemainingNumber
        cls="review-count"
        underlined={underlined === QueuedCards_Queue.REVIEW}
    >
        {queue?.reviewCount}
    </RemainingNumber>
</span>

<style>
    span {
        text-align: center;
    }
</style>
