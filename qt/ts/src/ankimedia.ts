/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

// After loading the page, wait a little to ensure all medias are processed
const ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT = 500;

/**
 * Find all audio and video tags and run them through the callback parameter.
 *
 * @param {Function} callback - to be called on each media.
 * @param {Array}    initial  - an additional list of items to be iterated over.
 */
function setAnkiMedia(callback, initial = undefined) {
    if (arguments.length < 1 || arguments.length > 2) {
        throw new Error(
            `The function setAnkiMedia() requires from 1 up to 2 argument(s) only, not ${arguments.length}!`
        );
    }
    let items = [];
    if (initial) {
        if (Array.isArray(initial)) {
            items.push(...initial);
        } else {
            throw new Error(
                `The setAnkiMedia() 'initial=${initial}/${typeof initial}' is not a valid array object!`
            );
        }
    }

    if (typeof callback != "function" && callback != undefined) {
        throw new Error(
            `The setAnkiMedia() 'callback=${callback}/${typeof callback}' is not a valid function!`
        );
    }
    if (callback.length < 1) {
        throw new Error(
            `The setAnkiMedia() 'callback=${callback}/${typeof callback}' should accept at least 1 argument!`
        );
    }
    items.push(...Array.from(document.querySelectorAll("audio")));
    items.push(...Array.from(document.querySelectorAll("video")));
    items.forEach(media => {
        callback(media);
    });
}

class AnkiMediaQueue {
    delay: number;
    playing: Array<[string, number]>;
    other_medias: Array<any>;
    files: Map<string, HTMLAudioElement>;
    medias: Map<string, HTMLAudioElement>;
    duplicates: Map<string, number>;
    frontmedias: Map<string, number>;
    add_duplicates: Map<string, number>;
    _add_duplicates_reset: number;
    _addall_reset: number;
    _addall_last_where: "front" | "back";
    play_duplicates: Map<string, number>;
    autoplay: boolean;
    is_playing: boolean;
    is_autoplay: boolean;
    is_first: boolean;
    is_setup: boolean;
    where: "front" | "back";
    _answer_element: HTMLElement | null;
    wait_question: boolean;
    has_previewed: boolean;
    skip_front: boolean;

    /**
     * Initialize the attributes to their default values.
     */
    constructor() {
        this._reset = this._reset.bind(this);
        this._whereIs = this._whereIs.bind(this);
        this._validateWhere = this._validateWhere.bind(this);
        this._validateSpeed = this._validateSpeed.bind(this);
        this._validateSetup = this._validateSetup.bind(this);
        this.add = this.add.bind(this);
        this._checkPreviewPage = this._checkPreviewPage.bind(this);
        this._play = this._play.bind(this);
        this._playnext = this._playnext.bind(this);
        this._getMediaElement = this._getMediaElement.bind(this);
        this.setup = this.setup.bind(this);
        this._fixDuplicates = this._fixDuplicates.bind(this);
        this._setupAudioPlay = this._setupAudioPlay.bind(this);
        this._checkDataAttributes = this._checkDataAttributes.bind(this);
        this._moveAudioElements = this._moveAudioElements.bind(this);

        this.playing = [];
        this.other_medias = [];
        this.files = new Map();
        this.medias = new Map();
        this.duplicates = new Map();
        this.frontmedias = new Map();
        this.add_duplicates = new Map();
        this.play_duplicates = new Map();
        this._reset();
    }

    _reset() {
        this.delay = 0.3;
        this.playing.length = 0;
        this.other_medias.length = 0;
        // force any old media to eject/stop loading: https://goo.gl/LdLk22
        // You can entirely reset the playback state, including the buffer, with load() and src = ''
        for (let media of this.medias.values()) {
            media.src = "";
        }
        this.files.clear();
        this.medias.clear();
        this.duplicates.clear();
        this.frontmedias.clear();
        this.add_duplicates.clear();
        this.play_duplicates.clear();
        this._add_duplicates_reset = 0;
        this._addall_reset = 0;
        this._addall_last_where = "front";
        this.autoplay = true;
        this.is_playing = false;
        this.is_autoplay = false;
        this.is_first = false;
        this.is_setup = false;
        this.where = "front";
        this.wait_question = true;
        this._answer_element = null;
        this.has_previewed = false;
        this.skip_front = false;
    }

    _whereIs(element: HTMLElement): "front" | "back" {
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
                `The ankimedia.setup() function must be called before calling ankimedia.${location}().`
            );
        }
    }

    _validateWhere(where, caller) {
        let fix_message =
            `Pass ankimedia.${caller}( "file.mp3", "front" ) if this is the question side ` +
            `or ankimedia.${caller}( "file.mp3", "back" ) if this is the answer side.`;
        if (!where) {
            throw new Error(`Missing the 'where=${where}' parameter!\n${fix_message}`);
        }
        if (!(where == "front" || where == "back")) {
            throw new Error(`Invalid 'where=${where}' parameter!\n${fix_message}`);
        }
    }

    _validateSpeed(speed) {
        if (typeof speed != "number" || speed <= 0) {
            throw new Error(
                `The 'speed=${speed}/${typeof speed}' is not a valid positive number.`
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
                `The function ankimedia.addall() requires from 0 up to 2 argument(s) only, not ${arguments.length}!`
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
            setAnkiMedia(media => {
                let localwhere =
                    media.getAttribute("data-where") || this._whereIs(media);
                this._validateWhere(localwhere, "addall");
                this._checkDataAttributes(media);

                if (localwhere == "front") {
                    this.frontmedias.set(media.id, 0);
                }
                if (localwhere == "back" && this.frontmedias.has(media.id)) {
                    return;
                }

                this.add(media.getAttribute("data-file"), localwhere, speed);
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
                `The function ankimedia.add() requires from 1 up to 3 argument(s) only, not ${arguments.length}!`
            );
        }

        let media = this._getMediaElement(filename, this.add_duplicates);
        if (media) {
            where = media.getAttribute("data-where") || where || this._whereIs(media);
        }
        this._validateSetup("add");
        this._validateWhere(where, "add");
        this._validateSpeed(speed);

        if (!(typeof filename == "string")) {
            throw new Error(
                `The 'filename=${filename}/${typeof filename}' is not a valid string.`
            );
        }
        if (filename.length < 1) {
            console.log(
                `The ${where} 'filename=${filename}' is too short. Not adding this media!`
            );
            return;
        }

        if (this.skip_front && where == "front") {
            return;
        }

        // console.log(`Trying ${filename} ${where} ${this.where}...`);
        if (!this.has_previewed && (this._checkPreviewPage() || where == this.where)) {
            this.playing.push([filename, speed]);

            // console.log(`Adding ${filename} ${where}...`);
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
            };
            if (document.readyState == "complete") {
                setTimeout(block_preview, ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT);
            } else {
                document.addEventListener("DOMContentLoaded", function() {
                    setTimeout(block_preview, ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT);
                });
            }
            return true;
        }
        return false;
    }

    _play() {
        if (this.is_playing) {
            return;
        }

        this.is_playing = true;
        this.is_first = true;
        this.play_duplicates.clear();
        this._playnext();
    }

    _playnext() {
        if (this.playing.length > 0) {
            let is_first = this.is_first;
            let first = this.playing.shift();
            let filename = first[0];
            let speed = first[1];
            let media = this._getMediaElement(filename, this.play_duplicates);

            if (!media) {
                media = new Audio(filename);
            }
            let data_speed = media.getAttribute("data-speed") || speed;

            media.playbackRate = parseFloat(data_speed as any);
            this.is_autoplay = true;
            let playpromise = media.play();
            if (playpromise) {
                playpromise.catch(error =>
                    console.log(`Could not play '${filename}' due to '${error}'!`)
                );
            } else {
                console.log(`Could not play the media '${filename}'!`);
            }
            media.addEventListener(
                "ended",
                () => {
                    setTimeout(this._playnext, is_first ? this.delay * 1000 : 0);
                },
                { once: true }
            );
        } else {
            this.is_playing = false;
        }
        this.is_first = false;
    }

    _getMediaElement(filename: string, selected: Map<string, number>) {
        let media = this.files.get(filename);
        if (media) {
            // if duplicate elements were found, select the one in a specific index
            let count = this.duplicates.get(filename);
            let index = selected.get(filename) || 0;
            selected.set(filename, index + 1);

            // console.log(`_getMediaElement count ${count} index ${index} ${media.id} ${filename}...`);
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
     */
    setup(parameters: any = {}) {
        let default_parameters = {
            delay: this.delay,
            wait: this.wait_question,
            extra: undefined,
            medias: this.other_medias,
            auto: this.autoplay,
        };
        let {
            delay = default_parameters.delay,
            wait = default_parameters.wait,
            extra = default_parameters.extra,
            medias = default_parameters.medias,
            auto = default_parameters.auto,
        } = parameters;

        if (typeof parameters != "object") {
            throw new Error(
                `Invalid 'parameters=${parameters}/${typeof parameters}' passed to setup!`
            );
        }
        for (let [key, value] of Object.entries(parameters)) {
            if (!(key in default_parameters)) {
                throw new Error(
                    `Invalid 'parameters=${key}-${value}/${typeof key}' passed to setup!`
                );
            }
        }

        if (!Array.isArray(medias)) {
            throw new Error(
                `The 'medias=${medias}/${typeof medias}' is not a valid array object!`
            );
        }
        if (typeof delay != "number" || delay < 0) {
            throw new Error(
                `The 'delay=${delay}/${typeof delay}' is not a valid positive number!`
            );
        }
        if (typeof extra != "function" && extra != undefined) {
            throw new Error(
                `The 'extra=${extra}/${typeof extra}' is not a valid function!`
            );
        }

        this._answer_element = document.getElementById("answer");
        this.is_setup = true;
        this.where = this._answer_element ? "back" : "front";
        this.delay = delay;
        this.wait_question = wait;
        this.other_medias = medias;
        this.autoplay = auto;

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

    _fixDuplicates() {
        let selected = new Map();
        this.duplicates.clear();

        setAnkiMedia(media => {
            let data_file = media.getAttribute("data-file");
            this._checkDataAttributes(media);

            // Automatically gives an object.id to every media file, if they do not have one.
            if (!media.id) {
                media.id = data_file;
            }
            if (!media.src) {
                media.src = data_file;
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
        let data_file = media.getAttribute("data-file");
        let data_speed = media.getAttribute("data-speed");

        if (typeof data_file != "string") {
            throw new Error(
                `A media element is missing its 'data-file=${data_file}' attribute.`
            );
        }
        if (
            data_speed != undefined &&
            (typeof data_speed != "string" || isNaN(data_speed as any))
        ) {
            throw new Error(
                `A media element has an invalid 'data-speed=${data_speed}/${typeof data_speed}' attribute.`
            );
        }
        this._validateSpeed(parseFloat(data_speed));
    }

    _moveAudioElements(extra: Function | undefined) {
        this.files.clear();

        // Move all audio elements into this object to avoid the audio from stopping
        // when the answer is showed.
        setAnkiMedia(media => {
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

    _setupAudioPlay(media: HTMLAudioElement, clone: HTMLAudioElement) {
        // Set to automatically play the audio when seeking the progress bar.
        media.addEventListener("seeked", event => media.play());
        clone.addEventListener("seeked", event => clone.play());

        // Set to automatically pause all other medias when playing a new media.
        let auto_pause = target => {
            return event => {
                // only clear the queue if the play event was from an user action
                if (!this.is_autoplay) {
                    this.playing.length = 0;
                }
                this.is_autoplay = false;

                setAnkiMedia(media => {
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

// @ts-ignore: Allow jest to import this and do unit tests
if (typeof exports != "undefined") {
    // @ts-ignore
    module.exports = {
        setAnkiMedia,
        AnkiMediaQueue,
        ANKI_MEDIA_QUEUE_PREVIEW_TIMEOUT,
    };
}
