/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */
/// <reference path="./reviewer.ts" />

import "./ankimedia";
const { ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT } = require("./ankimedia");

// Use `anki\qt\ts> npm test -- reviewer-exceptions.test.ts` to run a single test file
jest.disableAutomock();
jest.setTimeout(30000);

// Use this in a test to pause its execution, allowing you to open the chrome console
// and while keeping the express server running: chrome://inspect/#devices
// jest.setTimeout(2000000000);
// debugger; await new Promise(function(resolve) {});

describe("Test question and answer audios", () => {
    let self: any = global;
    let page = self.page;
    let address = process.env.SERVER_ADDRESS;

    var cardTemplate = (media_tag, setup_code, first_text) => {
        return `${first_text}
        <input type="button" value="x0.6" onclick="setAnkiMedia( media => { media.playbackRate = 0.6; } )">
        <input type="button" value="x10.6" onclick="setAnkiMedia( media => { media.playbackRate = 10.6; } )">
        ${media_tag}
        <script type="text/javascript">
            ankimedia.setup( {extra: media => {
                media.playbackRate = 4.0;
                if( !media.getAttribute("data-has-tests-extra-events") ) {
                    media.setAttribute("data-has-tests-extra-events", true);
                    media.addEventListener( "play", event => media.setAttribute( "data-has-started-at", Date.now() ) );
                    media.addEventListener( "ended", event => media.setAttribute( "data-has-ended-at", Date.now() ) );
                }
            }});
            ${setup_code}
        </script>`;
    };

    var questionTemplate = (file_name, setup_code) => {
        return cardTemplate(
            `<audio data-file="${file_name}" controlslist="nodownload" controls></audio>`,
            setup_code,
            `What is the past simple of the verb to bumb?<br>`
        );
    };

    var answerTemplate = (file_name, setup_code) => {
        return cardTemplate(
            `<audio data-file="${file_name}" controlslist="nodownload" controls></audio>`,
            setup_code,
            `<hr id="answer">The past simple is to boobs.<br>`
        );
    };

    var dataSpeedTemplate = (file_name, setup_code) => {
        return cardTemplate(
            `<audio data-file="${file_name}" data-speed="5" controlslist="nodownload" controls></audio>`,
            setup_code,
            `What is the past simple of the verb to bumb?<br>`
        );
    };

    let showQuestion = async (front_mp3, front_setup, templateName) =>
        await page.evaluate(
            async (mp3, setup, template) =>
                _showQuestion(await eval(`${template}(mp3, setup)`), ""),
            front_mp3,
            front_setup,
            templateName
        );

    let questionAndAnswer = async (
        question_mp3,
        question_setup,
        answer_mp3,
        answer_setup
    ) => {
        await page.evaluate(
            async (question_mp3, question_setup, answer_mp3, answer_setup) => {
                _showAnswer(
                    `${await questionTemplate(
                        question_mp3,
                        question_setup
                    )}${await answerTemplate(answer_mp3, answer_setup)}`,
                    ""
                );
            },
            question_mp3,
            question_setup,
            answer_mp3,
            answer_setup
        );
    };

    let getPausedMedias = async () =>
        await page.evaluate(async () => {
            let paused_medias = 0;
            let has_medias = 0;
            setAnkiMedia(media => {
                has_medias += 1;
                if (media.paused) {
                    paused_medias += 1;
                }
            });
            return has_medias ? has_medias - paused_medias : -111111;
        });

    let getPlayTimes = async mp3file =>
        await page.evaluate(async mp3 => {
            let audio = document.getElementById(mp3) as HTMLAudioElement;
            return [
                audio.getAttribute("data-has-started-at"),
                audio.getAttribute("data-has-ended-at"),
            ];
        }, mp3file);

    let getAudioSource = async mp3file =>
        await page.evaluate(async mp3 => {
            let audio = document.getElementById(mp3) as HTMLAudioElement;
            return audio.src;
        }, mp3file);

    (async () => await page.exposeFunction("cardTemplate", cardTemplate))();
    (async () => await page.exposeFunction("questionTemplate", questionTemplate))();
    (async () => await page.exposeFunction("answerTemplate", answerTemplate))();
    (async () => await page.exposeFunction("dataSpeedTemplate", dataSpeedTemplate))();

    beforeEach(async () => {
        await page.goto(`${address}/main_webview.html`);
        await page.waitForSelector(`[id="qa"]`);
    });

    test.each([
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.addall( "front" );`,
            `questionTemplate`,
        ],
        [
            `silence 1.mp3`,
            `ankimedia.setup({delay: 0, wait: false, medias: []}); ankimedia.addall( "front" );`,
            `questionTemplate`,
        ],
        [
            `silence 2.mp3`,
            `ankimedia.setup(); ankimedia.add( "front", "silence 2.mp3" );`,
            `questionTemplate`,
        ],
        [
            `silence 2.mp3`,
            `ankimedia.setup({delay: 0, wait: false, medias: []}); ankimedia.add( "front", "silence 2.mp3" );`,
            `questionTemplate`,
        ],
        [
            `silence 2.mp3`,
            `ankimedia.setup({delay: 0, wait: false, medias: []}); ankimedia.add( "front", "silence 2.mp3" );`,
            `dataSpeedTemplate`,
        ],
    ])(
        `Showing a question should play its audio file automatically:\nfront %s '%s'\n...`,
        async function(front_mp3, front_setup, templateName) {
            await showQuestion(front_mp3, front_setup, templateName);
            await page.waitForSelector(`audio[id="${front_mp3}"][data-has-ended-at]`);
            expect(await getPausedMedias()).toEqual(0);

            let question_times = await getPlayTimes(front_mp3);
            let audio_src = await getAudioSource(front_mp3);

            expect(audio_src.replace(/%20/g, " ")).toEqual(`${address}/${front_mp3}`);
            expect(question_times[0] < question_times[1]).toEqual(true);
        }
    );

    test.each([
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.addall( "front" );`,
            `silence 2.mp3`,
            `ankimedia.setup(); ankimedia.addall( "front" );`,
        ],
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.add( "front", "silence 1.mp3" );`,
            `silence 2.mp3`,
            `ankimedia.setup(); ankimedia.add( "front", "silence 2.mp3" );`,
        ],
    ])(
        `Showing a new question should play its audio automatically:\nfront %s '%s',\nrefront %s '%s'\n...`,
        async function(front_mp3, front_setup, refront_mp3, refront_setup) {
            await showQuestion(front_mp3, front_setup, "questionTemplate");
            await page.waitForSelector(`audio[id="${front_mp3}"][data-has-ended-at]`);
            expect(await getPausedMedias()).toEqual(0);

            let first_question_times = await getPlayTimes(front_mp3);
            let first_audio_src = await getAudioSource(front_mp3);

            await showQuestion(refront_mp3, refront_setup, "questionTemplate");
            await page.waitForSelector(`[id="${refront_mp3}"][data-has-ended-at]`);
            expect(await getPausedMedias()).toEqual(0);

            let second_question_times = await getPlayTimes(refront_mp3);
            let second_audio_src = await getAudioSource(refront_mp3);

            expect(first_audio_src.replace(/%20/g, " ")).toEqual(
                `${address}/${front_mp3}`
            );
            expect(second_audio_src.replace(/%20/g, " ")).toEqual(
                `${address}/${refront_mp3}`
            );

            expect(second_question_times[0] < second_question_times[1]).toEqual(true);
            expect(first_question_times[0] < second_question_times[0]).toEqual(true);
            expect(first_question_times[1] < second_question_times[1]).toEqual(true);
        }
    );

    test.each([
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.addall( "front" );`,
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.addall( "back" );`,
        ],
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.addall( "front" );`,
            `silence 2.mp3`,
            `ankimedia.setup(); ankimedia.addall( "back" );`,
        ],
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.add( "front", "silence 1.mp3" );`,
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.add( "back", "silence 1.mp3" );`,
        ],
        [
            `silence 1.mp3`,
            `ankimedia.setup(); ankimedia.add( "front", "silence 1.mp3" );`,
            `silence 2.mp3`,
            `ankimedia.setup(); ankimedia.add( "back", "silence 2.mp3" );`,
        ],
    ])(
        `Showing an answer with the same id as the question should only play the answer audio:\nfront %s '%s',\nback %s '%s'\n...`,
        async function(front_mp3, front_setup, back_mp3, back_setup) {
            let selector = back_mp3 == front_mp3 ? `${front_mp3}1` : back_mp3;

            await showQuestion(front_mp3, front_setup, "questionTemplate");
            await page.waitForSelector(`audio[id="${front_mp3}"][data-has-ended-at]`);
            expect(await getPausedMedias()).toEqual(0);

            let question_times = await getPlayTimes(front_mp3);
            let question_audio_src = await getAudioSource(front_mp3);

            await questionAndAnswer(front_mp3, front_setup, back_mp3, back_setup);
            await page.waitForSelector(`audio[id="${selector}"][data-has-ended-at]`);
            expect(await getPausedMedias()).toEqual(0);

            let question_times_recheck = await getPlayTimes(front_mp3);
            let answer_times = await getPlayTimes(selector);
            let answer_audio_src = await getAudioSource(selector);

            expect(question_audio_src.replace(/%20/g, " ")).toEqual(
                `${address}/${front_mp3}`
            );
            expect(answer_audio_src.replace(/%20/g, " ")).toEqual(
                `${address}/${back_mp3}`
            );

            // assert the question audio was not replayed when showing the answer
            expect(question_times[0] == question_times_recheck[0]).toEqual(true);
            expect(question_times[1] == question_times_recheck[1]).toEqual(true);

            expect(question_times[0] < question_times[1]).toEqual(true);
            expect(answer_times[0] < answer_times[1]).toEqual(true);
            expect(question_times[0] < answer_times[0]).toEqual(true);
            expect(question_times[1] < answer_times[1]).toEqual(true);
        }
    );

    test(`The card preview should not play multiple times while editing the page\n...`, async function() {
        await page.goto(`${address}/card_layout_back.html`);
        await page.waitForSelector(`[id="qa"]`);

        let showEverything = async (first_mp3, second_mp3) =>
            await questionAndAnswer(
                first_mp3,
                `ankimedia.setup({delay: 0}); ankimedia.addall( "front" );`,
                second_mp3,
                `ankimedia.setup({delay: 0}); ankimedia.addall( "back" );`
            );

        await showEverything("silence 1.mp3", "silence 2.mp3");
        await page.waitForSelector(`audio[id="silence 2.mp3"][data-has-ended-at]`);
        let question_times = await getPlayTimes("silence 1.mp3");
        let answer_times = await getPlayTimes("silence 2.mp3");

        expect(await getPausedMedias()).toEqual(0);
        expect(question_times[0] < question_times[1]).toEqual(true);
        expect(answer_times[0] < answer_times[1]).toEqual(true);
        expect(question_times[0] < answer_times[0]).toEqual(true);
        expect(question_times[1] < answer_times[1]).toEqual(true);

        await page.waitFor(ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT);
        await showEverything("silence 1.mp3", "silence 2.mp3");
        await page.waitForSelector(`audio[id="silence 2.mp3"]`);
        expect(await getPausedMedias()).toEqual(0);

        await showEverything("silence 1.mp3", "silence 2.mp3");
        await page.waitForSelector(`audio[id="silence 2.mp3"]`);
        expect(await getPausedMedias()).toEqual(0);

        await showEverything("silence 1.mp3", "silence 2.mp3");
        await page.waitForSelector(`audio[id="silence 2.mp3"]`);
        expect(await getPausedMedias()).toEqual(0);
    });
});
