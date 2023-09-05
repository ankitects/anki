// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Progress } from "@tslib/anki/collection_pb";
import { latestProgress } from "@tslib/backend";

export async function runWithBackendProgress<T>(
    callback: () => Promise<T>,
    onUpdate: (progress: Progress) => void,
): Promise<T> {
    const intervalId = setInterval(async () => {
        const progress = await latestProgress({});
        onUpdate(progress);
    }, 100);
    try {
        return await callback();
    } finally {
        clearInterval(intervalId);
    }
}
