//noinspection MissingCopyrightHeader - explicitly not GPL

/*
 * Copying and distribution of this file, with or without modification, are permitted in any
 * medium without royalty. This file is offered as-is, without any warranty.
 */
package com.ichi2.anki

import android.net.Uri
import com.ichi2.anki.api.BuildConfig
import com.ichi2.anki.api.Ease

/**
 * The contract between AnkiDroid and applications. Contains definitions for the supported URIs and
 * columns.
 *
 *
 * ### Overview
 *
 *
 * FlashCardsContract defines the access to flash card related information. Flash cards consist of
 * notes and cards. To find out more about notes and cards, see
 * [the basics section in the Anki manual.](https://docs.ankiweb.net/getting-started.html#key-concepts)
 *
 *
 * In short, you can think of cards as instances of notes, with the limitation that the number of
 * instances and their names are pre-defined.
 *
 *
 * The most important data of notes/cards are "fields". Fields contain the actual information of the
 * flashcard that is used for learning. Typical fields are "Japanese" and "English" (for a native
 * English speaker to learn Japanese), or just "front" and "back" (for a generic front side and back
 * side of a card, without saying anything about the purpose).
 *
 *
 * Note and card information is accessed in the following way:
 *
 * *   Each row from the [Note] provider represents a note that is stored in the AnkiDroid database.
 * This provider must be used in order to find flashcards. The notes
 * can be accessed by the [Note.CONTENT_URI], like this to search for note:
 *
 *     ```
 *     // Query all available notes
 *     final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, null, null, null);
 *     ```
 *
 *     or this if you know the note's ID:
 *
 *      ```
 *      String noteId = ... // Use the known note ID
 *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId);
 *      final Cursor cur = cr.query(noteUri, null, null, null, null);
 *      ```
 *
 * *   A row from the [Card] sub-provider gives access to notes cards. The
 * cards are accessed as described in the [Card] description.
 *
 * *   The format of notes and cards is described in note types. The note types are accessed as described
 * in the [Model] description.
 *
 *
 * The AnkiDroid Flashcard content provider supports the following operation on it's URIs:
 *
 * ```
 *                  URIs and operations supported by CardContentProvider
 *
 * URI                         | Description
 * --------------------------------------------------------------------------------------------------------------------
 * notes                       | Note with id `note_id` as raw data
 *                             | Supports insert(mid), query(). For code examples see class description of [Note].
 * --------------------------------------------------------------------------------------------------------------------
 * notes/<note_id>             | Note with id `note_id` as raw data
 *                             | Supports query(). For code examples see class description of [Note].
 * --------------------------------------------------------------------------------------------------------------------
 * notes/<note_id>/cards       | All cards belonging to note `note_id` as high level data (Deck name, question, answer).
 *                             | Supports query(). For code examples see class description of [Card].
 * --------------------------------------------------------------------------------------------------------------------
 * notes/<note_id>/cards/<ord> | NoteCard `ord` (with ord = 0... num_cards-1) belonging to note `note_id` as high level data (Deck name, question, answer).
 *                             | Supports update(), query(). For code examples see class description of [Card].
 * --------------------------------------------------------------------------------------------------------------------
 * cards                       | All cards as high level data (Deck name, question, answer).
 *                             | Supports query(). For code examples see class description of [Card].
 * --------------------------------------------------------------------------------------------------------------------
 * cards/<ord>                 | Card `ord` (with ord = 0... num_cards-1) as high level data (Deck name, question, answer).
 *                             | Supports update(), query(). For code examples see class description of [Card].
 * --------------------------------------------------------------------------------------------------------------------
 * models                      | All note types as JSONObjects.
 *                             | Supports query(). For code examples see class description of [Model].
 * --------------------------------------------------------------------------------------------------------------------
 * model/<model_id>            | Direct access to note type `model_id` as JSONObject.
 *                             | Supports query(). For code examples see class description of [Model].
 * --------------------------------------------------------------------------------------------------------------------
 * ```
 *
 * If AnkiDroid's storage is not yet configured (the user has not completed first-run setup),
 * operations on this provider throw [IllegalStateException].
 */
public object FlashCardsContract {
    public const val AUTHORITY: String = BuildConfig.AUTHORITY
    public const val READ_WRITE_PERMISSION: String = BuildConfig.READ_WRITE_PERMISSION

    /**
     * A content:// style uri to the authority for the flash card provider
     */
    @JvmField // required for Java API
    public val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    /**
     * The Notes can be accessed by
     * the [.CONTENT_URI]. If the [.CONTENT_URI] is appended by the note's ID, this
     * note can be directly accessed. If no ID is appended the content provides functions return
     * all the notes that match the query as defined in `selection` argument in the
     * `query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)` call.
     * For queries, the `selectionArgs` parameter can contain an optional selection statement for the notes table
     * in the sql database. E.g. "mid = 12345678" could be used to limit to a particular note type ID.
     * The `selection` parameter is an optional search string for the Anki browser. The syntax is described
     * [in the search section of the Anki manual](https://docs.ankiweb.net/searching.html).
     *
     *
     * Example for querying notes with a certain tag:
     *
     * ```
     *         final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI,
     *              null,         // projection
     *              "tag:my_tag", // example query
     *              null,         // selectionArgs is ignored for this URI
     *              null          // sortOrder is ignored for this URI
     *         );
     * ```
     *
     *
     * Example for querying notes with a certain note id with direct URI:
     *
     * ```
     *          Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *          final Cursor cursor = cr.query(noteUri,
     *              null,  // projection
     *              null,  // selection is ignored for this URI
     *              null,  // selectionArgs is ignored for this URI
     *              null   // sortOrder is ignored for this URI
     *          );
     * ```
     *
     *
     * In order to insert a new note (the cards for this note will be added to the default deck)
     * the [.CONTENT_URI] must be used together with a note type (see [Model])
     * ID, e.g.
     *
     * ```
     *          Long mId = ... // Use the correct note type ID
     *          ContentValues values = new ContentValues();
     *          values.put(FlashCardsContract.Note.MID, mId);
     *          Uri newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values);
     * ```
     *
     *
     * Updating tags for a note can be done this way:
     *
     * ```
     *          Uri updateNoteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *          ContentValues values = new ContentValues();
     *          values.put(FlashCardsContract.Note.TAGS, tag1 + " " + tag2);
     *          int updateCount = cr.update(updateNoteUri, values, null, null);
     * ```
     *
     *
     * A note consists of the following columns:
     *
     * ```
     *                  Note column description
     *
     * Type   | Name   | Access     | Description
     * --------------------------------------------------------------------------------------------------------------------
     * long   | _ID    | read-only  | Row ID. This is the ID of the note. It is the same as the note ID in Anki. This
     *        |        |            | ID can be used for accessing the data of a note using the URI
     *        |        |            | "content://com.ichi2.anki.flashcards/notes/<_ID>/data
     * --------------------------------------------------------------------------------------------------------------------
     * long   | GUID   | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * long   | MID    | read-only  | This is the ID of the note type that is used for rendering the cards. This ID can be used for
     *        |        |            | accessing the data of the note type using the URI
     *        |        |            | "content://com.ichi2.anki.flashcards/model/<ID>
     * --------------------------------------------------------------------------------------------------------------------
     * long   | MOD    | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * long   | USN    | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * long   | TAGS   | read-write | NoteTag of this note. NoteTag are separated  by spaces.
     * --------------------------------------------------------------------------------------------------------------------
     * String | FLDS   | read-write | Fields of this note. Fields are separated by "\\x1f", a.k.a. Consts.FIELD_SEPARATOR
     * --------------------------------------------------------------------------------------------------------------------
     * long   | SFLD   | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * long   | CSUM   | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * long   | FLAGS  | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * long   | DATA   | read-only  | See more at https://github.com/ankidroid/Anki-Android/wiki/Database-Structure
     * --------------------------------------------------------------------------------------------------------------------
     * ```
     */
    public object Note {
        /**
         * The content:// style URI for notes. If the it is appended by the note's ID, this
         * note can be directly accessed, e.g.
         *
         * ```
         *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
         * ```
         *
         * If the URI is appended by the note ID and then the keyword "data", it is possible to
         * access the details of a note:
         *
         *
         * ```
         *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
         *      Uri dataUri = Uri.withAppendedPath(noteUri, "data");
         * ```
         *
         *
         * For examples on how to use the URI for queries see class description.
         */
        @JvmField // required for Java API
        public val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")

        /**
         * The content:// style URI for notes, but with a direct SQL query to the notes table instead of accepting
         * a query in the libanki browser search syntax like the main URI #CONTENT_URI does.
         */
        @JvmField // required for Java API
        public val CONTENT_URI_V2: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes_v2")

        /**
         * This is the ID of the note. It is the same as the note ID in Anki. This ID can be
         * used for accessing the data of a note using the URI
         * "content://com.ichi2.anki.flashcards/notes/<ID>/data
         */
        @Suppress("ConstPropertyName", "ktlint:standard:backing-property-naming")
        public const val _ID: String = "_id"

        // field is part of the default projection available to the clients
        @Suppress("MemberVisibilityCanBePrivate")
        public const val GUID: String = "guid"

        // "mid" used to mean "note type id". "note type" used to be the name "note type". It can't be changed for compatibility reason.
        public const val MID: String = "mid"

        @Suppress("unused")
        public const val ALLOW_EMPTY: String = "allow_empty"
        public const val MOD: String = "mod"

        // field is part of the default projection available to the clients
        @Suppress("MemberVisibilityCanBePrivate")
        public const val USN: String = "usn"
        public const val TAGS: String = "tags"
        public const val FLDS: String = "flds"

        // field is part of the default projection available to the clients
        @Suppress("MemberVisibilityCanBePrivate")
        public const val SFLD: String = "sfld"
        public const val CSUM: String = "csum"
        public const val FLAGS: String = "flags"
        public const val DATA: String = "data"

        @JvmField // required for Java API
        public val DEFAULT_PROJECTION: Array<String> =
            arrayOf(
                _ID,
                GUID,
                MID,
                MOD,
                USN,
                TAGS,
                FLDS,
                SFLD,
                CSUM,
                FLAGS,
                DATA,
            )

        /**
         * MIME type used for a note.
         */
        public const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/vnd.com.ichi2.anki.note"

        /**
         * MIME type used for notes.
         */
        public const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.com.ichi2.anki.note"

        /**
         * Used only by bulkInsert() to specify which deck the notes should be placed in
         */
        public const val DECK_ID_QUERY_PARAM: String = "deckId"
    }

    /**
     * "Model" was the previous named of "note type". It is used here for the sake of compatibility with the ecosystem.
     * A note type describes what cards look like.
     *
     * ```
     *              Note Type description
     * Type    | Name             | Access    | Description
     * --------------------------------------------------------------------------------------------------------------------
     * long    | _ID              | read-only | Note type ID.
     * --------------------------------------------------------------------------------------------------------------------
     * String  | NAME             |           | Name of the Note type.
     * --------------------------------------------------------------------------------------------------------------------
     * String  | CSS              |           | CSS styling code which is shared across all the templates
     * --------------------------------------------------------------------------------------------------------------------
     * String  | FIELD_NAMES      | read-only | Names of all the fields, separate by the 0x1f character
     * --------------------------------------------------------------------------------------------------------------------
     * Integer | NUM_CARDS        | read-only | Number of card templates, which corresponds to the number of rows in the templates table
     * --------------------------------------------------------------------------------------------------------------------
     * Long    | DECK_ID          | read-only | The default deck that cards should be added to
     * --------------------------------------------------------------------------------------------------------------------
     * Integer | SORT_FIELD_INDEX | read-only | Which field is used as the main sort field
     * --------------------------------------------------------------------------------------------------------------------
     * Integer | TYPE             | read-only | 0 for normal note type, 1 for cloze note type
     * --------------------------------------------------------------------------------------------------------------------
     * String  | LATEX_POST       | read-only | Code to go at the end of LaTeX renderings in Anki Desktop
     * --------------------------------------------------------------------------------------------------------------------
     * String  | LATEX_PRE        | read-only | Code to go at the front of LaTeX renderings in Anki Desktop
     * --------------------------------------------------------------------------------------------------------------------
     * ```
     *
     *
     * It's possible to query all note type at once like this
     *
     * ```
     *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *      final Cursor cursor = cr.query(FlashCardsContract.Model.CONTENT_URI,
     *          null,  // projection
     *          null,  // selection is ignored for this URI
     *          null,  // selectionArgs is ignored for this URI
     *          null   // sortOrder is ignored for this URI
     *      );
     * ```
     *
     *
     * It's also possible to access a specific note type like this:
     *
     *
     * ```
     *      long noteTypeId = ...// Use the correct note type ID
     *      Uri noteTypeUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(noteTypeId));
     *      final Cursor cur = cr.query(noteTypeUri,
     *          null,  // projection
     *          null,  // selection is ignored for this URI
     *          null,  // selectionArgs is ignored for this URI
     *          null   // sortOrder is ignored for this URI
     *      );
     * ```
     *
     *
     * Instead of specifying the noteType ID, it's also possible to get the currently active noteType using the following URI:
     *
     *
     * ```
     *      Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, FlashCardsContract.Model.CURRENT_MODEL_ID);
     * ```
     */
    public object Model {
        /**
         * The content:// style URI for note type. If the it is appended by the note type's ID, this
         * note can be directly accessed. See class description above for further details.
         */
        @JvmField // required for Java API
        public val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "models")
        public const val CURRENT_MODEL_ID: String = "current"

        /**
         * This is the ID of the note type. It is the same as the note ID in Anki. This ID can be
         * used for accessing the data of the note type using the URI
         * `content://com.ichi2.anki.flashcards/models/<ID>`
         */
        @Suppress("ConstPropertyName", "ktlint:standard:backing-property-naming")
        public const val _ID: String = "_id"
        public const val NAME: String = "name"
        public const val FIELD_NAME: String = "field_name"
        public const val FIELD_NAMES: String = "field_names"
        public const val NUM_CARDS: String = "num_cards"
        public const val CSS: String = "css"
        public const val SORT_FIELD_INDEX: String = "sort_field_index"
        public const val TYPE: String = "type"
        public const val LATEX_POST: String = "latex_post"
        public const val LATEX_PRE: String = "latex_pre"
        public const val NOTE_COUNT: String = "note_count"

        /**
         * The deck ID that is selected by default when adding new notes with this note type.
         * This is only used when the "Deck for new cards" preference is set to "Decide by note type"
         */
        public const val DECK_ID: String = "deck_id"

        @JvmField // required for Java API
        public val DEFAULT_PROJECTION: Array<String> =
            arrayOf(
                _ID,
                NAME,
                FIELD_NAMES,
                NUM_CARDS,
                CSS,
                DECK_ID,
                SORT_FIELD_INDEX,
                TYPE,
                LATEX_POST,
                LATEX_PRE,
            )

        /**
         * MIME type used for a note type.
         */
        public const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/vnd.com.ichi2.anki.model"

        /**
         * MIME type used for note type.
         */
        public const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.com.ichi2.anki.model"
    }

    /**
     * Card template for a note type. A template defines how to render the fields of a note into the actual HTML that
     * makes up a flashcard. A note type can define multiple card templates, for example a Forward and Reverse Card could
     * be defined with the forward card allowing to review a word from Japanese -> English (e.g. 犬 -> dog), and the
     * reverse card allowing review in the "reverse" direction (e.g dog -> 犬). When a Note is inserted, a Card will
     * be generated for each active CardTemplate which is defined.
     */
    public object CardTemplate {
        /**
         * MIME type used for data.
         */
        public const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.com.ichi2.anki.model.template"
        public const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/vnd.com.ichi2.anki.model.template"

        /**
         * Row ID. This is a virtual ID which actually does not exist in AnkiDroid's data base.
         * This column only exists so that this interface can be used with existing CursorAdapters
         * that require the existence of a "_id" column. This means, that it CAN NOT be used
         * reliably over subsequent queries. Especially if the number of cards or fields changes,
         * the _ID will change too.
         */
        @Suppress("ConstPropertyName", "ktlint:standard:backing-property-naming")
        public const val _ID: String = "_id"

        /**
         * This is the ID of the note type that this row belongs to (i.e. [Model._ID]).
         * "Model" was the previous name of "note type". We keep this deprecated name for the sake of compatibility with the ecosystem.
         */
        public const val MODEL_ID: String = "model_id"

        /**
         * This is the ordinal / index of the card template (from 0 to number of cards - 1).
         */
        public const val ORD: String = "ord"

        /**
         * The template name e.g. "Card 1".
         */
        public const val NAME: String = "card_template_name"

        /**
         * The definition of the template for the question
         */
        public const val QUESTION_FORMAT: String = "question_format"

        /**
         * The definition of the template for the answer
         */
        public const val ANSWER_FORMAT: String = "answer_format"

        /**
         * Optional alternative definition of the template for the question when rendered with the browser
         */
        public const val BROWSER_QUESTION_FORMAT: String = "browser_question_format"

        /**
         * Optional alternative definition of the template for the answer when rendered with the browser
         */
        public const val BROWSER_ANSWER_FORMAT: String = "browser_answer_format"
        public const val CARD_COUNT: String = "card_count"

        /**
         * Default columns that are returned when querying the ...models/#/templates URI.
         */
        @JvmField // required for Java API
        public val DEFAULT_PROJECTION: Array<String> =
            arrayOf(
                _ID,
                MODEL_ID,
                ORD,
                NAME,
                QUESTION_FORMAT,
                ANSWER_FORMAT,
            )
    }

    /**
     * A card is an instance of a note.
     *
     *
     * If the URI of a note is appended by the keyword "cards", it is possible to
     * access all the cards that are associated with this note:
     *
     *
     * ```
     *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *      Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *      final Cursor cur = cr.query(cardsUri,
     *          null,  // projection
     *          null,  // selection is ignored for this URI
     *          null,  // selectionArgs is ignored for this URI
     *          null   // sortOrder is ignored for this URI
     *      );
     *```
     *
     * If it is furthermore appended by the cards ordinal (see [.CARD_ORD]) it's possible to
     * directly access a specific card.
     *
     *
     * ```
     *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *      Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *      Uri specificCardUri = Uri.withAppendedPath(noteUri, Integer.toString(cardOrd));
     *      final Cursor cur = cr.query(specificCardUri,
     *          null,  // projection
     *          null,  // selection is ignored for this URI
     *          null,  // selectionArgs is ignored for this URI
     *          null   // sortOrder is ignored for this URI
     *      );
     * ```
     *
     * A card consists of the following columns:
     *
     * ```
     *              Card column description
     * Type   | Name             | Access     | Description
     * --------------------------------------------------------------------------------------------------------------------
     * long   | NOTE_ID          | read-only  | This is the ID of the note that this row belongs to (i.e. Note._ID)
     * --------------------------------------------------------------------------------------------------------------------
     * int    | CARD_ORD         | read-only  | This is the ordinal of the card. A note has 1..n cards. The ordinal can also be
     *        |                  |            | used to directly access a card as describe in the class description.
     * --------------------------------------------------------------------------------------------------------------------
     * String | CARD_NAME        | read-only  | The card's name.
     * --------------------------------------------------------------------------------------------------------------------
     * String | DECK_ID          | read-write | The id of the deck that this card is part of.
     * --------------------------------------------------------------------------------------------------------------------
     * String | QUESTION         | read-only  | The question for this card.
     * --------------------------------------------------------------------------------------------------------------------
     * String | ANSWER           | read-only  | The answer for this card.
     * --------------------------------------------------------------------------------------------------------------------
     * String | QUESTION_SIMPLE  | read-only  | The question for this card in the simplified form, without card styling information (CSS).
     * --------------------------------------------------------------------------------------------------------------------
     * String | ANSWER_SIMPLE    | read-only  | The answer for this card in the simplified form, without card styling information (CSS).
     * --------------------------------------------------------------------------------------------------------------------
     * String | ANSWER_PURE      | read-only  | Purified version of the answer. In case the [.ANSWER] contains any additional elements
     *        |                  |            | (like a duplicate of the question) this is removed for [.ANSWER_PURE].
     *        |                  |            | Like [.ANSWER_SIMPLE] it does not contain styling information (CSS).
     * --------------------------------------------------------------------------------------------------------------------
     * ```
     *
     * The only writable column is the [.DECK_ID]. Moving a card to another deck, can be
     * done as shown in this example
     *
     * ```
     *      Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
     *      Uri cardsUri = Uri.withAppendedPath(noteUri, "cards");
     *      final Cursor cur = cr.query(cardsUri,
     *          null,  // selection is ignored for this URI
     *          null,  // selectionArgs is ignored for this URI
     *          null   // sortOrder is ignored for this URI
     *      );
     *      do {
     *          long newDid = 12345678L;
     *          long oldDid = cur.getLong(cur.getColumnIndex(FlashCardsContract.Card.DECK_ID));
     *          if(newDid != oldDid){
     *              // Move to new deck
     *              ContentValues values = new ContentValues();
     *              values.put(FlashCardsContract.Card.DECK_ID, newDid);
     *              Uri cardUri = Uri.withAppendedPath(cardsUri, cur.getString(cur.getColumnIndex(FlashCardsContract.Card.CARD_ORD)));
     *              cr.update(cardUri, values, null, null);
     *          }
     *      } while (cur.moveToNext());
     * ```
     */
    public object Card {
        /**
         * The ID of the card in the Anki database.
         */
        @Suppress("ConstPropertyName", "ktlint:standard:backing-property-naming")
        public const val _ID: String = "_id"

        /**
         * This is the ID of the note that this card belongs to (i.e. [Note._ID]).
         */
        public const val NOTE_ID: String = "note_id"

        /**
         * This is the ordinal of the card. A note has 1..n cards. The ordinal can also be used
         * to directly access a card as describe in the class description.
         */
        public const val CARD_ORD: String = "ord"

        /**
         * The card's name.
         */
        public const val CARD_NAME: String = "card_name"

        /**
         * The name of the deck that this card is part of.
         */
        public const val DECK_ID: String = "deck_id"

        /**
         * The stored Anki repetition count for this card.
         *
         * Counts how many times Anki has recorded this card as answered during study. The Anki
         * manual's
         * [`prop:reps`](https://docs.ankiweb.net/searching.html#card-properties) search uses the
         * same stored counter. New cards typically start at 0.
         *
         * This provider exposes the backend value as-is; exact effects of manual operations such as
         * rescheduling depend on Anki's scheduler/backend behavior.
         */
        public const val REPS: String = "reps"

        /**
         * The stored Anki lapse count for this card.
         *
         * Counts how many times Anki has recorded this card as lapsed. In Anki's manual, a
         * [lapse](https://docs.ankiweb.net/deck-options.html#lapses) is pressing Again on a review
         * card, and [`prop:lapses`](https://docs.ankiweb.net/searching.html#card-properties)
         * searches this same stored counter.
         *
         * This provider exposes the backend value as-is.
         */
        public const val LAPSES: String = "lapses"

        /**
         * The stored Anki card type code: the card's learning or review stage.
         *
         * See also [Anki's card states](https://docs.ankiweb.net/getting-started.html#card-states).
         *
         * Think of this as a simple state machine that affects how the card is scheduled when it
         * is answered. A card typically moves `0 -> 1 -> 2`; if a review card lapses, it
         * typically moves `2 -> 3 -> 2`.
         *
         * * `0` = new
         * * `1` = learning
         * * `2` = review
         * * `3` = relearning
         *
         * Other values should be treated as unknown.
         */
        public const val TYPE: String = "type"

        /**
         * The stored original deck id for this card.
         *
         * For cards in filtered decks, Anki keeps a link to the card's
         * [home deck](https://docs.ankiweb.net/filtered-decks.html#home-decks).
         *
         * * If the card is currently in a filtered deck, this is the deck id the card came from.
         * * If the card is not currently in a filtered deck, this value is 0.
         */
        public const val ORIGINAL_DECK_ID: String = "original_deck_id"

        /**
         * The question for this card.
         */
        public const val QUESTION: String = "question"

        /**
         * The answer for this card.
         */
        public const val ANSWER: String = "answer"

        /**
         * Simplified version of the question, without card styling (CSS).
         */
        public const val QUESTION_SIMPLE: String = "question_simple"

        /**
         * Simplified version of the answer, without card styling (CSS).
         */
        public const val ANSWER_SIMPLE: String = "answer_simple"

        /**
         * Purified version of the answer. In case the ANSWER contains any additional elements
         * (like a duplicate of the question) this is removed for ANSWER_PURE
         */
        public const val ANSWER_PURE: String = "answer_pure"

        /**
         * The stored Anki queue code for this card: how Anki decides whether the card can be shown.
         *
         * See [AnkiDroid's cards table docs](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure/#cards).
         *
         * Unlike [TYPE], queue also includes temporary display states such as buried, suspended,
         * and preview.
         *
         * * `-3` = manually buried
         * * `-2` = sibling buried
         * * `-1` = suspended
         * * `0` = new
         * * `1` = learning
         * * `2` = review
         * * `3` = day-learning / relearning; next review is at least one day after the previous
         *   review
         * * `4` = preview
         *
         * Negative values indicate cards that are not currently schedulable. Non-negative values
         * indicate active scheduler queues.
         *
         * Other values should be treated as unknown.
         */
        public const val RAW_QUEUE: String = "queue"

        /**
         * The stored Anki due value for this card.
         *
         * This is raw scheduler state, not a single normalized timestamp or day number.
         *
         * See [AnkiDroid's cards table docs](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure/#cards).
         *
         * This value is state-dependent:
         *
         * * new queue: the order in which cards are to be studied; starts from 1
         * * learning / relearning queue: Unix timestamp in seconds
         * * review queue: the collection scheduler day number when the card is due for review.
         *   This is not a calendar date; converting it to one requires collection
         *   creation/day-cutoff metadata not currently exposed by this API.
         * * filtered deck: the position of the card inside the filtered deck
         *
         * Consumers must read this together with [RAW_QUEUE], and with [RAW_ORIGINAL_DUE] when
         * filtered-deck behavior matters.
         */
        public const val RAW_DUE: String = "due"

        /**
         * The stored original due value for this card.
         *
         * For cards in filtered decks, this stores the due value from before the card entered the
         * filtered deck. If the card is not currently in a filtered deck, this value is `0`.
         *
         * @see RAW_DUE for the state-dependent meaning of the current due value
         */
        public const val RAW_ORIGINAL_DUE: String = "original_due"

        /**
         * The stored Anki interval (`ivl`) for this card, in days.
         *
         * See [AnkiDroid's cards table docs](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure/#cards).
         *
         * For review cards, this is the scheduled number of days between reviews. For relearning
         * cards, this retains the review interval that will apply when the card returns to the
         * review queue.
         *
         * Learning cards store `0`; their active learning due value is stored in [RAW_DUE].
         */
        public const val INTERVAL: String = "interval"

        /**
         * The stored SM-2 ease factor for this card.
         *
         * This factor helps determine the next review interval for cards using SM-2 scheduling.
         * It is not used by FSRS.
         *
         * This is an integer scaled by 10, so `2500` means `250%`.
         * New SM-2 cards typically start at `2500`.
         */
        public const val RAW_SM2_FACTOR: String = "sm2_factor"

        /**
         * The stored Anki `left` value for this card.
         *
         * This is relevant for learning and relearning cards.
         *
         * Anki uses this as an internal scheduler counter. The stored value is encoded as:
         *
         * * `left % 1000`: learning or relearning reps left before graduation
         * * `left / 1000`: reps that can still be completed before the day cutoff
         *
         * For example, `left = 2003` means 3 reps left before graduation, with 2 of
         * them still completable before the day cutoff.
         *
         * This provider exposes the backend value as-is, so consumers should not depend on a
         * normalized encoding.
         */
        public const val RAW_LEFT: String = "left"

        /**
         * The stored original position for this card, if the backend has one.
         *
         * This is raw card metadata from the backend. It is not the same as the card's current
         * due/order value, and it is not related to filtered-deck original due handling.
         *
         * See [Anki's studying documentation](https://docs.ankiweb.net/studying.html#editing-and-more)
         * for the user-facing "Restore original position" behavior, and
         * [Anki's card info documentation](https://docs.ankiweb.net/stats.html#card-info) for the
         * card position shown in card info.
         *
         * When present, Anki uses this to retain a card's original position independently from
         * later user changes such as repositioning. Cards that do not have this metadata return
         * `null`.
         */
        public const val ORIGINAL_POSITION: String = "original_position"

        /**
         * The raw custom data string stored on this card.
         *
         * This value is used by custom scheduling hooks. The provider exposes the backend value
         * as-is: it does not parse, validate, normalize, or promise a schema for the string.
         *
         * See [Anki's custom-data search documentation](https://docs.ankiweb.net/searching.html#custom-data)
         * for examples of how Anki treats this as custom scheduler data.
         *
         * Consumers should treat this as opaque text owned by the scheduler/custom scheduling
         * code that wrote it. Cards with no custom data return an empty string.
         */
        public const val RAW_CUSTOM_DATA: String = "custom_data"

        /**
         * The FSRS stability value stored in the card's memory state, if present.
         *
         * This is raw FSRS scheduler state. Stability represents the estimated number of days it
         * takes for recall probability to drop from 100% to 90% for this card's memory.
         *
         * See [Anki's card stability documentation](https://docs.ankiweb.net/stats.html#card-stability).
         *
         * Cards without stored FSRS memory state return `null`. Consumers should not expect this
         * value to be present for cards scheduled without FSRS, new cards without memory state, or
         * cards whose scheduler state has not stored FSRS memory data.
         *
         * @see FSRS_DIFFICULTY
         */
        public const val FSRS_STABILITY: String = "fsrs_stability"

        /**
         * The FSRS difficulty value stored in the card's memory state, if present.
         *
         * This is raw FSRS scheduler state. Difficulty represents the estimated inherent
         * difficulty of recalling this card's memory.
         *
         * See [Anki's card difficulty documentation](https://docs.ankiweb.net/stats.html#card-difficulty).
         *
         * Cards without stored FSRS memory state return `null`. Consumers should not expect this
         * value to be present for cards scheduled without FSRS, new cards without memory state, or
         * cards whose scheduler state has not stored FSRS memory data.
         *
         * @see FSRS_STABILITY
         */
        public const val FSRS_DIFFICULTY: String = "fsrs_difficulty"

        /**
         * The desired retention value stored on this card, if present.
         *
         * This is raw FSRS scheduler metadata, expressed as a fraction such as `0.9` for 90%.
         * It is exposed as stored by the backend and is not derived from deck options at query
         * time.
         *
         * See [Anki's desired retention documentation](https://docs.ankiweb.net/deck-options.html#desired-retention).
         *
         * Cards without a stored desired-retention value return `null`.
         */
        public const val FSRS_DESIRED_RETENTION: String = "fsrs_desired_retention"

        /**
         * The FSRS decay value stored on this card, if present.
         *
         * This is raw FSRS scheduler metadata used by the FSRS forgetting curve. The provider
         * exposes the backend value as-is and does not reinterpret it.
         *
         * See the [FSRS algorithm documentation](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm#fsrs-6)
         * for the forgetting-curve context.
         *
         * Cards without a stored decay value return `null`.
         */
        public const val FSRS_DECAY: String = "fsrs_decay"

        /**
         * The last review time stored on this card, if present.
         *
         * This is raw backend card metadata in Unix epoch seconds. It is not a formatted date and
         * it is not synthesized from the revlog by this provider.
         *
         * See [Anki's revlog documentation](https://docs.ankiweb.net/stats.html#manual-analysis)
         * for the related Unix epoch convention used in review history, and the upstream
         * [last review time change](https://github.com/ankitects/anki/pull/4124) for why this
         * card-level value exists.
         *
         * Cards without a stored last-review time return `null`.
         */
        public const val LAST_REVIEW_TIME_SECONDS: String = "last_review_time_secs"

        /**
         * The content:// style URI for cards. Can be used to search for cards or access specific cards.
         * For examples on how to use the URI for queries see the overview in [FlashCardsContract].
         */
        @JvmField // required for Java API
        public val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "cards")

        @JvmField // required for Java API
        public val DEFAULT_PROJECTION: Array<String> =
            arrayOf(
                _ID,
                NOTE_ID,
                CARD_ORD,
                CARD_NAME,
                DECK_ID,
                QUESTION,
                ANSWER,
            )

        /**
         * MIME type used for a card.
         */
        public const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/vnd.com.ichi2.anki.card"

        /**
         * MIME type used for cards.
         */
        public const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.com.ichi2.anki.card"
    }

    /**
     * A ReviewInfo contains information about a card that is scheduled for review.
     *
     *
     * To access the next scheduled card(s), a simple query with the [.CONTENT_URI] can be used.
     * Arguments:
     * ```
     *              ReviewInfo information table
     * Type  | Name   | Default value                       | Description
     * --------------------------------------------------------------------------------------------------------------------
     * long  | deckID | The deck, that was last selected    | The deckID of the deck from which the scheduled cards should be pulled.
     *       |        | for reviewing by the user in the    |
     *       |        | Deck chooser dialog of the App      |
     * --------------------------------------------------------------------------------------------------------------------
     * int   | limit  | 1                                   | The maximum number of cards (rows) that will be returned. In case
     *       |        |                                     | the deck has fewer scheduled cards, the returned number of cards will be
     *       |        |                                     | lower than the limit.
     * --------------------------------------------------------------------------------------------------------------------
     * ```
     *
     *
     * ```
     *      Uri scheduled_cards_uri = FlashCardsContract.ReviewInfo.CONTENT_URI;
     *      String deckArguments[] = new String[]{"5", "123456789"};
     *      String deckSelector = "limit=?, deckID=?";
     *      final Cursor cur = cr.query(scheduled_cards_uri,
     *          null,  // projection
     *          deckSelector,  // if null, default values will be used
     *          deckArguments,  // if null, the deckSelector must not contain any placeholders ("?")
     *          null   // sortOrder is ignored for this URI
     *      );
     * ```
     *
     *
     * A ReviewInfo consists of the following columns:
     *
     * ```
     *              ReviewInfo column information
     * Type       | Name              | Access     | Description
     * --------------------------------------------------------------------------------------------------------------------
     * long       | NOTE_ID           | read-only  | This is the ID of the note that this row belongs to (i.e. [Note._ID]).
     * --------------------------------------------------------------------------------------------------------------------
     * int        | CARD_ORD          | read-only  | This is the ordinal of the card. A note has 1..n cards. The ordinal can also be used
     *            |                   |            | to directly access a card as describe in the class description.
     * --------------------------------------------------------------------------------------------------------------------
     * int        | BUTTON_COUNT      | read-only  | The number of buttons/ease identifiers that can be used to answer the card.
     * --------------------------------------------------------------------------------------------------------------------
     * JSONArray  | NEXT_REVIEW_TIMES | read-only  | A JSONArray containing when the card will be scheduled for review for all ease identifiers
     *            |                   |            | available. The number of entries in this array must equal the number of buttons in [.BUTTON_COUNT].
     * --------------------------------------------------------------------------------------------------------------------
     * JSONArray  | MEDIA_FILES       | read-only  | The media files, like images and sound files, contained in the cards.
     * --------------------------------------------------------------------------------------------------------------------
     * String     | EASE              | write-only | The ease of the card. Used when answering the card. One of:
     *            |                   |            | com.ichi2.anki.api.Ease.EASE_1.value
     *            |                   |            | com.ichi2.anki.api.Ease.EASE_2.value
     *            |                   |            | com.ichi2.anki.api.Ease.EASE_3.value
     *            |                   |            | com.ichi2.anki.api.Ease.EASE_4.value
     * --------------------------------------------------------------------------------------------------------------------
     * String     | TIME_TAKEN        | write_only | The time it took to answer the card (in milliseconds). Used when answering the card.
     * --------------------------------------------------------------------------------------------------------------------
     * int        | BURY              | write-only | Set to 1 to bury the card. Mutually exclusive with setting EASE/TIME_TAKEN/SUSPEND
     * --------------------------------------------------------------------------------------------------------------------
     * int        | SUSPEND           | write_only | Set to 1 to suspend the card. Mutually exclusive with setting EASE/TIME_TAKEN/BURY
     * --------------------------------------------------------------------------------------------------------------------
     * ```
     *
     *
     * Answering a card can be done as shown in this example. Don't set BURY/SUSPEND when answering a card.
     *
     * ```
     *      ContentResolver cr = getContentResolver();
     *      Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
     *      ContentValues values = new ContentValues();
     *      long noteId = 123456789; //<-- insert real note id here
     *      int cardOrd = 0;   //<-- insert real card ord here
     *      int ease = Ease.EASE_3.value; //<-- insert real ease here
     *      long timeTaken = System.currentTimeMillis() - cardStartTime; //<-- insert real time taken here
     *
     *      values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId);
     *      values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
     *      values.put(FlashCardsContract.ReviewInfo.EASE, ease);
     *      values.put(FlashCardsContract.ReviewInfo.TIME_TAKEN, timeTaken);
     *      cr.update(reviewInfoUri, values, null, null);
     * ```
     *
     *
     * Burying or suspending a card can be done this way. Don't set EASE/TIME_TAKEN when burying/suspending a card
     *
     * ```
     *      ContentResolver cr = getContentResolver();
     *      Uri reviewInfoUri = FlashCardsContract.ReviewInfo.CONTENT_URI;
     *      ContentValues values = new ContentValues();
     *      long noteId = card.note(col).getId();
     *      int cardOrd = card.getOrd();
     *
     *      values.put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId);
     *      values.put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd);
     *
     *      // use this to bury a card
     *      values.put(FlashCardsContract.ReviewInfo.BURY, 1);
     *
     *      // if you want to suspend, use this instead
     *      // values.put(FlashCardsContract.ReviewInfo.SUSPEND, 1);
     *
     *      int updateCount = cr.update(reviewInfoUri, values, null, null);
     * ```
     */
    public object ReviewInfo {
        @JvmField // required for Java API
        public val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")

        /**
         * This is the ID of the note that this card belongs to (i.e. [Note._ID]).
         */
        public const val NOTE_ID: String = "note_id"

        /**
         * This is the ordinal of the card. A note has 1..n cards. The ordinal can also be used
         * to directly access a card as describe in the class description.
         */
        public const val CARD_ORD: String = "ord"

        /**
         * This is the number of ease modes. It can take a value between 2 and 4.
         */
        public const val BUTTON_COUNT: String = "button_count"

        /**
         * This is a JSONArray containing the next review times for all buttons.
         */
        public const val NEXT_REVIEW_TIMES: String = "next_review_times"

        /**
         * The names of the media files in the question and answer
         */
        public const val MEDIA_FILES: String = "media_files"

        /**
         * Ease of an answer. Is not set when requesting the scheduled cards.
         * Can take values from the [Ease] enum e.g. [Ease.EASE_1.value].
         */
        public const val EASE: String = "answer_ease"

        /*
         * Time it took to answer the card (in ms)
         */
        public const val TIME_TAKEN: String = "time_taken"

        /**
         * Write-only field, allows burying of a card when set to 1
         */
        public const val BURY: String = "buried"

        /**
         * Write-only field, allows suspending of a card when set to 1
         */
        public const val SUSPEND: String = "suspended"

        @JvmField // required for Java API
        public val DEFAULT_PROJECTION: Array<String> =
            arrayOf(
                NOTE_ID,
                CARD_ORD,
                BUTTON_COUNT,
                NEXT_REVIEW_TIMES,
                MEDIA_FILES,
            )

        /**
         * MIME type used for ReviewInfo.
         */
        public const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.com.ichi2.anki.review_info"
    }

    /**
     * A Deck contains information about a deck contained in the users deck list.
     *
     *
     * To request a list of all decks the URI [.CONTENT_ALL_URI] can be used.
     * To request the currently selected deck the URI [.CONTENT_SELECTED_URI] can be used.
     *
     * A Deck consists of the following columns:
     *
     * ```
     *              Columns available in a Deck
     *
     * Type       | Name         | Access    | Description
     * --------------------------------------------------------------------------------------------------------------------
     * long       | DECK_ID      | read-only | This is the unique ID of the Deck.
     * --------------------------------------------------------------------------------------------------------------------
     * String     | DECK_NAME    |           | This is the name of the Deck as the user usually sees it.
     * --------------------------------------------------------------------------------------------------------------------
     * String     | DECK_DESC    |           | The deck description shown on the overview page
     * --------------------------------------------------------------------------------------------------------------------
     * JSONArray  | DECK_COUNTS  | read-only | These are the deck counts of the Deck. [learn, review, new]
     * --------------------------------------------------------------------------------------------------------------------
     * JSONObject | OPTIONS      | read-only | These are the options of the deck.
     * --------------------------------------------------------------------------------------------------------------------
     * Boolean    | DECK_DYN     | read-only | Whether or not the deck is a filtered deck
     * --------------------------------------------------------------------------------------------------------------------
     * ```
     *
     * Requesting a list of all decks can be done as shown in this example
     *
     * ```
     *      Cursor decksCursor = getContentResolver().query(FlashCardsContract.Deck.CONTENT_ALL_URI, null, null, null, null);
     *      if (decksCursor.moveToFirst()) {
     *          HashMap<Long,String> decks = new HashMap<Long,String>();
     *          do {
     *              long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
     *              String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));
     *              try {
     *                  JSONObject deckOptions = new JSONObject(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.OPTIONS)));
     *                  JSONArray deckCounts = new JSONArray(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)));
     *              } catch (JSONException e) {
     *                  e.printStackTrace();
     *              }
     *              decks.put(deckID, deckName);
     *          } while (decksCursor.moveToNext());
     *      }
     * ```
     *
     *
     * Requesting a single deck can be done the following way:
     *
     * ```
     *      long deckId = 123456 //<-- insert real deck ID here
     *      Uri deckUri = Uri.withAppendedPath(FlashCardsContract.Deck.CONTENT_ALL_URI, Long.toString(deckId));
     *      Cursor decksCursor = getContentResolver().query(deckUri, null, null, null, null);
     *
     *      if (decksCursor == null || !decksCursor.moveToFirst()) {
     *          Log.d(TAG, "query for deck returned no result");
     *          if (decksCursor != null) {
     *              decksCursor.close();
     *          }
     *      } else {
     *          JSONObject decks = new JSONObject();
     *          long deckID = decksCursor.getLong(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_ID));
     *          String deckName = decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_NAME));
     *
     *          try {
     *              JSONObject deckOptions = new JSONObject(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.OPTIONS)));
     *              JSONArray deckCounts = new JSONArray(decksCursor.getString(decksCursor.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)));
     *              Log.d(TAG, "deckCounts " + deckCounts);
     *              Log.d(TAG, "deck Options " + deckOptions);
     *              decks.put(deckName, deckID);
     *          } catch (JSONException e) {
     *              e.printStackTrace();
     *          }
     *          decksCursor.close();
     *      }
     * ```
     *
     *
     * Updating the selected deck can be done as shown in this example
     *
     * ```
     *      long deckId = 123456; //>-- insert real deck id here
     *
     *      ContentResolver cr = getContentResolver();
     *      Uri selectDeckUri = FlashCardsContract.Deck.CONTENT_SELECTED_URI;
     *      ContentValues values = new ContentValues();
     *      values.put(FlashCardsContract.Deck.DECK_ID, deckId);
     *      cr.update(selectDeckUri, values, null, null);
     * ```
     */
    public object Deck {
        @JvmField // required for Java API
        public val CONTENT_ALL_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")

        @JvmField // required for Java API
        public val CONTENT_SELECTED_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "selected_deck")

        /**
         * The name of the Deck
         */
        public const val DECK_NAME: String = "deck_name"

        /**
         * The unique identifier of the Deck
         */
        public const val DECK_ID: String = "deck_id"

        /**
         * The number of cards in the Deck
         */
        public const val DECK_COUNTS: String = "deck_count"

        /**
         * The options of the Deck
         */
        public const val OPTIONS: String = "options"

        /**
         * 1 if dynamic (AKA filtered) deck
         */
        public const val DECK_DYN: String = "deck_dyn"

        /**
         * Deck description
         */
        public const val DECK_DESC: String = "deck_desc"

        @JvmField // required for Java API
        public val DEFAULT_PROJECTION: Array<String> =
            arrayOf(
                DECK_NAME,
                DECK_ID,
                DECK_COUNTS,
                OPTIONS,
                DECK_DYN,
                DECK_DESC,
            )

        /**
         * MIME type used for Deck.
         */
        public const val CONTENT_TYPE: String = "vnd.android.cursor.dir/vnd.com.ichi2.anki.deck"
    }

    /**
     * For interacting with Anki's media collection.
     *
     *
     * To insert a file into the media collection use:
     *
     * ```
     *      Uri fileUri = ...; //<-- Use real Uri for media file here
     *      String preferredName = "my_media_file"; //<-- Use preferred name for inserted media file here
     *      ContentResolver cr = getContentResolver();
     *      ContentValues cv = new ContentValues();
     *      cv.put(AnkiMedia.FILE_URI, fileUri.toString());
     *      cv.put(AnkiMedia.PREFERRED_NAME, "file_name");
     *      Uri insertedFile = cr.insert(AnkiMedia.CONTENT_URI, cv);
     * ```
     */
    public object AnkiMedia {
        /**
         * Content Uri for the MEDIA row of the CardContentProvider
         */
        @JvmField // required for Java API
        public val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "media")

        /**
         * Uri.toString() which points to the media file that is to be inserted.
         */
        public const val FILE_URI: String = "file_uri"

        /**
         * The preferred name for the file that will be inserted/copied into collection.media
         */
        public const val PREFERRED_NAME: String = "preferred_name"
    }
}
