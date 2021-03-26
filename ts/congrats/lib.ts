// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "anki/backend_proto";
import { postRequest } from "anki/postrequest";
import { naturalUnit, unitAmount, unitName } from "anki/time";
import type { I18n } from "anki/i18n";

export async function getCongratsInfo(): Promise<pb.BackendProto.CongratsInfoOut> {
    return pb.BackendProto.CongratsInfoOut.decode(
        await postRequest("/_anki/congratsInfo", "")
    );
}

export function buildNextLearnMsg(
    info: pb.BackendProto.CongratsInfoOut,
    i18n: I18n
): string {
    const secsUntil = info.secsUntilNextLearn;
    // next learning card not due (/ until tomorrow)?
    if (secsUntil == 0 || secsUntil > 86_400) {
        return "";
    }

    const unit = naturalUnit(secsUntil);
    const amount = Math.round(unitAmount(unit, secsUntil));
    const unitStr = unitName(unit);
    const nextLearnDue = i18n.schedulingNextLearnDue({
        amount,
        unit: unitStr,
    });
    const remaining = i18n.schedulingLearnRemaining({
        remaining: info.learnRemaining,
    });
    return `${nextLearnDue} ${remaining}`;
}
