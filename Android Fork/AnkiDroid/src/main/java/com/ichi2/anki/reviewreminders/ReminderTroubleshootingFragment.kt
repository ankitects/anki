// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentReminderTroubleshootingBinding
import com.ichi2.anki.databinding.ItemTroubleshootingCheckBinding
import com.ichi2.anki.utils.doOnApplyWindowInsets
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.utils.ext.onWindowFocusChanged
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.ext.setBackgroundTint
import com.ichi2.anki.utils.ext.setSupportActionBarText
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.attemptToEnableNotifications
import com.ichi2.utils.Permissions.openAppNotificationsSettingsScreen
import com.ichi2.utils.TruncatedString
import com.ichi2.utils.copyToClipboard
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

/**
 * An action which a user can take to resolve a [check][TroubleshootingCheck].
 *
 * Example: Disabling power saving mode.
 *
 * `null` for checks which are not immediately resolvable: we should not offer to turn
 * 'Do not disturb' off, as this is a user preference.
 */
private data class ResolveCheckAction(
    val label: String,
    /** A hardcoded (non-translatable) description of the action for logging purposes. */
    val logDescription: String,
    val action: () -> Unit,
)

/** %alpha to use for the circular background of icons */
private const val BACKGROUND_ALPHA = 0.12f

class ReminderTroubleshootingFragment : Fragment(R.layout.fragment_reminder_troubleshooting) {
    private val viewModel: ReminderTroubleshootingViewModel by activityViewModels {
        reminderTroubleshootingViewModelFactory(requireContext())
    }

    @VisibleForTesting
    internal val binding by viewBinding(FragmentReminderTroubleshootingBinding::bind)

    /**
     * [ScheduleRemindersFragment] can be hosted from multiple activities and must change its UI to accommodate its host
     * Since this fragment is launched from [ScheduleRemindersFragment], it must also know its host to adjust its UI accordingly.
     *
     * @see ScheduleRemindersFragment.FragmentHost
     */
    private val host: ScheduleRemindersFragment.FragmentHost by lazy {
        requireArguments().requireParcelable(ARG_HOST)
    }

    internal val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshChecks()
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        when (host.toolbarType) {
            ScheduleRemindersFragment.ToolbarType.EXTERNAL -> setupExternalActivityToolbar()
            ScheduleRemindersFragment.ToolbarType.INTERNAL_COLLAPSIBLE,
            ScheduleRemindersFragment.ToolbarType.INTERNAL_NON_COLLAPSIBLE,
            -> setupInternalFragmentToolbar()
        }

        setupSummary()
        setupTroubleshootingChecks()
        setupSettingChangeDetector()
        setupDebugButton()
        setupContentInsets()
    }

    /**
     * Keeps the toolbar and the end of the scrolled content clear of the system bars and any
     * display cutout.
     *
     * The content renders underneath the bottom bar while scrolling.
     *
     * Hosts which show this fragment below a toolbar of their own consume the top inset.
     */
    private fun setupContentInsets() {
        binding.troubleshootingToolbar.doOnApplyWindowInsets { view, insets, initial ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            view.updatePadding(left = bars.left, right = bars.right)
            // Margin must be used to align the icon and the title.
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = initial.margins.top + bars.top
            }
        }
        binding.scrollView.doOnApplyWindowInsets { view, insets, initial ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            view.updatePadding(left = bars.left, right = bars.right, bottom = initial.padding.bottom + bars.bottom)
        }
        // the view replaces the reminders list after the insets were dispatched, so request them again
        binding.root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    private fun setupExternalActivityToolbar() {
        binding.troubleshootingToolbar.isVisible = false
        setSupportActionBarText(title = TR.errorsTroubleshootingButton())
        requireActivity().invalidateMenu()
    }

    private fun setupInternalFragmentToolbar() {
        binding.troubleshootingToolbar.title = TR.errorsTroubleshootingButton()
        binding.troubleshootingToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupSummary() {
        viewModel.state.collectInLifecycle { state ->
            val context = requireContext()
            val (iconRes, tintColor, summaryText) =
                when (state.summaryStatus) {
                    SummaryStatus.Error ->
                        Triple(
                            R.drawable.ic_cancel_24,
                            context.getColor(android.R.color.holo_red_dark),
                            context.getString(CommonString.reminder_troubleshooting_summary_error),
                        )

                    SummaryStatus.Warning ->
                        Triple(
                            R.drawable.ic_warning_24,
                            getColorFromAttr(context, CommonR.attr.reminderTroubleshootingWarning),
                            context.getString(CommonString.reminder_troubleshooting_summary_warning),
                        )

                    SummaryStatus.Ok ->
                        Triple(
                            R.drawable.ic_check_circle_24,
                            getColorFromAttr(context, CommonR.attr.reminderTroubleshootingOk),
                            context.getString(CommonString.reminder_troubleshooting_summary_ok),
                        )
                }
            binding.summaryIcon.setImageResource(iconRes)
            binding.summaryIcon.setColorFilter(tintColor)
            binding.summaryIconContainer.setBackgroundTint(tintColor, alpha = BACKGROUND_ALPHA)
            binding.summaryText.text = summaryText
        }
    }

    private fun setupTroubleshootingChecks() {
        val checksAdapter =
            TroubleshootingChecksAdapter { check ->
                with(this) { check.resolveAction() }
            }
        binding.checksList.adapter = checksAdapter
        viewModel.state.collectInLifecycle { state ->
            val visibleChecks = state.checks.filter { it.result !is CheckResult.Unavailable }
            checksAdapter.submitList(visibleChecks)
        }
    }

    /**
     * Refreshes checks when the window regains focus.
     *
     * This handles cases that [onResume] misses, such as toggling battery saver
     * from the notification shade, which doesn't pause/resume the activity.
     */
    private fun setupSettingChangeDetector() {
        onWindowFocusChanged { hasFocus -> if (hasFocus) viewModel.refreshChecks() }
    }

    private fun setupDebugButton() {
        binding.copyDebugInfo.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val debugInfo = ReminderLogTree.readReminderLog() + "\n\n" + ReviewRemindersDatabase.dumpContentsToString()
                requireContext().copyToClipboard(
                    TruncatedString.from(debugInfo),
                    failureMessageId = CommonString.about_ankidroid_error_copy_debug_info,
                )
            }
        }
    }

    companion object {
        /**
         * Arguments key for specifying the host of this fragment.
         */
        private const val ARG_HOST = "arg_host"

        fun newInstance(host: ScheduleRemindersFragment.FragmentHost): ReminderTroubleshootingFragment =
            ReminderTroubleshootingFragment().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(ARG_HOST, host)
                    }
                Timber.i(
                    "Creating ReminderTroubleshootingFragment with host=%s",
                    host,
                )
            }
    }
}

/**
 * Shared factory for [ReminderTroubleshootingViewModel].
 *
 * Lives outside the ViewModel itself to keep the ViewModel free of `Context`. Both
 * [ScheduleRemindersFragment] and [ReminderTroubleshootingFragment] use this with
 * `by activityViewModels { … }` so they observe a single VM + repository instance.
 */
internal fun reminderTroubleshootingViewModelFactory(context: Context): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ReminderTroubleshootingViewModel(ReminderTroubleshootingRepository(context))
        }
    }

private class TroubleshootingChecksAdapter(
    private val getResolveAction: (TroubleshootingCheck) -> ResolveCheckAction?,
) : ListAdapter<TroubleshootingCheck, TroubleshootingChecksAdapter.ViewHolder>(DIFF_CALLBACK) {
    class ViewHolder(
        val binding: ItemTroubleshootingCheckBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding =
            ItemTroubleshootingCheckBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val check = getItem(position)
        val context = holder.itemView.context
        val tintColor = check.result.tintColor(context)
        val lastIndex = itemCount - 1

        val titleText = with(context) { check.title() }
        holder.binding.title.text = titleText
        holder.binding.statusName.text = with(context) { check.statusName() }

        val explanationText = with(context) { check.explanation() }
        holder.binding.explanation.text = explanationText
        holder.binding.explanation.isVisible = explanationText != null

        holder.binding.statusIcon.setImageResource(check.result.iconRes())
        holder.binding.statusIcon.setColorFilter(tintColor)
        // The warning triangle is visually top-heavy; nudge it up to appear centered
        holder.binding.statusIcon.translationY = if (check.result is CheckResult.Warning) -1.dp.toPx(context).toFloat() else 0f
        holder.binding.iconContainer.setBackgroundTint(tintColor, alpha = BACKGROUND_ALPHA)

        // Rounded card-group: top/middle/bottom/single shapes with 2dp gaps
        val backgroundRes =
            when {
                itemCount == 1 -> R.drawable.bg_troubleshooting_item_single
                position == 0 -> R.drawable.bg_troubleshooting_item_top
                position == lastIndex -> R.drawable.bg_troubleshooting_item_bottom
                else -> R.drawable.bg_troubleshooting_item_middle
            }
        holder.binding.itemContainer.setBackgroundResource(backgroundRes)

        // Action link + item click: only actionable for failed/warning checks
        val needsFix = check.result.hasIssue
        val resolveAction = if (needsFix) getResolveAction(check) else null
        holder.binding.actionLink.isVisible = resolveAction != null
        if (resolveAction != null) {
            holder.binding.actionLink.text = resolveAction.label
            val clickListener =
                View.OnClickListener {
                    Timber.i("Launching fix for '%s': %s", titleText, resolveAction.logDescription)
                    resolveAction.action()
                }
            holder.binding.itemContainer.setOnClickListener(clickListener)
            holder.binding.actionLink.setOnClickListener(clickListener)
        } else {
            holder.binding.itemContainer.setOnClickListener(null)
            holder.binding.itemContainer.isClickable = false
        }

        val gap = if (position < lastIndex) 2.dp.toPx(context) else 0
        val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
        lp?.bottomMargin = gap
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<TroubleshootingCheck>() {
                override fun areItemsTheSame(
                    oldItem: TroubleshootingCheck,
                    newItem: TroubleshootingCheck,
                ) = oldItem::class == newItem::class

                override fun areContentsTheSame(
                    oldItem: TroubleshootingCheck,
                    newItem: TroubleshootingCheck,
                ) = oldItem == newItem
            }
    }
}

context(context: Context)
private fun TroubleshootingCheck.title(): String =
    context.getString(
        when (this) {
            is TroubleshootingCheck.NotificationPermission -> CommonString.reminder_troubleshooting_check_notification_permission
            is TroubleshootingCheck.NotificationChannelEnabled -> CommonString.reminder_troubleshooting_check_notification_channel
            is TroubleshootingCheck.DoNotDisturbOff -> CommonString.reminder_troubleshooting_check_do_not_disturb
            is TroubleshootingCheck.UnrestrictedOptimizationEnabled -> CommonString.reminder_troubleshooting_check_battery_optimization
            is TroubleshootingCheck.PowerSavingModeOff -> CommonString.reminder_troubleshooting_check_power_saving_mode
            is TroubleshootingCheck.ExactAlarmPermission -> CommonString.reminder_troubleshooting_check_exact_alarm_permission
        },
    )

context(context: Context)
private fun TroubleshootingCheck.statusName(): String? {
    val passed = result == CheckResult.Passed
    val statusRes =
        when (this) {
            is TroubleshootingCheck.NotificationPermission,
            is TroubleshootingCheck.ExactAlarmPermission,
            ->
                if (passed) {
                    CommonString.reminder_troubleshooting_status_granted
                } else {
                    CommonString.reminder_troubleshooting_status_denied
                }
            is TroubleshootingCheck.NotificationChannelEnabled -> if (passed) CommonString.enabled else CommonString.disabled
            is TroubleshootingCheck.DoNotDisturbOff,
            is TroubleshootingCheck.PowerSavingModeOff,
            ->
                if (passed) {
                    CommonString.reminder_troubleshooting_status_off
                } else {
                    CommonString.reminder_troubleshooting_status_on
                }
            is TroubleshootingCheck.UnrestrictedOptimizationEnabled ->
                when (result) {
                    is CheckResult.Passed -> CommonString.reminder_troubleshooting_status_unrestricted
                    is CheckResult.Warning -> CommonString.reminder_troubleshooting_status_optimized
                    is CheckResult.Failed -> CommonString.reminder_troubleshooting_status_restricted
                    else -> null
                }
        }
    return statusRes?.let { context.getString(it) }
}

context(context: Context)
private fun TroubleshootingCheck.explanation(): String? {
    val explanationRes =
        when (this) {
            // no need for an explanation: the 'grant permission' action should be sufficient
            is TroubleshootingCheck.NotificationPermission -> null
            is TroubleshootingCheck.NotificationChannelEnabled ->
                if (result.hasIssue) CommonString.reminder_troubleshooting_explanation_notification_channel else null
            is TroubleshootingCheck.DoNotDisturbOff ->
                if (result.hasIssue) CommonString.reminder_troubleshooting_explanation_do_not_disturb else null
            is TroubleshootingCheck.UnrestrictedOptimizationEnabled ->
                when (result) {
                    is CheckResult.Warning -> CommonString.reminder_troubleshooting_explanation_battery_optimized
                    is CheckResult.Failed -> CommonString.reminder_troubleshooting_explanation_battery_restricted
                    else -> null
                }
            is TroubleshootingCheck.PowerSavingModeOff ->
                if (result.hasIssue) CommonString.reminder_troubleshooting_explanation_power_saving_mode else null
            is TroubleshootingCheck.ExactAlarmPermission ->
                if (result.hasIssue) CommonString.reminder_troubleshooting_explanation_exact_alarm else null
        }
    return explanationRes?.let { context.getString(it) }
}

private fun CheckResult.iconRes(): Int =
    when (this) {
        is CheckResult.Loading -> R.drawable.ic_sync
        is CheckResult.Passed -> R.drawable.ic_check_circle_24
        is CheckResult.Failed -> R.drawable.ic_cancel_24
        is CheckResult.Warning -> R.drawable.ic_warning_24
        is CheckResult.Unavailable -> R.drawable.ic_error_outline
        is CheckResult.Error -> R.drawable.ic_error_outline
    }

private fun CheckResult.tintColor(context: Context): Int =
    when (this) {
        is CheckResult.Passed -> getColorFromAttr(context, CommonR.attr.reminderTroubleshootingOk)
        is CheckResult.Warning -> getColorFromAttr(context, CommonR.attr.reminderTroubleshootingWarning)
        is CheckResult.Failed -> context.getColor(android.R.color.holo_red_dark)
        is CheckResult.Loading -> context.getColor(android.R.color.darker_gray)
        is CheckResult.Unavailable -> context.getColor(android.R.color.darker_gray)
        is CheckResult.Error -> context.getColor(android.R.color.holo_red_dark)
    }

/**
 * Returns a [ResolveCheckAction] for the check.
 */
context(fragment: ReminderTroubleshootingFragment)
private fun TroubleshootingCheck.resolveAction(): ResolveCheckAction? {
    val context = fragment.requireContext()

    fun requestNotificationPermission(): ResolveCheckAction? =
        ResolveCheckAction(
            label = context.getString(CommonString.reminder_troubleshooting_action_grant_permission),
            logDescription = "requesting POST_NOTIFICATIONS via system dialog or app settings",
        ) {
            fragment.attemptToEnableNotifications(fragment.notificationPermissionLauncher)
        }

    fun requestReminderNotifChannelPermission(): ResolveCheckAction? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return ResolveCheckAction(
            label = context.getString(CommonString.reminder_troubleshooting_action_enable_notification_channel),
            logDescription = "opening app notification settings screen",
        ) {
            fragment.openAppNotificationsSettingsScreen(highlightedChannel = NotificationChannel.REVIEW_REMINDERS)
        }
    }

    fun requestUnrestrictedBackgroundUsage(): ResolveCheckAction {
        fun openBatteryOptimizationList() = context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        return if (Permissions.canRequestIgnoreBatteryOptimizations(context)) {
            ResolveCheckAction(
                label = context.getString(CommonString.reminder_troubleshooting_action_disable_battery_optimization),
                logDescription = "opening ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            ) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                } catch (e: ActivityNotFoundException) {
                    // not all devices can request an exemption
                    Timber.w(e, "cannot request a battery optimization exemption; opening the list")
                    openBatteryOptimizationList()
                }
            }
        } else {
            ResolveCheckAction(
                label = context.getString(CommonString.reminder_troubleshooting_action_open_battery_settings),
                logDescription = "opening ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
            ) {
                openBatteryOptimizationList()
            }
        }
    }

    fun openBatterySaverSettings() =
        ResolveCheckAction(
            label = context.getString(CommonString.reminder_troubleshooting_action_open_battery_settings),
            logDescription = "opening ACTION_BATTERY_SAVER_SETTINGS",
        ) {
            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }

    fun openExactAlarmSettings(): ResolveCheckAction? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return ResolveCheckAction(
            label = context.getString(CommonString.reminder_troubleshooting_action_grant_permission),
            logDescription = "opening ACTION_REQUEST_SCHEDULE_EXACT_ALARM",
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        }
    }

    return when (this) {
        is TroubleshootingCheck.NotificationPermission -> requestNotificationPermission()
        is TroubleshootingCheck.NotificationChannelEnabled -> requestReminderNotifChannelPermission()
        is TroubleshootingCheck.DoNotDisturbOff -> null
        is TroubleshootingCheck.UnrestrictedOptimizationEnabled -> requestUnrestrictedBackgroundUsage()
        is TroubleshootingCheck.PowerSavingModeOff -> openBatterySaverSettings()
        is TroubleshootingCheck.ExactAlarmPermission -> openExactAlarmSettings()
    }
}
