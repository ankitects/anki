# Review Reminders

## Glossary

| Term                           | Definition                                                                                                                                                          |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Review Reminder / Reminder     | A recurring, scheduled notification that reminds the user to review their Anki cards.                                                                               |
| Notification                   | An individual instance of a review reminder firing and displaying an OS-enabled visual on the user's device.                                                        |
| ReviewReminderScope / Scope    | The set of associated cards a review reminder inspects to determine if it should fire. Either a specific deck (deck-specific) or the overall collection (app-wide). |
| Fire                           | The act of a review reminder displaying an OS-enabled notification to the user.                                                                                     |
| Card Trigger Threshold         | The number of cards that must be due in a review reminder's scope for the review reminder to fire.                                                                  |

## Functionality

> See `ReviewReminder`

A review reminder is tied to a specific scope and is set for a specific time of day. When the scheduled time for a review reminder arrives,
it examines its scope and counts the number of cards which are currently due (including those in sub-decks). If the number of due cards is greater than or equal to the
card trigger threshold, the review reminder fires and displays a notification to the user. Otherwise, the notification is skipped and the review reminder
will try to fire again tomorrow (the next moment the time associated with the review reminder is reached). Advanced review reminder settings can be used to customize other conditions for firing; see `ReviewReminder`.
When an app-wide review reminder is tapped by the user, the app opens to the deck list. When a deck-specific review reminder is tapped, the app opens to that specific deck's reviewer.

Card trigger thresholds are used because:

1. Most users will not want reminders to do their flashcards if they have already finished them.
2. Some users may only want reminders if their flashcards are piling up.
3. They were provided in a simplified form by the legacy notifications system, which review reminders replace.

## Example Use Cases

1. A user wishes to receive a daily reminder at noon, and an extra reminder if they haven't reviewed yet by 10 PM.
They set up two review reminders, one for noon and one for 10 PM, both with a card trigger threshold of 1. If they have reviewed all their cards by 10 PM, the second reminder will not fire.
2. A user often remembers to review their cards in the morning, but sometimes forgets. They do not care about finishing reviews for all 
their cards and instead just care that they maintain a habit of opening up the app once a day. They set up a review reminder for 3 PM
with a card trigger threshold of 1 and enable the "only notify if no reviews" option.
3. A user currently has a legacy notification that shows a permanent notification in the notification shade if they have at least ten cards due, and they wish to migrate
this to the new review reminders system. This is not exactly possible, but they can get close by creating one review reminder per each daytime hour (or perhaps even more granularly) and setting the card trigger
threshold to 10 for each.
4. A user does not want to open up the app every day but would like to be reminded to review their cards if they have a large backlog. 
They set up a review reminder for 5 PM with a card trigger threshold of 50 and count only "To Review" cards toward the threshold.
5. A user has customized a deck to have hour-long learning steps and would like to be reminded precisely when their cards become due.
This is not exactly possible, but they can get close by creating one review reminder per each daytime hour (or perhaps even more granularly), setting the card trigger threshold to 1 for each, and counting only "Learning" cards toward the threshold.


## UI / UX

> See `ScheduleRemindersFragment`, `AddEditReminderDialog`

Review reminders can be accessed either via a specific deck or via the app-wide Settings menu. More concretely, as of writing,
the full list of access points is:

1. `DeckPicker` > long-press on a deck > "Schedule reminders"
2. `DeckPicker` > `StudyOptions` of a deck > bell icon
3. `Settings` > `Review Reminders`

The Settings menu displays all review reminders for the entire collection and for all decks. Viewing review reminders for a specific deck
shows all review reminders for that specific deck (the review reminders of child decks are not listed).

`ScheduleRemindersFragment` is the principal UI fragment for review reminders. Because it needs to be shown in both deck-specific
and app-wide settings contexts, and in particular because it must stylistically align with those different contexts, it renders itself differently
based on which `FragmentHost` it is located within. The dialog for creating and editing review reminders is `AddEditReminderDialog`.
Users with issues with review reminders can consult `ReminderTroubleshootingFragment`.

## Persistence

> See `ReviewRemindersDatabase`

Review reminders are stored via SharedPreferences. While other methods like Room or DataStore were considered,
SharedPreferences was chosen due to its simplicity and the ability to not add new dependencies to AnkiDroid.

The SharedPreferences file used is separate from the primary preferences file to avoid cluttering the primary preferences.
Each preference contains the review reminders for a single deck, with the key encoding the deck ID.
There also exists a single preference for the global review reminders, which contains all review reminders that
are not associated with a specific deck. This design is intended to make it easier to retrieve the review reminders for a
specific scope without having to filter through a larger list of review reminders, and because it is an intuitive way to
persist the review reminders.

The object stored at each individual SharedPreference key is a pair of the schema version and a string.
The string is a serialized map of `ReviewReminderId` to `ReviewReminder`. The entire map deserializes into a `ReviewReminderGroup`,
which provides a clean interface for accessing the review reminders for a specific scope.

An example of the SharedPreference file's content:

```json
{ 
  "app_wide": {
    "version": 3,
    "remindersMapJson": {
      "0": {
        "id": 0,
        "time": {"hour": 21, "minute": 24},
        "cardTriggerThreshold": 1,
        "scope": {"type": "com.ichi2.anki.reviewreminders.ReviewReminderScope.Global"},
        "enabled": true,
        "latestNotifTime": 1789244669003,
        "profileID": "",
        "onlyNotifyIfNoReviews": false
      }
    }
  },
  "deck_1788806657103": {
    "version": 3,
    "remindersMapJson": {
      "2": {
        "id": 2,
        "time": {"hour": 12, "minute": 46},
        "cardTriggerThreshold": 0,
        "scope": {"type": "com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific", "did": 1788806657103},
        "enabled": false,
        "latestNotifTime": 1788810433527,
        "profileID": "",
        "onlyNotifyIfNoReviews": true
      }
    }
  }
}
```

Review reminders have unique IDs. A `ReviewReminder` is (essentially) immutable and must be destroyed and recreated with a new ID to be modified.
This ensures no two reminders will have the same ID.

The fields of `ReviewReminder` are explicit data classes and inline value classes to ensure that the data of a `ReviewReminder` is strongly typed.

### Schema Migrations

> See `ReviewReminder`, `ReviewReminderSchema`

Since review reminders are stored in SharedPreferences, schema migrations (changes to the `ReviewReminder` data class)
must be handled manually to ensure that outdated review reminders do not crash the app when deserialized.
New fields cannot be added without a schema change because `ignoreUnknownKeys` is not enabled for deserialization.

For an example of a schema migration, see [9b13c6426c31c39eb5f69a58509cdda014e40093](https://github.com/ankidroid/Anki-Android/commit/9b13c6426c31c39eb5f69a58509cdda014e40093).

If a migration fails, the app intentionally does not crash, as failing review reminders should not prevent the user
from accessing the overall app. Instead, the failing review reminders are deleted, an exception report is gathered,
and the user is informed of the failure via a toast. Unit tests have been designed to prevent an update from
being published without the necessary schema migrations in place.

Technically, some changes to the `ReviewReminder` schema do not require a migration (for example, adding a new field with a default value).
However, unit tests are designed to fail in this case, and it is good policy to always add a schema migration for any change, no matter how small.

### Deck Deletion Handling

If a review reminder is created and associated with a specific deck, and that deck is later deleted, what should happen to the review reminder?
The intuitive answer is to delete the review reminder as well (perhaps even lazily), but that causes many problems.
For instance, what happens if the deletion is undone via an "undo" button, or if a backup is reverted to, or if the deletion happens on a separate device
and is brought to the primary device via a sync?

Hence, the cleanest solution is to leave the review reminder in place, but to mark it as "orphaned" (i.e. its associated deck no longer exists).
Notifications do not fire for these orphaned review reminders, and they are greyed-out in the UI to indicate their status to the user.
However, no immediate dialog is shown to the user to inform them of the orphaned status, as this would be disruptive.
In short, instead of handling the decision on whether to delete the review reminder ourselves, we kick the question to the user. If the deck is somehow restored,
the review reminder works again like before. If the user wants to delete the review reminder, they can do so manually.

### Limitations and Possible Future Improvements

The actual information stored for review reminders in SharedPreferences would fit very nicely in a single SQL-style table.
A great deal of complexity has sprung up in the code to manually handle serialization, deserialization, and schema migrations.
Migrations and schema versioning come pre-packaged with systems like Room.

In a weird way, review reminders currently have "two" IDs: the deck ID and the reminder ID. This is clunky and not ideal.
The deck ID (concretely, `ReviewReminderScope`) is used to determine which preference to store the reminder in
(i.e. which "table" to store it in). The reminder ID (concretely, `ReviewReminderId`) is used to identify review reminders
globally. Perhaps these should be consolidated somehow to create a single compound key.
We might be willing to sacrifice the ability to cleanly find all review reminders for a single deck and instead have the
scope of a review reminder be a single field per reminder. This would allow `ReviewReminderId` to be a true primary key.

The current design of `ReviewReminderScope` also creates a fragile invariant in regard to SharedPreferences storage. It is technically possible
to store a review reminder with scope A in the preference for scope B. A similar issue exists for `ReviewReminderId`, too,
which is both a field of a `ReviewReminder` but also the key in the serialized map from review reminder IDs to serialized review reminder strings.
See `StoredReviewReminderGroup`.

`ReviewReminderGroup` is a bit awkward. A caller may edit a `ReviewReminderGroup` and think that their edits have been persisted to the disk,
when in fact the persistence methods defined by `ReviewRemindersDatabase` should be used instead.

## Alarm Management and Notifications

> See `ReviewReminderAlarmManager`, `NotificationService`

`ReviewReminderAlarmManager` handles all alarm scheduling for review reminders. `NotificationService` handles the sending of
all review reminder notifications.

Since AnkiDroid is not an alarm-clock app, we do not have access to exact alarm scheduling (unless the user chooses to grant them to the app).
Hence, our ability to set alarms is restricted to `AlarmManager.setRepeating` and `AlarmManager.setWindow`. The latter guarantees
sends within a ten-minute window, whereas the former can sometimes postpone alarm firings for unacceptably long periods of time.
See the Android AlarmManager documentation for more information: https://developer.android.com/develop/background-work/services/alarms.
Hence, we use `AlarmManager.setWindow` to schedule review reminders. However, `setWindow` does not create a recurring alarm,
so we must reschedule the next alarm each time a review reminder fires.

The flow is as follows:

1. A review reminder is created. `scheduleReviewReminderNotification` schedules the corresponding alarm.
2. Time passes.
3. When the alarm fires, `NotificationService` handles it via `handleReviewReminderNotification`, which atomically retrieves the reminder from the database,
verifies that the latest firing has not taken place yet, then updates the latest firing time to be the current time.
4. `NotificationService` has now decided to attempt a firing of the reminder. It schedules the next alarm for the next day before continuing.
5. `NotificationService`'s `sendReviewReminderNotification` then performs all pre-fire checks (ex. card trigger threshold, advanced settings), aborting if any fails.
6. `NotificationService`'s `fireReviewReminderNotification` performs the actual OS call to display the notification to the user.

If the device shuts down, the alarm is lost. Hence, on-boot listeners in `BootService` and the application `AnkiDroidApp` file
iterate through all review reminders and reschedule all of them when the device or process start up respectively. Critically,
`scheduleReviewReminderNotification` also checks to see if the current time is past the latest scheduled time. If so, a notification
is also immediately fired (via `ReviewReminderAlarmManager` invoking `NotificationService`. This is to handle the case where the device may go to sleep for over a day, if the app crashes and reopens
shortly after the scheduled time, etc.)

`scheduleReviewReminderNotification` and `NotificationService`'s flow for firing a review reminder are essentially idempotent. If a
review reminder is scheduled twice, the OS de-duplicates the alarm. If a review reminder attempts to be fired twice, the `latestNotifTime` field
of `ReviewReminder` and the firing-time bookkeeping of `NotificationService` ensure that only one notification is actually sent to the user.

### Snoozing

> See `SnoozeService`

Notifications can be snoozed by the user by pressing a button on the OS notification display, either for five minutes or for one hour.
The flow is as follows:

1. The user presses the snooze button on the notification.
2. An immediate broadcast is sent to `SnoozeService`, which instructs `ReviewReminderAlarmManager` to use `AlarmManager.setWindow`
to schedule a new, non-recurring alarm for the snoozed time.
3. Time passes.
4. The snoozed time arrives. `NotificationService` proceeds with its usual flow, except that it skips scheduling the next alarm, since this is a one-off snoozed alarm.
The alarm for the next routine firing has already been scheduled by the principal firing.

## Permissions Management

> See `PermissionsBottomSheet`

The app needs an OS permission to send notifications to the user. If the permission is not granted and the user creates a review
reminder, the `PermissionsBottomSheet` is displayed to the user. If permission is not granted and at least one review reminder exists, a persistent snackbar on `ScheduleRemindersFragment`
is displayed to warn the user of this fact. If dismissed, the `PermissionsBottomSheet` is never shown again.

## Development

Ensure schema migrations are correctly handled if `ReviewReminder` is edited.

Since review reminder code is difficult to test and debug, ensure valuable review-reminder-related logs are forwarded to `ReminderLogTree`.
