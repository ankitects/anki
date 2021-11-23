"use strict";
/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */
// After loading the page, wait a little to ensure all medias are processed
const ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT = 1000;
/**
 * Find all audio and video tags and run them through the callback parameter.
 *
 * @param {Function} callback - to be called on each media.
 * @param {Array}    initial  - an additional list of items to be iterated over.
 */
function setAnkiMedia(callback, initial = undefined) {
    if (arguments.length < 1 || arguments.length > 2) {
        throw new Error(
            `The function setAnkiMedia() requires from 1 up to 2 argument(s) only, not ${arguments.length}!`,
        );
    }
    let items = [];
    if (initial) {
        if (Array.isArray(initial)) {
            items.push(...initial);
        } else {
            throw new Error(
                `The setAnkiMedia() 'initial=${initial}/${typeof initial}' is not a valid array object!`,
            );
        }
    }
    if (typeof callback != "function" && callback != undefined) {
        throw new Error(
            `The setAnkiMedia() 'callback=${callback}/${typeof callback}' is not a valid function!`,
        );
    }
    if (callback.length < 1) {
        throw new Error(
            `The setAnkiMedia() 'callback=${callback}/${typeof callback}' should accept at least 1 argument!`,
        );
    }
    items.push(...Array.from(document.querySelectorAll("audio")));
    items.push(...Array.from(document.querySelectorAll("video")));
    items.forEach((media) => {
        callback(media);
    });
}
class AnkiMediaQueue {
    /**
     * Initialize the attributes to their default values.
     */
    constructor() {
        this._reset = this._reset.bind(this);
        this._debug = this._debug.bind(this);
        this._clearPlayingElement = this._clearPlayingElement.bind(this);
        this._whereIs = this._whereIs.bind(this);
        this._validateWhere = this._validateWhere.bind(this);
        this._validateSpeed = this._validateSpeed.bind(this);
        this._validateSetup = this._validateSetup.bind(this);
        this.add = this.add.bind(this);
        this._checkPreviewPage = this._checkPreviewPage.bind(this);
        this.replay = this.replay.bind(this);
        this._play = this._play.bind(this);
        this._playnext = this._playnext.bind(this);
        this._getMediaElement = this._getMediaElement.bind(this);
        this.setup = this.setup.bind(this);
        this._getSource = this._getSource.bind(this);
        this._fixDuplicates = this._fixDuplicates.bind(this);
        this._setupAudioPlay = this._setupAudioPlay.bind(this);
        this._checkDataAttributes = this._checkDataAttributes.bind(this);
        this._moveAudioElements = this._moveAudioElements.bind(this);
        this.playing_front = [];
        this.playing_back = [];
        this.replay_back_queue = [];
        this.replay_front_queue = [];
        this.other_medias = [];
        this.files = new Map();
        this.medias = new Map();
        this.duplicates = new Map();
        this.frontmedias = new Map();
        this.add_duplicates = new Map();
        this.play_duplicates = new Map();
        this._reset();
    }
    /**
     * Wrapper around `console.log` to easily enable/disable debug messages at once
     * by selecting the same word.
     */
    _debug(...args) {
        console.log(...args);
    }
    _reset(parameters = {}) {
        // this._debug(`_reset parameters '${JSON.stringify(parameters)}'`);
        let { skip_front_reset = false } = parameters;
        this.delay = 0.3;
        this.playing_front.length = 0;
        this.playing_back.length = 0;
        this.replay_back_queue.length = 0;
        this.replay_front_queue.length = 0;
        this.other_medias.length = 0;
        this.files.clear();
        this.medias.clear();
        this.duplicates.clear();
        this.frontmedias.clear();
        this.add_duplicates.clear();
        this.play_duplicates.clear();
        this._add_duplicates_reset = 0;
        this._addall_reset = 0;
        this._addall_last_where = "front";
        if (this._playing_element) {
            this._playing_element.removeEventListener("ended", this._startnext);
        }
        if (this._playing_element_timer) {
            clearTimeout(this._playing_element_timer);
        }
        this._playing_element = new Audio();
        this._playing_element_timer = undefined;
        this._startnext = (event) => {};
        this._clearPlayingElement();
        this.autoplay = true;
        this.is_playing = false;
        this.is_autoplay = false;
        this.is_autoseek = true;
        if (this._is_autoseek_timer) {
            clearTimeout(this._is_autoseek_timer);
        }
        if (this._check_preview_page_timer) {
            clearTimeout(this._check_preview_page_timer);
        }
        this._is_autoseek_timer = undefined;
        this._check_preview_page_timer = undefined;
        this._is_autoseek_callback = () => {};
        this.is_setup = false;
        this.where = "front";
        this.wait_question = true;
        this._answer_element = null;
        this.has_previewed = false;
        if (!skip_front_reset) {
            this.skip_front = false;
        }
    }
    _clearPlayingElement() {
        if (this._playing_element) {
            this._playing_element.removeEventListener("ended", this._startnext);
        }
        if (this._playing_element_timer) {
            clearTimeout(this._playing_element_timer);
        }
        this._playing_element = undefined;
        this._playing_element_timer = undefined;
        this._startnext = (event) => {};
    }
    _whereIs(element) {
        this._validateSetup("_whereIs");
        if (this._answer_element && element) {
            let position = this._answer_element.compareDocumentPosition(element);
            return position & Node.DOCUMENT_POSITION_PRECEDING ? "front" : "back";
        }
        return "front";
    }
    _validateSetup(location) {
        if (!this.is_setup || !location) {
            throw new Error(
                `The ankimedia.setup() function must be called before calling ankimedia.${location}().`,
            );
        }
    }
    _validateWhere(where, caller, media = undefined) {
        let fix_message =
            `Pass ankimedia.${caller}( "file.mp3", "front" ) if this is the question side ` +
            `or ankimedia.${caller}( "file.mp3", "back" ) if this is the answer side.`;
        if (!where) {
            throw new Error(
                `Missing the 'where=${where}' parameter!\n${fix_message} ` +
                    this._getMediaInfo(media),
            );
        }
        if (!(where == "front" || where == "back")) {
            throw new Error(
                `Invalid 'where=${where}' parameter!\n${fix_message} ` +
                    this._getMediaInfo(media),
            );
        }
    }
    _validateSpeed(speed, media = undefined) {
        if (typeof speed != "number" || speed <= 0) {
            throw new Error(
                `The 'speed=${speed}/${typeof speed}' is not a valid positive number.` +
                    this._getMediaInfo(media),
            );
        }
    }
    /**
     * Automatically add all media elements found and start playing them sequentially.
     *
     * @param {string} where - pass "front" if this is being called on the card-front,
     *        otherwise, pass "back" if it is being called on the card-back.
     *        If not specified, each media element to be added automatically can also have a
     *        "data-where" attribute with the value "front" if this is being called on the
     *        card-front, otherwise, the value "back" if it is being called on the card-back.
     * @param {number} speed - the speed to play the audio, where 1.0 is the default speed.
     *        Each media element can also have an attribute as `data-speed="1.0"` indicating
     *        the speed it should play. The `data-speed` value has precedence over this parameter.
     */
    addall(where = undefined, speed = undefined) {
        speed = speed || 1.0;
        if (arguments.length > 2) {
            throw new Error(
                `The function ankimedia.addall() requires from 0 up to 2 argument(s) only, not ${arguments.length}!`,
            );
        }
        this._validateSetup("addall");
        this._validateSpeed(speed);
        if (where) {
            this._validateWhere(where, "addall");
        } else {
            if (
                Date.now() - this._addall_reset > ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT ||
                this._addall_last_where != this.where
            ) {
                this._addall_reset = Date.now();
                this._addall_last_where = this.where;
            } else {
                return;
            }
        }
        if (!where || where == this.where) {
            setAnkiMedia((media) => {
                let localwhere =
                    media.getAttribute("data-where") || this._whereIs(media);
                this._validateWhere(localwhere, "addall", media);
                this._checkDataAttributes(media);
                if (localwhere == "front") {
                    this.frontmedias.set(media.id, 0);
                }
                if (localwhere == "back" && this.frontmedias.has(media.id)) {
                    return;
                }
                this.add(this._getSource(media), localwhere, speed);
            }, this.other_medias);
        }
    }
    /**
     * Add an audio file to the playing queue and immediately starts playing, if not playing
     * already.
     *
     * @param {string} filename - an audio filename for playing.
     * @param {string} where    - pass "front" if this is being called on the card-front,
     *        otherwise, pass "back" as it is being called on the card-back.
     *        If not specified, each media element to be added automatically can also have a
     *        "data-where" attribute with the value "front" if this is being called on the
     *        card-front, otherwise, the value "back" if it is being called on the card-back.
     * @param {number} speed    - the speed to play the audio, where 1.0 is the default speed.
     *        Each media element can also have an attribute as `data-speed="1.0"` indicating
     *        the speed it should play. The `data-speed` value has precedence over this parameter.
     */
    add(filename, where = undefined, speed = undefined) {
        speed = speed || 1.0;
        if (arguments.length < 1 || arguments.length > 3) {
            throw new Error(
                `The function ankimedia.add() requires from 1 up to 3 argument(s) only, not ${arguments.length}!`,
            );
        }
        let media = this._getMediaElement(filename, this.add_duplicates);
        if (media) {
            where = media.getAttribute("data-where") || where || this._whereIs(media);
        }
        this._validateSetup("add");
        if (!(typeof filename == "string")) {
            throw new Error(
                `The 'filename=${filename}/${typeof filename}' is not a valid string. ` +
                    this._getMediaInfo(media),
            );
        }
        filename = filename.trim();
        if (filename.length < 1) {
            console.log(
                `The ${where} 'filename=${filename}' is too short. Not adding this media! ` +
                    this._getMediaInfo(media),
            );
            return;
        }
        this._validateWhere(where, "add", media);
        this._validateSpeed(speed, media);
        // this._debug(`Trying ${filename} ${where} ${this.where}...`);
        if (!this.has_previewed && (this._checkPreviewPage() || where == this.where)) {
            if (where == "front") {
                this.playing_front.push([filename, speed]);
                this.replay_front_queue.push([filename, speed]);
            } else {
                this.playing_back.push([filename, speed]);
                this.replay_back_queue.push([filename, speed]);
            }
            // this._debug(`Adding ${filename} ${where}...`);
            if (this.autoplay) {
                this._play();
            }
        }
    }
    _checkPreviewPage() {
        // avoid continuously playing when previewing/editing the card
        if (document.title == "card layout") {
            let block_preview = () => {
                this.has_previewed = true;
                this._check_preview_page_timer = undefined;
            };
            block_preview = block_preview.bind(this);
            if (document.readyState == "complete") {
                this._check_preview_page_timer = setTimeout(
                    block_preview,
                    ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT,
                );
            } else {
                document.addEventListener("DOMContentLoaded", function () {
                    this._check_preview_page_timer = setTimeout(
                        block_preview,
                        ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT,
                    );
                });
            }
            return true;
        } else if (document.title === "previewer") {
            return true;
        }
        return false;
    }
    replay() {
        // this._debug(`replay '${this.is_playing}'`);
        if (this._is_autoseek_timer) {
            clearTimeout(this._is_autoseek_timer);
            this._is_autoseek_callback();
        }
        let is_autoseek = this.is_autoseek;
        this.is_autoseek = false;
        try {
            if (this._playing_element) {
                this._playing_element.pause();
                this._playing_element.currentTime = 0;
                this._clearPlayingElement();
            }
            // this._debug(`play_duplicates ${Array.from(this.play_duplicates.values())}`);
            this.play_duplicates.clear();
            this.playing_front.length = 0;
            this.playing_back.length = 0;
            setAnkiMedia((media) => {
                media.pause();
                media.currentTime = 0;
            }, this.other_medias);
            this.playing_front.push(...this.replay_front_queue);
            this.playing_back.push(...this.replay_back_queue);
            this.is_playing = false;
            this._play();
        } finally {
            this._is_autoseek_callback = () => {
                this.is_autoseek = is_autoseek;
                this._is_autoseek_timer = undefined;
                this._is_autoseek_callback = () => {};
            };
            this._is_autoseek_callback = this._is_autoseek_callback.bind(this);
            this._is_autoseek_timer = setTimeout(
                this._is_autoseek_callback,
                ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT,
            );
        }
    }
    _play() {
        // this._debug(`_play '${this.is_playing}'`);
        if (this.is_playing) {
            return;
        }
        // this._debug(`queues ${this.replay_front_queue} ${this.replay_back_queue}`);
        this.is_playing = true;
        if (this.where == "front") {
            // this._debug(`play_duplicates ${Array.from(this.play_duplicates.values())}`);
            this.play_duplicates.clear();
        }
        this._playnext();
    }
    _playnext() {
        this._playing_element_timer = undefined;
        let filename = undefined;
        let speed = undefined;
        let media = undefined;
        while (true) {
            let is_front = false;
            let first = this.playing_front.shift();
            if (first) {
                is_front = true;
            } else {
                first = this.playing_back.shift();
            }
            if (first) {
                filename = first[0];
                speed = first[1];
                media = this._getMediaElement(filename, this.play_duplicates);
                // this._debug(`Playing ${this.skip_front} ${!!media} '${filename}'...`);
                if (!media) {
                    media = new Audio(filename);
                }
            }
            if (media && this.skip_front && is_front) {
                media = undefined;
                continue;
            }
            break;
        }
        if (media) {
            let data_speed = media.getAttribute("data-speed") || speed;
            media.playbackRate = parseFloat(data_speed);
            this.is_autoplay = true;
            let playpromise = media.play();
            if (playpromise) {
                playpromise.catch((error) =>
                    console.log(
                        `Could not play '${filename}' due to '${error}'! ` +
                            this._getMediaInfo(media),
                    ),
                );
            } else {
                console.log(
                    `Could not play the media '${filename}'! ` +
                        this._getMediaInfo(media),
                );
            }
            this._startnext = (event) => {
                if (this.playing_back.length || this.playing_back.length) {
                    this._playing_element_timer = setTimeout(
                        this._playnext,
                        this.delay * 1000,
                    );
                } else {
                    this._playnext();
                }
            };
            this._playing_element = media;
            this._startnext = this._startnext.bind(this);
            media.addEventListener("ended", this._startnext, { once: true });
        } else {
            this.is_playing = false;
            this._playing_element = undefined;
        }
    }
    _getMediaInfo(media) {
        let results = "";
        if (media) {
            results += "id=" + String(media.id) + "|";
            results += "src=" + String(media.src) + "|";
            if (media.children) {
                let children = media.children;
                for (var index = 0; index < children.length; index++) {
                    let child = children[index];
                    results += "source=" + String(child.src) + "|";
                }
            }
            results += "data-id=" + String(media.getAttribute("data-id")) + "|";
            results += "data-speed=" + String(media.getAttribute("data-speed")) + "|";
        } else {
            results += String(media);
        }
        return results;
    }
    _getMediaElement(filename, selected) {
        let media = this.files.get(filename);
        if (media) {
            // if duplicate elements were found, select the one in a specific index
            let count = this.duplicates.get(filename);
            let index = selected.get(filename) || 0;
            selected.set(filename, index + 1);
            // this._debug(`_getMediaElement count ${count} index ${index} ${media.id} ${filename}...`);
            if (index <= count) {
                let last_id = filename + index.toString();
                let last_media = this.files.get(last_id);
                if (last_media) {
                    media = last_media;
                }
            }
            this._checkDataAttributes(media);
        }
        return media;
    }
    /**
     * Call this on your front-card before adding new medias to the playing queue.
     * You can call this function as `setup({delay: 5, wait: false})`.
     *
     * @param {number} delay   - how many seconds to time to wait before playing the next audio.
     * @param {boolean} wait   - if true (default), wait the question audio to play
     *        when the answer was showed before it had finished playing.
     * @param {function} extra - a function(media) to be run on each media of the page.
     * @param {array} medias   - an array of initial values to be passed to setAnkiMedia() calls.
     * @param {boolean} auto   - if true (default), auto-play the media elements.
     * @param {boolean} skip   - if true (default), it will skip playing the front media.
     */
    setup(parameters = {}) {
        // this._debug(`setup parameters '${JSON.stringify(parameters)}'`);
        let default_parameters = {
            delay: this.delay,
            wait: this.wait_question,
            extra: undefined,
            medias: this.other_medias,
            auto: this.autoplay,
            skip: this.skip_front,
        };
        let {
            delay = default_parameters.delay,
            wait = default_parameters.wait,
            extra = default_parameters.extra,
            medias = default_parameters.medias,
            auto = default_parameters.auto,
            skip = default_parameters.skip,
        } = parameters;
        if (typeof parameters != "object") {
            throw new Error(
                `Invalid 'parameters=${parameters}/${typeof parameters}' passed to setup!`,
            );
        }
        for (let [key, value] of Object.entries(parameters)) {
            if (!(key in default_parameters)) {
                throw new Error(
                    `Invalid 'parameters=${key}-${value}/${typeof key}' passed to setup!`,
                );
            }
        }
        if (!Array.isArray(medias)) {
            throw new Error(
                `The 'medias=${medias}/${typeof medias}' is not a valid array object!`,
            );
        }
        if (typeof delay != "number" || delay < 0) {
            throw new Error(
                `The 'delay=${delay}/${typeof delay}' is not a valid positive number!`,
            );
        }
        if (typeof extra != "function" && extra != undefined) {
            throw new Error(
                `The 'extra=${extra}/${typeof extra}' is not a valid function!`,
            );
        }
        let answerids = document.querySelectorAll("[id^=answer]");
        this._answer_element = answerids ? answerids[0] : null;
        this.is_setup = true;
        this.where = this._answer_element ? "back" : "front";
        this.delay = delay;
        this.wait_question = wait;
        this.other_medias = medias;
        this.autoplay = auto;
        this.skip_front = skip;
        if (
            this.where == "back" &&
            Date.now() - this._add_duplicates_reset > ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT
        ) {
            this.add_duplicates.clear();
            this._add_duplicates_reset = Date.now();
        }
        this._fixDuplicates();
        this._moveAudioElements(extra);
    }
    _getSource(media) {
        let source = media.getAttribute("src");
        if (!source && media.firstChild && media.firstChild.getAttribute) {
            source = media.firstChild.getAttribute("src");
        }
        return source;
    }
    _fixDuplicates() {
        let selected = new Map();
        this.duplicates.clear();
        setAnkiMedia((media) => {
            this._checkDataAttributes(media);
            let data_file = this._getSource(media);
            // Automatically gives an object.id to every media file, if they do not have one.
            if (!media.id) {
                media.id = data_file;
            }
            // Remove duplicated element ids
            let media_id = media.id;
            if (selected.has(media_id)) {
                let index = selected.get(media_id) + 1;
                media.id = media_id + index.toString();
                selected.set(media_id, index);
            } else {
                selected.set(media_id, 0);
            }
            // Create the `data-id` attribute allowing the correct audio element to be
            // selected by the automatic media playing
            if (this.duplicates.has(data_file)) {
                let index = this.duplicates.get(data_file) + 1;
                this.duplicates.set(data_file, index);
                media.setAttribute("data-id", data_file + index.toString());
            } else {
                this.duplicates.set(data_file, 0);
                media.setAttribute("data-id", data_file);
            }
        }, this.other_medias);
    }
    _checkDataAttributes(media) {
        let data_file = this._getSource(media);
        let data_speed = media.getAttribute("data-speed");
        if (typeof data_file != "string") {
            throw new Error(
                `A media element is missing its 'src=${data_file}' attribute. ` +
                    this._getMediaInfo(media),
            );
        }
        let timmed_data_file = data_file.trim();
        if (timmed_data_file != data_file) {
            throw new Error(
                `A media element has leading or trailing whitespaces on its 'src=${data_file}' attribute. ` +
                    this._getMediaInfo(media),
            );
        }
        if (
            data_speed != undefined &&
            (typeof data_speed != "string" || isNaN(data_speed))
        ) {
            throw new Error(
                `A media element has an invalid 'data-speed=${data_speed}/${typeof data_speed}' attribute. ` +
                    this._getMediaInfo(media),
            );
        }
        this._validateSpeed(parseFloat(data_speed), media);
    }
    _moveAudioElements(extra) {
        this.files.clear();
        // Move all audio elements into this object to avoid the audio from stopping
        // when the answer is showed.
        setAnkiMedia((media) => {
            let clone;
            if (this.medias.has(media.id)) {
                clone = this.medias.get(media.id);
                if (this.wait_question) {
                    media.parentNode.replaceChild(clone, media);
                } else if (!clone.paused) {
                    this.is_playing = false;
                }
            } else {
                clone = media.cloneNode(true);
                this.medias.set(clone.id, clone);
                media.parentNode.replaceChild(clone, media);
                this._setupAudioPlay(media, clone);
            }
            let data_id = clone.getAttribute("data-id");
            this.files.set(data_id, clone);
            if (extra) {
                extra(media);
                extra(clone);
            }
        }, this.other_medias);
    }
    _setupAudioPlay(media, clone) {
        // Set to automatically play the audio when seeking the progress bar.
        let auto_play = (target) => {
            return (event) => {
                if (this.is_autoseek) {
                    target.play();
                }
            };
        };
        media.addEventListener("seeked", auto_play(media));
        clone.addEventListener("seeked", auto_play(clone));
        // Set to automatically pause all other medias when playing a new media.
        let auto_pause = (target) => {
            return (event) => {
                // only clear the queue if the play event was from an user action
                if (!this.is_autoplay) {
                    this.playing_front.length = 0;
                    this.playing_back.length = 0;
                }
                this.is_autoplay = false;
                setAnkiMedia((media) => {
                    if (media.id != target.id) {
                        media.pause();
                    }
                }, this.other_medias);
            };
        };
        media.addEventListener("play", auto_pause(media));
        clone.addEventListener("play", auto_pause(clone));
    }
}
var ankimedia = new AnkiMediaQueue();
// @ts-ignore: Allow jest to import this and do unit tests
if (typeof exports != "undefined") {
    // @ts-ignore
    module.exports = {
        setAnkiMedia,
        AnkiMediaQueue,
        ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT,
    };
}
