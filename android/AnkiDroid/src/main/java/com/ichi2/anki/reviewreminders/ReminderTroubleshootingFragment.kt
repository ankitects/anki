/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.databinding.FragmentReminderTroubleshootingBinding
import com.ichi2.anki.databinding.ItemTroubleshootingCheckBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.onWindowFocusChanged
import com.ichi2.anki.utils.ext.setBackgroundTint
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import com.ichi2.utils.dp
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

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

    private val binding by viewBinding(FragmentReminderTroubleshootingBinding::bind)

    internal val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshChecks()
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        setupSummary()
        setupTroubleshootingChecks()
        setupSettingChangeDetector()
    }

    private fun setupSummary() {
        viewModel.state.launchCollectionInLifecycleScope { state ->
            val context = requireContext()
            val (iconRes, tintColor, summaryText) =
                when (state.summaryStatus) {
                    SummaryStatus.Error ->
                        Triple(
                            R.drawable.ic_cancel_24,
                            context.getColor(android.R.color.holo_red_dark),
                            "Reminders are unavailable.",
                        )

                    SummaryStatus.Warning ->
                        Triple(
                            R.drawable.ic_warning_24,
                            getColorFromAttr(context, R.attr.reminderTroubleshootingWarning),
                            "Reminders may not work correctly.",
                        )

                    SummaryStatus.Ok ->
                        Triple(
                            R.drawable.ic_check_circle_24,
                            getColorFromAttr(context, R.attr.reminderTroubleshootingOk),
                            "Your reminders should work as expected.",
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
        viewModel.state.launchCollectionInLifecycleScope { state ->
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

        holder.binding.title.text = check.title()
        holder.binding.statusName.text = check.statusName()

        val explanationText = check.explanation()
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
                    Timber.i("Launching fix for '%s': %s", check.title(), resolveAction.logDescription)
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

// TODO: move to string resources
private fun TroubleshootingCheck.title(): String =
    when (this) {
        is TroubleshootingCheck.NotificationPermission -> "Notification permission"
        is TroubleshootingCheck.DoNotDisturbOff -> "Do not disturb"
        is TroubleshootingCheck.UnrestrictedOptimizationEnabled -> "Battery optimization"
        is TroubleshootingCheck.PowerSavingModeOff -> "Power saving mode"
        is TroubleshootingCheck.ExactAlarmPermission -> "Alarms & reminders permission"
    }

// TODO: move to string resources
private fun TroubleshootingCheck.statusName(): String? =
    when (this) {
        is TroubleshootingCheck.NotificationPermission -> if (result == CheckResult.Passed) "Granted" else "Denied"
        is TroubleshootingCheck.DoNotDisturbOff -> if (result == CheckResult.Passed) "Off" else "On"
        is TroubleshootingCheck.UnrestrictedOptimizationEnabled ->
            when (result) {
                is CheckResult.Passed -> "Unrestricted"
                is CheckResult.Warning -> "Optimized"
                is CheckResult.Failed -> "Restricted"
                else -> null
            }
        is TroubleshootingCheck.PowerSavingModeOff -> if (result == CheckResult.Passed) "Off" else "On"
        is TroubleshootingCheck.ExactAlarmPermission -> if (result == CheckResult.Passed) "Granted" else "Denied"
    }

// TODO: move to string resources
private fun TroubleshootingCheck.explanation(): String? =
    when (this) {
        // no need for an explanation: the 'grant permission' action should be sufficient
        is TroubleshootingCheck.NotificationPermission -> null
        is TroubleshootingCheck.DoNotDisturbOff ->
            if (result.hasIssue) "Do Not Disturb may mute reminder notifications" else null
        is TroubleshootingCheck.UnrestrictedOptimizationEnabled ->
            when (result) {
                is CheckResult.Warning -> "Battery optimization may delay reminders"
                is CheckResult.Failed -> "Background usage is disabled. Reminders may not be delivered"
                else -> null
            }
        is TroubleshootingCheck.PowerSavingModeOff ->
            if (result.hasIssue) "Power saving mode may prevent timely delivery of reminders" else null
        is TroubleshootingCheck.ExactAlarmPermission ->
            if (result.hasIssue) "Required to schedule reminders at exact times" else null
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
        is CheckResult.Passed -> getColorFromAttr(context, R.attr.reminderTroubleshootingOk)
        is CheckResult.Warning -> getColorFromAttr(context, R.attr.reminderTroubleshootingWarning)
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

    // TODO: move labels to string resources
    fun requestNotificationPermission(): ResolveCheckAction? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return ResolveCheckAction(
            label = "Grant permission",
            logDescription = "requesting POST_NOTIFICATIONS via system dialog or app settings",
        ) {
            fragment.requestPermissionThroughDialogOrSettings(
                activity = fragment.requireActivity(),
                permission = Manifest.permission.POST_NOTIFICATIONS,
                permissionRequestedFlag = Prefs::notificationsPermissionRequested,
                permissionRequestLauncher = fragment.notificationPermissionLauncher,
            )
        }
    }

    // Opens the full battery optimization list. The user must manually find the app.
    // For 'full' (non-Play) builds, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS could be used
    // with the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS manifest permission for a direct dialog,
    // but Google Play restricts that permission.
    fun requestUnrestrictedBackgroundUsage() =
        ResolveCheckAction(label = "Open battery settings", logDescription = "opening ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS") {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

    fun openBatterySaverSettings() =
        ResolveCheckAction(label = "Open battery settings", logDescription = "opening ACTION_BATTERY_SAVER_SETTINGS") {
            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }

    fun openExactAlarmSettings(): ResolveCheckAction? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return ResolveCheckAction(label = "Grant permission", logDescription = "opening ACTION_REQUEST_SCHEDULE_EXACT_ALARM") {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:${context.packageName}".toUri()
                },
            )
        }
    }

    return when (this) {
        is TroubleshootingCheck.NotificationPermission -> requestNotificationPermission()
        is TroubleshootingCheck.DoNotDisturbOff -> null
        is TroubleshootingCheck.UnrestrictedOptimizationEnabled -> requestUnrestrictedBackgroundUsage()
        is TroubleshootingCheck.PowerSavingModeOff -> openBatterySaverSettings()
        is TroubleshootingCheck.ExactAlarmPermission -> openExactAlarmSettings()
    }
}
