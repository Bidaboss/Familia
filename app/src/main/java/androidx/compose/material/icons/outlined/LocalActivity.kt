/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.LocalActivity: ImageVector
    get() {
        if (_localActivity != null) {
            return _localActivity!!
        }
        _localActivity = materialIcon(name = "Outlined.LocalActivity") {
            materialPath {
                moveTo(22.0f, 10.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(1.1f, 0.0f, 1.99f, 0.9f, 1.99f, 2.0f)
                reflectiveCurveToRelative(-0.89f, 2.0f, -2.0f, 2.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                close()
                moveTo(20.0f, 8.54f)
                curveToRelative(-1.19f, 0.69f, -2.0f, 1.99f, -2.0f, 3.46f)
                reflectiveCurveToRelative(0.81f, 2.77f, 2.0f, 3.46f)
                lineTo(20.0f, 18.0f)
                lineTo(4.0f, 18.0f)
                verticalLineToRelative(-2.54f)
                curveToRelative(1.19f, -0.69f, 2.0f, -1.99f, 2.0f, -3.46f)
                curveToRelative(0.0f, -1.48f, -0.8f, -2.77f, -1.99f, -3.46f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(2.54f)
                close()
                moveTo(9.07f, 16.0f)
                lineTo(12.0f, 14.12f)
                lineTo(14.93f, 16.0f)
                lineToRelative(-0.89f, -3.36f)
                lineToRelative(2.69f, -2.2f)
                lineToRelative(-3.47f, -0.21f)
                lineTo(12.0f, 7.0f)
                lineToRelative(-1.27f, 3.22f)
                lineToRelative(-3.47f, 0.21f)
                lineToRelative(2.69f, 2.2f)
                close()
            }
        }
        return _localActivity!!
    }

private var _localActivity: ImageVector? = null
