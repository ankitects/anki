/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */
const express = require('express');
const puppeteer = require('puppeteer');

module.exports = async () => {
    await setupExpress();
    await setupPuppeteer();
};

/**
 * Convert a any object to true/false to be used on puppeteer headless option.
 * @return {boolean} true if value is undefined, 'true', 'yes', has length 0, or is a nonzero.
 *         false if value is 'false', 'no', the zero number or any other string.
 */
function stringToBoolean(value) {
    value = String(value);
    switch(value.toLowerCase().trim()){
        case "true": case "yes": return true;
        case "false": case "no": return false;
        default: {
            if(value.length === 0 || value == "undefined") {
                return true;
            }
            return !!parseInt(value);
        }
    }
}

async function setupExpress() {
    let server;
    const app = express();

    await new Promise(function(resolve) {
        server = app.listen(0, "127.0.0.1", function() {
            let address = server.address();
            process.env.SERVER_ADDRESS = `http://${address.address}:${address.port}`;
            console.log(` Running static file server on '${process.env.SERVER_ADDRESS}'...`);
            resolve();
        });
    });

    global.server = server;
    app.get('/favicon.ico', (req, res) => res.sendStatus(200));
    app.use(express.static('./testfiles'));
    app.use(express.static('../aqt_data/web'));
}

async function setupPuppeteer() {
    let PUPPETEER_SLOWMO = parseInt(process.env.PUPPETEER_SLOWMO) || undefined;
    let PUPPETEER_HEADLESS = stringToBoolean(process.env.PUPPETEER_HEADLESS);
    let PUPPETEER_CHROME_ARGS = process.env.PUPPETEER_CHROME_ARGS;
    let chrome_args = [
        // "--start-maximized",
        // "--window-position=960,10",
        "--autoplay-policy=no-user-gesture-required",
        // Puppeteer with headless:true is extremely slow
        // https://github.com/puppeteer/puppeteer/issues/1718
        "--proxy-server='direct://'",
        '--proxy-bypass-list=*',
    ];

    if(PUPPETEER_CHROME_ARGS) {
        PUPPETEER_CHROME_ARGS = PUPPETEER_CHROME_ARGS.trim();
        if(PUPPETEER_CHROME_ARGS.length > 0) {
            chrome_args.push(...PUPPETEER_CHROME_ARGS.split(' '));
        }
    }

    console.log(` Environment 'slowMo=${PUPPETEER_SLOWMO}' 'headless=${PUPPETEER_HEADLESS}' 'args=${PUPPETEER_CHROME_ARGS}'`);
    let browser = await puppeteer.launch({
        dumpio: true, // https://github.com/puppeteer/puppeteer/issues/4253
        headless: PUPPETEER_HEADLESS, // show the Chrome window
        slowMo: PUPPETEER_SLOWMO, // slow things down e.g. by 250 ms
        ignoreDefaultArgs: [
            "--mute-audio",
        ],
        args: chrome_args,
    });

    // http://${host}:${port}/json/version
    // ws://${host}:${port}/devtools/browser/<id>
    // https://chromedevtools.github.io/devtools-protocol
    process.env.PUPPETEER_BROWSER_ENDPOINT = browser.wsEndpoint();
    browser.disconnect();    
}
