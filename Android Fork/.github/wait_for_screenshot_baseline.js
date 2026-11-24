// SPDX-License-Identifier: GPL-3.0-or-later

module.exports = async function waitForScreenshotBaseline({
    github,
    context,
    core,
    sleep = delay => new Promise(resolve => setTimeout(resolve, delay)),
}) {
    const base = context.payload.pull_request?.base || {
        sha: context.sha,
        ref: context.ref.replace(/^refs\/heads\//, ""),
    };

    // The comparison checkout includes this base commit. A previous successful
    // baseline can incorrectly attribute changes on main to an unrelated PR.
    // The matching store run may still be recording, so wait up to twenty minutes.
    for (let attempt = 0; attempt < 40; attempt++) {
        const {
            data: { workflow_runs: runs },
        } = await github.rest.actions.listWorkflowRuns({
            ...context.repo,
            workflow_id: "screenshot_store.yml",
            branch: base.ref,
            head_sha: base.sha,
            event: "push",
            status: "success",
            per_page: 1,
        });
        if (runs.length) {
            core.info(`Using screenshot baseline from ${base.sha}: run ${runs[0].id}`);
            return runs[0].id;
        }
        core.info(`Waiting for a successful screenshot baseline for ${base.sha}`);
        await sleep(30_000);
    }
    throw new Error(`No successful screenshot baseline for ${base.sha} after twenty minutes`);
};
