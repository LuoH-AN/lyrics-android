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

public val Icons.Filled.MotionPhotosOff: ImageVector
    get() {
        if (_motionPhotosOff != null) {
            return _motionPhotosOff!!
        }
        _motionPhotosOff = materialIcon(name = "Filled.MotionPhotosOff") {
            materialPath {
                moveTo(20.84f, 20.84f)
                lineTo(3.16f, 3.16f)
                lineTo(1.89f, 4.43f)
                lineToRelative(1.89f, 1.89f)
                curveTo(2.66f, 7.93f, 2.0f, 9.89f, 2.0f, 12.0f)
                curveToRelative(0.0f, 5.52f, 4.48f, 10.0f, 10.0f, 10.0f)
                curveToRelative(2.11f, 0.0f, 4.07f, -0.66f, 5.68f, -1.77f)
                lineToRelative(1.89f, 1.89f)
                lineToRelative(1.27f, -1.28f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                curveToRelative(0.0f, -1.55f, 0.45f, -3.0f, 1.22f, -4.23f)
                lineToRelative(1.46f, 1.46f)
                curveTo(6.25f, 10.06f, 6.0f, 11.0f, 6.0f, 12.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                curveToRelative(1.0f, 0.0f, 1.94f, -0.25f, 2.77f, -0.68f)
                lineToRelative(1.46f, 1.46f)
                curveTo(15.0f, 19.55f, 13.55f, 20.0f, 12.0f, 20.0f)
                close()
                moveTo(6.32f, 3.77f)
                curveTo(7.93f, 2.66f, 9.89f, 2.0f, 12.0f, 2.0f)
                curveToRelative(5.52f, 0.0f, 10.0f, 4.48f, 10.0f, 10.0f)
                curveToRelative(0.0f, 2.11f, -0.66f, 4.07f, -1.77f, 5.68f)
                lineToRelative(-1.45f, -1.45f)
                curveTo(19.55f, 15.0f, 20.0f, 13.55f, 20.0f, 12.0f)
                curveToRelative(0.0f, -4.41f, -3.59f, -8.0f, -8.0f, -8.0f)
                curveToRelative(-1.55f, 0.0f, -3.0f, 0.45f, -4.23f, 1.22f)
                lineTo(6.32f, 3.77f)
                close()
                moveTo(18.0f, 12.0f)
                curveToRelative(0.0f, 1.0f, -0.25f, 1.94f, -0.68f, 2.77f)
                lineTo(9.23f, 6.68f)
                curveTo(10.06f, 6.25f, 11.0f, 6.0f, 12.0f, 6.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                close()
            }
        }
        return _motionPhotosOff!!
    }

private var _motionPhotosOff: ImageVector? = null
