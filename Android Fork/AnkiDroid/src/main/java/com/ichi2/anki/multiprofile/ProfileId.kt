// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import java.util.UUID

/**
 * A type-safe identifier for a user profile.
 */
@JvmInline
value class ProfileId(
    val value: String,
) {
    companion object {
        val DEFAULT = ProfileId("default")

        /**
         * Generates a new, random Profile ID strictly adhering to the format "p_" + 8 hex chars.
         */
        fun generate(): ProfileId {
            // "p_" prefix ensures folder safety (no collision with reserved words) and
            // substring(0, 8) gives enough entropy for local profiles
            // We use substring(0, 8) to extract the first 8 characters of a UUIDv4.
            // A standard UUIDv4 is formatted as: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
            // by extracting the first chunk, we isolate 32 bits of purely random data,
            // strictly avoiding the constant version bit ('4') and variant bit ('y').
            return ProfileId("p_" + UUID.randomUUID().toString().substring(0, 8))
        }
    }

    fun isDefault(): Boolean = this == DEFAULT

    override fun toString(): String = value
}
