// SPDX-License-Identifier: GPL-3.0-or-later

const fs = require("node:fs");

module.exports = async function checkAlphaRelease({ github, context, core }) {
    async function skipRelease(reason) {
        core.info(`Skipping scheduled release: ${reason}`);
        await github.rest.actions.cancelWorkflowRun({
            ...context.repo,
            run_id: context.runId,
        });
        // Cancellation is asynchronous. Keep this step alive so cancelWorkflowRun can kill it.
        await keepProcessAlive();
    }

    const metadata = JSON.parse(
        fs.readFileSync("AnkiDroid/build/outputs/release-metadata.json", "utf8"),
    );
    if (metadata.releaseType !== "alpha") {
        return await skipRelease("main is not on an alpha version.");
    }

    const releases = await github.paginate(github.rest.repos.listReleases, {
        ...context.repo,
        per_page: 100,
    });
    // Use calendar days to check, so last Monday's release doesn't block this release.
    const day = 24 * 60 * 60 * 1000;
    const today = Math.floor(Date.now() / day);
    const recentRelease = releases.find(
        release => !release.draft && today - Math.floor(Date.parse(release.published_at) / day) < 7,
    );
    if (recentRelease) {
        return await skipRelease(
            `${recentRelease.tag_name} was published fewer than seven UTC calendar days ago.`,
        );
    }

    const latestAlpha = releases
        .filter(release => !release.draft && /^v\d+\.\d+\.\d+alpha\d+$/.test(release.tag_name))
        .sort((a, b) => Date.parse(b.published_at) - Date.parse(a.published_at))[0];
    if (!latestAlpha) {
        return await skipRelease("no published alpha to compare against.");
    }

    const { data: comparison } = await github.rest.repos.compareCommitsWithBasehead({
        ...context.repo,
        basehead: `${latestAlpha.tag_name}...${context.sha}`,
    });
    if (comparison.status !== "ahead" || comparison.ahead_by === 0) {
        return await skipRelease(`no new commits descending from ${latestAlpha.tag_name}.`);
    }

    core.info(
        `Publishing an alpha: ${comparison.ahead_by} new commits since ${latestAlpha.tag_name}.`,
    );
    return true;
};

async function keepProcessAlive() {
    // Fail after five minutes if cancellation never arrives.
    await new Promise(resolve => setTimeout(resolve, 5 * 60 * 1000));
    throw new Error("Timed out waiting for workflow cancellation.");
}
