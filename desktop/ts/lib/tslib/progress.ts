// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Progress } from "@generated/anki/collection_pb";
import { latestProgress } from "@generated/backend";

export async function runWithBackendProgress<T>(
    callback: () => Promise<T>,
    onUpdate: (progress: Progress) => void,
): Promise<T> {
    let done = false;
    async function progressCallback() {
        const progress = await latestProgress({});
        onUpdate(progress);
        if (done) {
            return;
        }
        setTimeout(progressCallback, 100);
    }
    setTimeout(progressCallback, 100);
    try {
        return await callback();
    } finally {
        done = true;
    }
}
