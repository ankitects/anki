// Fetch results to parse like this, with the workflow run id you want:
// curl https://api.github.com/repos/mikehardy/Anki-Android/actions/runs/2210525974/jobs?per_page=100 > emulator_perf_results.json

// Or if you have more than 100 results, you need to page through them and merge them, there is a script
// ./fetch_workflow_jobs_json.sh 2212862357

function main() {
    // Read in the results
    // console.log("Processing results in " + process.argv[2]);
    var fs = require("fs");
    var runLog = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));

    console.log(
        '"Android API","Emulator Architecture","Emulator Image","First Boot Warmup Delay","Average AVD Create/Boot Elapsed Seconds","Average AVD Reboot/Test Elapsed Seconds","Average Total Elapsed Seconds","Failure Count"',
    );

    let averageTimings = {};

    runLog.jobs.forEach(job => {
        // console.log("analyzing job " + job.name);
        const matrixVars = job.name.match(/.*\((.*)\)/)[1].split(", ");
        // console.log("Job name: " + job.name);
        // console.log("  Android API level: " + matrixVars[0]);
        // console.log("  Emulator Architecture: " + matrixVars[1]);
        // console.log("  Emulator Image: " + matrixVars[2]);

        const startTime = new Date(job.started_at);
        const endTime = new Date(job.completed_at);
        let jobElapsed = endTime - startTime;
        jobElapsed = jobElapsed > 0 ? jobElapsed : 0; // some are negative !?

        // console.log("  conclusion: " + job.conclusion);
        // console.log("  elapsed_time_seconds: " + jobElapsed / 1000);

        let AVDCreateBootElapsedSeconds = -1;
        let AVDRebootTestElapsedSeconds = -1;
        let stepFailed = false;

        job.steps.forEach(step => {
            if (!["success", "skipped"].includes(step.conclusion)) {
                stepFailed = true;
                return;
            }
            const stepStart = new Date(step.started_at);
            const stepEnd = new Date(step.completed_at);
            let stepElapsedSeconds = (stepEnd - stepStart) / 1000;
            stepElapsedSeconds = stepElapsedSeconds > 0 ? stepElapsedSeconds : 0; // some are negative !?

            switch (step.name) {
                case "AVD Boot and Snapshot Creation":
                    AVDCreateBootElapsedSeconds = stepElapsedSeconds;
                case "Run Emulator Tests":
                    AVDRebootTestElapsedSeconds = stepElapsedSeconds;
            }
        });

        // Get or create aggregate timing entry
        timingKey = `${matrixVars[0]}_${matrixVars[1]}_${matrixVars[2]}_${matrixVars[3]}`;
        let currentAverageTiming = averageTimings[timingKey];
        if (currentAverageTiming === undefined) {
            currentAverageTiming = {
                api: matrixVars[0],
                arch: matrixVars[1],
                target: matrixVars[2],
                warmtime: matrixVars[3],
                totalCreateBootElapsedSecs: 0,
                totalTestElapsedSecs: 0,
                runs: 0,
                failureCount: 0,
            };
            averageTimings[timingKey] = currentAverageTiming;
        }

        // If something failed, set status and skip timing aggregation
        if (stepFailed) {
            currentAverageTiming.failureCount++;
            return;
        }

        // Update our aggregate timings
        currentAverageTiming.totalCreateBootElapsedSecs += AVDCreateBootElapsedSeconds;
        currentAverageTiming.totalTestElapsedSecs += AVDRebootTestElapsedSeconds;
        currentAverageTiming.runs++;
    });

    // Print out averages for each non-iteration combo
    Object.keys(averageTimings).forEach(key => {
        // console.log("printing timings for key " + key);
        const timing = averageTimings[key];
        // console.log("entry is " + JSON.stringify(timing));
        console.log(
            `"${timing.api}","${timing.arch}","${timing.target}","${timing.warmtime}","${
                timing.totalCreateBootElapsedSecs / timing.runs
            }","${timing.totalTestElapsedSecs / timing.runs}","${
                (timing.totalCreateBootElapsedSecs + timing.totalTestElapsedSecs) / timing.runs
            }","${timing.failureCount}"`,
        );
    });
}

main();
