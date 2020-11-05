<script lang="ts">
    import { I18n } from "anki/i18n";
    import pb from "anki/backend_proto";
    import { buildNextLearnMsg } from "./lib";
    import { bridgeLink } from "anki/bridgecommand";

    export let info: pb.BackendProto.CongratsInfoOut;
    export let i18n: I18n;

    const congrats = i18n.tr(i18n.TR.SCHEDULING_CONGRATULATIONS_FINISHED);
    const nextLearnMsg = buildNextLearnMsg(info, i18n);
    const today_reviews = i18n.tr(i18n.TR.SCHEDULING_TODAY_REVIEW_LIMIT_REACHED);
    const today_new = i18n.tr(i18n.TR.SCHEDULING_TODAY_NEW_LIMIT_REACHED);

    const unburyThem = bridgeLink("unbury", i18n.tr(i18n.TR.SCHEDULING_UNBURY_THEM));
    const buriedMsg = i18n.tr(i18n.TR.SCHEDULING_BURIED_CARDS_FOUND, { unburyThem });
    const customStudy = bridgeLink(
        "customStudy",
        i18n.tr(i18n.TR.SCHEDULING_CUSTOM_STUDY)
    );
    const customStudyMsg = i18n.tr(i18n.TR.SCHEDULING_HOW_TO_CUSTOM_STUDY, {
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
    </div>
</div>
