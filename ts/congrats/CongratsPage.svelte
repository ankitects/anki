<script lang="ts">
    import "../sass/core.css";

    import { I18n } from "anki/i18n";
    import pb from "anki/backend_proto";
    import { buildNextLearnMsg } from "./lib";
    import { bridgeLink } from "anki/bridgecommand";

    export let info: pb.BackendProto.CongratsInfoOut;
    export let i18n: I18n;

    const congrats = i18n.schedulingCongratulationsFinished();
    const nextLearnMsg = buildNextLearnMsg(info, i18n);
    const today_reviews = i18n.schedulingTodayReviewLimitReached();
    const today_new = i18n.schedulingTodayNewLimitReached();

    const unburyThem = bridgeLink("unbury", i18n.schedulingUnburyThem());
    const buriedMsg = i18n.schedulingBuriedCardsFound({ unburyThem });
    const customStudy = bridgeLink("customStudy", i18n.schedulingCustomStudy());
    const customStudyMsg = i18n.schedulingHowToCustomStudy({
        customStudy,
    });
</script>

<style>
    .congrats-outer {
        display: flex;
        justify-content: center;
    }

    .congrats-inner {
        max-width: 30em;
    }

    .description {
        border: 1px solid var(--border);
        padding: 1em;
    }
</style>

<div class="congrats-outer">
    <div class="congrats-inner">
        <h3>{congrats}</h3>

        <p>{nextLearnMsg}</p>

        {#if info.reviewRemaining}
            <p>{today_reviews}</p>
        {/if}

        {#if info.newRemaining}
            <p>{today_new}</p>
        {/if}

        {#if info.bridgeCommandsSupported}
            {#if info.haveSchedBuried || info.haveUserBuried}
                <p>
                    {@html buriedMsg}
                </p>
            {/if}

            {#if !info.isFilteredDeck}
                <p>
                    {@html customStudyMsg}
                </p>
            {/if}
        {/if}

        {#if info.deckDescription}
            <div class="description">
                {@html info.deckDescription}
            </div>
        {/if}
    </div>
</div>
