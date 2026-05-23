/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.hasAllPermissions
import com.ichi2.anki.databinding.ViewPermissionsItemBinding
import com.ichi2.anki.utils.ext.usingStyledAttributes
import timber.log.Timber

/**
 * Layout item that can be used to request permissions from the user.
 *
 * Multiple related permissions can be requested together.
 * E.g. READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE.
 *
 *
 * XML attributes:
 * * app:permissionTitle ([R.styleable.PermissionItem_permissionTitle]):
 *     Title of the permission
 * * app:permissionSummary ([R.styleable.PermissionItem_permissionSummary]):
 *     Brief description of the permission. It can be used to explain to the user
 *     why the permission should be granted
 * * app:permissionIcon ([R.styleable.PermissionItem_permissionIcon]):
 *     Icon to be shown at the frame side
 * * app:permission ([R.styleable.PermissionItem_permission]):
 *     Permission string to be asked. Will be overridden by app:permissions if set.
 * * app:permissions ([R.styleable.PermissionItem_permissions]):
 *     Array of permission strings to be asked. Overrides app:permission if set
 *
 * @see R.layout.permissions_item
 */
class PermissionsItem(
    context: Context,
    attrs: AttributeSet,
) : FrameLayout(context, attrs) {
    val binding = ViewPermissionsItemBinding.inflate(LayoutInflater.from(context), this, true)

    /**
     * The value of either app:permissions or app:permission.
     */
    val permissions: List<String>
    val areGranted get() = hasAllPermissions(context, permissions)

    init {
        binding.switchWidget.apply {
            isEnabled = true
            setOnCheckedChangeListener { button, _ ->
                button.isChecked = areGranted
            }
        }

        permissions =
            context.usingStyledAttributes(attrs, R.styleable.PermissionItem) {
                getTextArray(R.styleable.PermissionItem_permissions)?.map { it.toString() }
                    ?: getString(R.styleable.PermissionItem_permission)?.let { listOf(it) }
                    ?: throw IllegalArgumentException("Either app:permission or app:permissions should be set")
            }

        context.withStyledAttributes(attrs, R.styleable.PermissionItem) {
            binding.title.text = getText(R.styleable.PermissionItem_permissionTitle)
            binding.summary.text = getText(R.styleable.PermissionItem_permissionSummary)

            val icon = getDrawable(R.styleable.PermissionItem_permissionIcon)
            icon?.let {
                val color = MaterialColors.getColor(this@PermissionsItem, android.R.attr.colorControlNormal)
                binding.icon.apply {
                    setImageDrawable(it)
                    imageTintList = ColorStateList.valueOf(color)
                }
            }
        }
        setOnClickListener {
            Timber.i("Permission item clicked, requesting permissions: $areGranted")
            permissionsRequested?.invoke(areGranted)
        }

        binding.switchWidget.setOnClickListener {
            Timber.i("Permission switch clicked, requesting permissions: $areGranted")
            permissionsRequested?.invoke(areGranted)
        }
        updateSwitchCheckedStatus()
    }

    /**
     * Executed when the user is requesting permission.
     * Provides whether the permission was granted before this switch. Set by [setOnPermissionsRequested].
     */
    private var permissionsRequested: ((areAlreadyGranted: Boolean) -> Unit)? = null

    /**
     * Checks the switch if the permissions are granted,
     * or uncheck if not
     */
    fun updateSwitchCheckedStatus() {
        binding.switchWidget.isChecked = areGranted
    }

    /**
     * When the user request permissions, [onPermissionsRequested] is called.
     * */
    fun setOnPermissionsRequested(onPermissionsRequested: (areAlreadyGranted: Boolean) -> Unit) {
        this.permissionsRequested = onPermissionsRequested
    }
}
