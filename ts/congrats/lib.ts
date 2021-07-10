// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Backend } from "lib/proto";
import { postRequest } from "lib/postrequest";
import { naturalUnit, unitAmount, unitName } from "lib/time";

import * as tr from "lib/i18n";

export async function getCongratsInfo(): Promise<Backend.CongratsInfoResponse> {
    return Backend.CongratsInfoResponse.decode(
        await postRequest("/_anki/congratsInfo", "")
    );
}

export function buildNextLearnMsg(info: Backend.CongratsInfoResponse): string {
    const secsUntil = info.secsUntilNextLearn;
    // next learning card not due (/ until tomorrow)?
    if (secsUntil == 0 || secsUntil > 86_400) {
        return "";
    }

    const unit = naturalUnit(secsUntil);
    const amount = Math.round(unitAmount(unit, secsUntil));
    const unitStr = unitName(unit);
    const nextLearnDue = tr.schedulingNextLearnDue({
        amount,
        unit: unitStr,
    });
    const remaining = tr.schedulingLearnRemaining({
        remaining: info.learnRemaining,
    });
    return `${nextLearnDue} ${remaining}`;
}
