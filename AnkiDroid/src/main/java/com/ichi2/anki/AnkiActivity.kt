//noinspection MissingCopyrightHeader #8659
@file:Suppress("LeakingThis") // fine - used as WeakReference

package com.ichi2.anki

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ProgressBar
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.AttrRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_DARK
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_LIGHT
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_SYSTEM
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anim.ActivityTransitionAnimation.Direction.DEFAULT
import com.ichi2.anim.ActivityTransitionAnimation.Direction.NONE
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.android.animationDisabled
import com.ichi2.anki.common.android.themes.disableXiaomiForceDarkMode
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.compat.CompatHelper.Companion.registerReceiverCompat
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.ARG_SHARE_AS_TEXT
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.KEY_EXPORT_PATH
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.REQUEST_EXPORT_SAVE
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.REQUEST_EXPORT_SHARE
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.handleExportReadyRequest
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.compat.customtabs.CustomTabsFallback
import com.ichi2.compat.customtabs.CustomTabsHelper
import com.ichi2.themes.Themes
import com.ichi2.utils.AdaptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import androidx.browser.customtabs.CustomTabsIntent.Builder as CustomTabsIntentBuilder
import com.ichi2.anki.common.android.R as CommonR

@UiThread
open class AnkiActivity(
    @LayoutRes contentLayoutId: Int? = null,
) : AppCompatActivity(contentLayoutId ?: 0),
    ShortcutGroupProvider,
    AnkiActivityProvider {
    val exportReadyViewModel by viewModels<ExportReadyViewModel>()

    /**
     * Receiver that informs us when a broadcast listen in [broadcastsActions] is received.
     *
     * @see registerReceiver
     * @see broadcastsActions
     */
    private var broadcastReceiver: BroadcastReceiver? = null

    var importColpkgListener: ImportColpkgListener? = null

    val dialogHandler = DialogHandler(this)
    override val ankiActivity = this

    private val customTabActivityHelper: CustomTabActivityHelper = CustomTabActivityHelper()

    private lateinit var fileExportPath: String
    private val saveFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                saveFileCallback(result)
            } else {
                Timber.i("The file selection for the exported collection was cancelled")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The hardware buttons should control the music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
        // Set the theme
        Themes.setTheme(this)
        disableXiaomiForceDarkMode(this)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            @Suppress("deprecation")
            window.navigationBarColor = getColor(R.color.transparent)
        }
        supportFragmentManager.setFragmentResultListener(REQUEST_EXPORT_SAVE, this) { _, bundle ->
            saveExportFile(
                bundle.getString(KEY_EXPORT_PATH) ?: error("Missing required exportPath!"),
            )
        }
        supportFragmentManager.setFragmentResultListener(REQUEST_EXPORT_SHARE, this) { _, bundle ->
            shareFile(
                path = bundle.requireString(KEY_EXPORT_PATH),
                asText = bundle.getBoolean(ARG_SHARE_AS_TEXT, false),
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                exportReadyViewModel.exportReadyDestination.filterNotNull().collect(::handleExportReadyRequest)
            }
        }

        if (savedInstanceState != null) {
            val restoredValue = savedInstanceState.getString(KEY_EXPORT_FILE_NAME) ?: return
            fileExportPath = restoredValue
        }
    }

    override fun onStart() {
        super.onStart()
        // Disable the notifications bar if running under the test monkey.
        // This is a work-around for an issue with the monkey feature of adb - when the
        // monkey runs on a physical device, it can pull the status bar down, and escape the app
        // under test.
        if (AdaptionUtil.isUserATestClient && window != null) {
            // Note: this is run in `onStart`, since it appears the decorView can be null in `onCreate`
            CompatHelper.compat.hideStatusBar(window)
        }
        customTabActivityHelper.bindCustomTabsService(this)
    }

    override fun onStop() {
        super.onStop()
        customTabActivityHelper.unbindCustomTabsService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcastReceiver?.let { unregisterReceiver(it) }
    }

    override fun onResume() {
        super.onResume()
        UsageAnalytics.sendAnalyticsScreenView(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(
            SIMPLE_NOTIFICATION_ID,
        )
        // Show any pending dialogs which were stored persistently
        dialogHandler.executeMessage()
    }

    open fun setViewBinding(binding: ViewBinding) {
        setContentView(binding.root)
    }

    /**
     * Sets the title of the toolbar (support action bar) for the activity.
     *
     * @param title The new title to be set for the toolbar.
     */
    open fun setToolbarTitle(title: String) {
        supportActionBar?.title = title
    }

    /**
     * Sets the title of the toolbar (support action bar) for the activity.
     *
     * @param title The new title to be set for the toolbar.
     */
    open fun setToolbarTitle(
        @StringRes titleRes: Int,
    ) = setToolbarTitle(getString(titleRes))

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Timber.i("Home button pressed")
            return onActionBarBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun onActionBarBackPressed(): Boolean {
        Timber.v("onActionBarBackPressed")
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // called when the CollectionLoader finishes... usually will be over-ridden
    protected open fun onCollectionLoaded(col: Collection) {
        hideProgressBar()
    }

    /**
     * Maps from intent name action to function to run when this action is received by [broadcastReceiver].
     * By default it handles [SdCardReceiver.MEDIA_EJECT], and shows/dismisses dialogs when an SD
     * card is ejected/remounted (collection is saved beforehand by [SdCardReceiver])
     */
    protected open val broadcastsActions =
        mapOf(
            SdCardReceiver.MEDIA_EJECT to { onSdCardNotMounted() },
        )

    /**
     * Register a broadcast receiver, associating an intent to an action as in [broadcastsActions].
     * Add more values in [broadcastsActions] to react to more intents.
     */
    fun registerReceiver() {
        if (broadcastReceiver != null) {
            // Receiver already registered
            return
        }
        broadcastReceiver =
            object : AnkiBroadcastReceiver() {
                override fun onReceiveBroadcast(
                    context: Context,
                    intent: Intent,
                ) {
                    broadcastsActions[intent.action]?.invoke()
                }
            }.also {
                val iFilter = IntentFilter()
                broadcastsActions.keys.map(iFilter::addAction)
                registerReceiverCompat(it, iFilter, ContextCompat.RECEIVER_EXPORTED)
            }
    }

    protected fun onSdCardNotMounted() {
        showThemedToast(this, resources.getString(R.string.sd_card_not_mounted), false)
        finish()
    }

    /** Legacy code should migrate away from this, and use withCol {} instead.
     * */
    val getColUnsafe: Collection
        get() = CollectionManager.getColUnsafe()

    fun colIsOpenUnsafe(): Boolean = CollectionManager.isOpenUnsafe()

    override fun setContentView(view: View?) {
        if (animationDisabled()) {
            view?.clearAnimation()
        }
        super.setContentView(view)
    }

    override fun setContentView(
        view: View?,
        params: ViewGroup.LayoutParams?,
    ) {
        if (animationDisabled()) {
            view?.clearAnimation()
        }
        super.setContentView(view, params)
    }

    override fun addContentView(
        view: View?,
        params: ViewGroup.LayoutParams?,
    ) {
        if (animationDisabled()) {
            view?.clearAnimation()
        }
        super.addContentView(view, params)
    }

    override fun startActivity(intent: Intent) {
        startActivityWithAnimation(intent, DEFAULT)
    }

    fun startActivityWithAnimation(
        intent: Intent,
        animation: Direction,
    ) {
        enableIntentAnimation(intent)
        super.startActivity(intent)
        enableActivityAnimation(animation, open = true)
    }

    override fun finish() {
        finishWithAnimation(DEFAULT)
    }

    fun finishWithAnimation(animation: Direction) {
        Timber.i("finishWithAnimation %s", animation)
        super.finish()
        enableActivityAnimation(animation, open = false)
    }

    private fun disableIntentAnimation(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    /**
     * @param open when `true`, overrides the animation for entering this activity.
     * When `false`, overrides the animation for closing this activity
     */
    private fun disableActivityAnimation(open: Boolean) {
        ActivityTransitionAnimation.slide(this, NONE, open)
    }

    @KotlinCleanup("Maybe rename this? This only disables the animation conditionally")
    private fun enableIntentAnimation(intent: Intent) {
        if (animationDisabled()) {
            disableIntentAnimation(intent)
        }
    }

    /**
     * @param open when `true`, overrides the animation for entering this activity.
     * When `false`, overrides the animation for closing this activity
     */
    private fun enableActivityAnimation(
        animation: Direction,
        open: Boolean,
    ) {
        if (animationDisabled()) {
            disableActivityAnimation(open)
        } else {
            ActivityTransitionAnimation.slide(this, animation, open)
        }
    }

    /** Method for loading the collection which is inherited by every [AnkiActivity]  */
    fun startLoadingCollection() {
        Timber.d("AnkiActivity.startLoadingCollection()")
        if (colIsOpenUnsafe()) {
            Timber.d("Synchronously calling onCollectionLoaded")
            onCollectionLoaded(getColUnsafe)
            return
        }
        // Open collection asynchronously if it hasn't already been opened
        showProgressBar()
        lifecycleScope.launch {
            val col =
                withContext(Dispatchers.IO) {
                    // load collection
                    try {
                        Timber.d("CollectionLoader accessing collection")
                        val col = CollectionManager.getColUnsafe()
                        Timber.i("CollectionLoader obtained collection")
                        col
                    } catch (e: RuntimeException) {
                        Timber.e(e, "loadInBackground - RuntimeException on opening collection")
                        CrashReportService.sendExceptionReport(e, "CollectionLoader.load")
                        null
                    }
                }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                if (col != null) {
                    Timber.d("Asynchronously calling onCollectionLoaded")
                    onCollectionLoaded(col)
                } else {
                    onCollectionLoadError()
                }
            }
        }
    }

    /** The action to take when there was an error loading the collection  */
    fun onCollectionLoadError() {
        Timber.w("onCollectionLoadError")
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.putExtra("collectionLoadError", true) // don't currently do anything with this
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(deckPicker)
    }

    fun showProgressBar() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar != null) {
            progressBar.visibility = View.VISIBLE
        }
    }

    open fun hideProgressBar() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar != null) {
            progressBar.visibility = View.GONE
        }
    }

    internal fun mayOpenUrl(
        @StringRes url: Int,
    ) {
        val url = getString(url)
        val success = customTabActivityHelper.mayLaunchUrl(url.toUri(), null, null)
        if (!success) {
            Timber.w("Couldn't preload url: %s", url)
        }
    }

    /**
     * Opens a URL in a custom tab, with fallback to a browser if no custom tab implementation is available.
     *
     * This method first checks if there is a web browser available on the device. If no browser is found,
     * a snackbar message is displayed informing the user. If a browser is available, a custom tab is
     * opened with customized appearance and animations.
     *
     * @param url The URI to be opened.
     */
    open fun openUrl(url: Uri) {
        if (!AdaptionUtil.hasWebBrowser(this)) {
            showSnackbar(getString(R.string.no_browser_msg, url.toString()))
            return
        }
        val toolbarColor = MaterialColors.getColor(this, CommonR.attr.appBarColor, 0)
        val navBarColor = MaterialColors.getColor(this, CommonR.attr.customTabNavBarColor, 0)
        val colorSchemeParams =
            CustomTabColorSchemeParams
                .Builder()
                .setToolbarColor(toolbarColor)
                .setNavigationBarColor(navBarColor)
                .build()
        val builder =
            CustomTabsIntentBuilder(customTabActivityHelper.session)
                .setShowTitle(true)
                .setStartAnimations(this, R.anim.slide_right_in, R.anim.slide_left_out)
                .setExitAnimations(this, R.anim.slide_left_in, R.anim.slide_right_out)
                .setCloseButtonIcon(
                    BitmapFactory.decodeResource(
                        this.resources,
                        R.drawable.ic_back_arrow_custom_tab,
                    ),
                ).setColorScheme(customTabsColorScheme)
                .setDefaultColorSchemeParams(colorSchemeParams)
        val customTabsIntent = builder.build()
        CustomTabsHelper.addKeepAliveExtra(this, customTabsIntent.intent)
        CustomTabActivityHelper.openCustomTab(this, customTabsIntent, url, CustomTabsFallback())
    }

    fun openUrl(urlString: String) {
        openUrl(urlString.toUri())
    }

    fun openUrl(
        @StringRes url: Int,
    ) {
        openUrl(getString(url))
    }

    private val customTabsColorScheme: Int
        get() =
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                COLOR_SCHEME_SYSTEM
            } else if (Themes.isNightTheme) {
                COLOR_SCHEME_DARK
            } else {
                COLOR_SCHEME_LIGHT
            }

    /**
     * Calls [.showAsyncDialogFragment] internally, using the channel
     * [Channel.GENERAL]
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     */
    open fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
        showAsyncDialogFragment(newFragment, Channel.GENERAL)
    }

    /**
     * Global method to show a dialog fragment including adding it to back stack and handling the case where the dialog
     * is shown from an async task, by showing the message in the notification bar if the activity was stopped before the
     * AsyncTask completed
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     * @param channel the Channel to use for the notification
     */
    fun showAsyncDialogFragment(
        newFragment: AsyncDialogFragment,
        channel: Channel,
    ) {
        try {
            showDialogFragment(newFragment)
        } catch (e: IllegalStateException) {
            Timber.w(e, "failed to show fragment, activity is likely paused. Sending notification")
            // Store a persistent message to SharedPreferences instructing AnkiDroid to show dialog
            DialogHandler.storeMessage(newFragment.dialogHandlerMessage?.toMessage())
            // Show a basic notification to the user in the notification bar in the meantime
            val title = newFragment.notificationTitle
            val message = newFragment.notificationMessage
            showSimpleNotification(title, message, channel)
        }
    }

    /**
     * Show a simple message dialog, dismissing the message without taking any further action when OK button is pressed.
     * If a DialogFragment cannot be shown due to the Activity being stopped then the message is shown in the
     * notification bar instead.
     *
     * @param message
     * @param reload flag which forces app to be restarted when true
     */
    open fun showSimpleMessageDialog(
        message: String,
        title: String = "",
        reload: Boolean = false,
    ) {
        val newFragment: AsyncDialogFragment =
            SimpleMessageDialog.newInstance(title, message, reload)
        showAsyncDialogFragment(newFragment)
    }

    fun showSimpleNotification(
        title: String,
        message: String?,
        channel: Channel,
    ) {
        // Use the title as the ticker unless the title is simply "AnkiDroid"
        val ticker: String? =
            if (title == resources.getString(R.string.app_name)) {
                message
            } else {
                title
            }
        // Build basic notification
        val builder =
            NotificationCompat
                .Builder(
                    this,
                    channel.id,
                ).setSmallIcon(R.drawable.ic_star_notify)
                .setContentTitle(title)
                .setContentText(message)
                .setColor(getColor(CommonR.color.material_light_blue_500))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTicker(ticker)
        configureLegacyNotificationSettings(builder)
        // Creates an explicit intent for an Activity in your app
        val resultIntent = Intent(this, DeckPicker::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val resultPendingIntent =
            PendingIntentCompat.getActivity(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
        builder.setContentIntent(resultPendingIntent)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // mId allows you to update the notification later on.
        notificationManager.notify(SIMPLE_NOTIFICATION_ID, builder.build())
    }

    @LegacyNotifications("Delete once review reminders are stable, these are configurable from OS settings")
    private fun configureLegacyNotificationSettings(builder: NotificationCompat.Builder) {
        if (!Prefs.newReviewRemindersEnabled) {
            // Enable vibrate and blink if set in preferences
            // In the new review reminders system, the user sets these by going to the OS settings themselves
            val prefs = this.sharedPrefs()
            if (prefs.getBoolean("widgetVibrate", false)) {
                builder.setVibrate(longArrayOf(1000, 1000, 1000))
            }
            if (prefs.getBoolean("widgetBlink", false)) {
                builder.setLights(Color.BLUE, 1000, 1000)
            }
        }
    }

    // Show dialogs to deal with database loading issues etc
    open fun showDatabaseErrorDialog(
        errorDialogType: DatabaseErrorDialogType,
        exceptionData: CustomExceptionData? = null,
    ) {
        val newFragment: AsyncDialogFragment = DatabaseErrorDialog.newInstance(errorDialogType, exceptionData)
        showAsyncDialogFragment(newFragment)
    }

    /**
     * sets [.getSupportActionBar] and returns the action bar
     * @return The action bar which was created
     * @throws IllegalStateException if the bar could not be enabled
     */
    protected fun enableToolbar(): ActionBar {
        val toolbar =
            findViewById<Toolbar>(R.id.toolbar)
                ?: // likely missing "<include layout="@layout/toolbar" />"
                throw IllegalStateException("Unable to find toolbar")
        setSupportActionBar(toolbar)
        return supportActionBar!!
    }

    protected fun showedActivityFailedScreen(savedInstanceState: Bundle?) =
        showedActivityFailedScreen(
            savedInstanceState = savedInstanceState,
            activitySuperOnCreate = { state -> super.onCreate(state) },
        )

    /** @see Window.setNavigationBarColor */
    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    fun setNavigationBarColor(
        @AttrRes attr: Int,
    ) {
        window.navigationBarColor = getColorFromAttr(this, attr)
    }

    fun closeCollectionAndFinish() {
        Timber.i("closeCollectionAndFinish()")
        Timber.i("closeCollection: %s", "AnkiActivity:closeCollectionAndFinish()")
        CollectionManager.closeCollectionBlocking()
        finish()
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        val shortcutGroups = getShortcuts()
        data.addAll(shortcutGroups)
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
    }

    /**
     * Get current activity keyboard shortcuts
     */
    private fun getShortcuts(): List<KeyboardShortcutGroup> {
        val generalShortcutGroup =
            ShortcutGroup(
                listOf(
                    shortcut("Ctrl+Z", R.string.undo),
                ),
                R.string.pref_cat_general,
            ).toShortcutGroup(this)

        return listOfNotNull(shortcuts?.toShortcutGroup(this), generalShortcutGroup)
    }

    /**
     * If storage permissions are not granted, shows a toast message and finishes the activity.
     *
     * This should be called AFTER a call to `super.`[onCreate]
     *
     * @return `true`: activity may continue to start, `false`: [onCreate] should stop executing
     * as storage permissions are mot granted
     *
     * @throws SystemStorageException if `getExternalFilesDir` returns null
     */
    fun ensureStoragePermissions(): Boolean {
        if (IntentHandler.grantedStoragePermissions(this, showToast = true)) {
            return true
        }
        Timber.w("finishing activity. No storage permission")
        finish()
        return false
    }

    override val shortcuts
        get(): ShortcutGroup? = null

    override fun onSaveInstanceState(outState: Bundle) {
        if (::fileExportPath.isInitialized) {
            outState.putString(KEY_EXPORT_FILE_NAME, fileExportPath)
        }
        super.onSaveInstanceState(outState)
    }

    @NeedsTest("#20993 verify that the proper mime type is used for the share intent")
    private fun shareFile(
        path: String,
        asText: Boolean = false,
    ) {
        // Make sure the file actually exists
        val attachment = File(path)
        if (!attachment.exists()) {
            Timber.e("Specified apkg file %s does not exist", path)
            showThemedToast(this, resources.getString(R.string.apk_share_error), false)
            return
        }
        val authority = "${this.packageName}.apkgfileprovider"

        // Get a URI for the file to be shared via the FileProvider API
        val uri: Uri =
            try {
                FileProvider.getUriForFile(this, authority, attachment)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not generate a valid URI for the apkg file")
                showThemedToast(this, resources.getString(R.string.apk_share_error), false)
                return
            }
        val targetMimeType = if (asText) "text/plain" else "application/apkg"

        val sendIntent =
            ShareCompat
                .IntentBuilder(this)
                .setType(targetMimeType)
                .setStream(uri)
                .setSubject(getString(R.string.export_email_subject, attachment.name))
                .setHtmlText(
                    getString(
                        R.string.export_email_text,
                        getString(R.string.link_manual),
                        getString(R.string.link_distributions),
                    ),
                ).intent
                .apply {
                    clipData = ClipData.newUri(contentResolver, attachment.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
        val shareFileIntent =
            Intent.createChooser(
                sendIntent,
                getString(R.string.export_share_title),
            )
        if (shareFileIntent.resolveActivity(packageManager) != null) {
            startActivity(shareFileIntent)
        } else {
            // Try to save it?
            showSnackbar(R.string.export_send_no_handlers)
            saveExportFile(path)
        }
    }

    private fun saveExportFile(exportPath: String) {
        // Make sure the file actually exists
        val attachment = File(exportPath)
        if (!attachment.exists()) {
            Timber.e("saveExportFile() Specified apkg file %s does not exist", exportPath)
            showSnackbar(R.string.export_save_apkg_unsuccessful)
            return
        }

        fileExportPath = exportPath

        // Send the user to the standard Android file picker via Intent
        val saveIntent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/apkg"
                putExtra(Intent.EXTRA_TITLE, attachment.name)
                putExtra("android.content.extra.SHOW_ADVANCED", true)
                putExtra("android.content.extra.FANCY", true)
                putExtra("android.content.extra.SHOW_FILESIZE", true)
            }
        try {
            saveFileLauncher.launch(saveIntent)
        } catch (ex: ActivityNotFoundException) {
            Timber.w(ex, "No activity found to handle saveExportFile request")
            showSnackbar(R.string.activity_start_failed)
        }
    }

    private fun saveFileCallback(result: ActivityResult) {
        launchCatchingTask {
            withProgress(getString(R.string.export_saving_exported_collection)) {
                val isSuccessful =
                    withContext(Dispatchers.IO) {
                        exportToProvider(result.data!!)
                    }

                if (isSuccessful) {
                    showSnackbar(R.string.export_save_apkg_successful, Snackbar.LENGTH_SHORT)
                } else {
                    showSnackbar(R.string.export_save_apkg_unsuccessful)
                }
            }
        }
    }

    private fun exportToProvider(
        intent: Intent,
        deleteAfterExport: Boolean = true,
    ): Boolean {
        if (intent.data == null) {
            Timber.e("exportToProvider() provided with insufficient intent data %s", intent)
            return false
        }
        val uri = intent.data
        Timber.d("Exporting from file to ContentProvider URI: %s/%s", fileExportPath, uri.toString())
        try {
            contentResolver.openFileDescriptor(uri!!, "w").use { pfd ->
                if (pfd != null) {
                    FileOutputStream(pfd.fileDescriptor).use { fileOutputStream ->
                        CompatHelper.compat.copyFile(fileExportPath, fileOutputStream)
                    }
                } else {
                    Timber.w(
                        "exportToProvider() failed - ContentProvider returned null file descriptor for %s",
                        uri,
                    )
                    return false
                }
            }
            if (deleteAfterExport && !File(fileExportPath).delete()) {
                Timber.w("Failed to delete temporary export file %s", fileExportPath)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to export file to Uri: %s/%s", fileExportPath, uri.toString())
            return false
        }
        return true
    }

    companion object {
        /** Extra key to set the finish animation of an activity  */
        const val FINISH_ANIMATION_EXTRA = "finishAnimation"

        private const val SIMPLE_NOTIFICATION_ID = 0
        private const val KEY_EXPORT_FILE_NAME = "key_export_file_name"
    }
}

fun Fragment.requireAnkiActivity(): AnkiActivity =
    ankiActivity
        ?: throw java.lang.IllegalStateException("Fragment $this not attached to an AnkiActivity.")

val Fragment.ankiActivity: AnkiActivity?
    get() = this.requireActivity() as? AnkiActivity?

interface AnkiActivityProvider {
    val ankiActivity: AnkiActivity
}
