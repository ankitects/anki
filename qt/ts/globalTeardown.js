/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */
const puppeteer = require('puppeteer');

module.exports = async () => {
    global.server.close();
    let PUPPETEER_BROWSER_ENDPOINT = process.env.PUPPETEER_BROWSER_ENDPOINT;

    if(PUPPETEER_BROWSER_ENDPOINT) {
        let browser = await puppeteer.connect({browserWSEndpoint: PUPPETEER_BROWSER_ENDPOINT});
        await browser.close();
    }
};
