/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luoh.music.lrc.ui.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Filled.FormatAlignLeft",
    ReplaceWith( "Icons.AutoMirrored.Filled.FormatAlignLeft",
            "androidx.compose.material.icons.automirrored.filled.FormatAlignLeft"),
)
public val Icons.Filled.FormatAlignLeft: ImageVector
    get() {
        if (_formatAlignLeft != null) {
            return _formatAlignLeft!!
        }
        _formatAlignLeft = materialIcon(name = "Filled.FormatAlignLeft") {
            materialPath {
                moveTo(15.0f, 15.0f)
                lineTo(3.0f, 15.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(15.0f, 7.0f)
                lineTo(3.0f, 7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.0f)
                lineTo(15.0f, 7.0f)
                close()
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 21.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 19.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(18.0f)
                lineTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                close()
            }
        }
        return _formatAlignLeft!!
    }

private var _formatAlignLeft: ImageVector? = null
