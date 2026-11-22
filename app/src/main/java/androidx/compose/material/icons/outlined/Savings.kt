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

public val Icons.Outlined.Savings: ImageVector
    get() {
        if (_savings != null) {
            return _savings!!
        }
        _savings = materialIcon(name = "Outlined.Savings") {
            materialPath {
                moveTo(15.0f, 10.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveTo(15.0f, 10.55f, 15.0f, 10.0f)
                close()
                moveTo(8.0f, 9.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(22.0f, 7.5f)
                verticalLineToRelative(6.97f)
                lineToRelative(-2.82f, 0.94f)
                lineTo(17.5f, 21.0f)
                lineTo(12.0f, 21.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                lineToRelative(-5.5f, 0.0f)
                curveTo(4.5f, 21.0f, 2.0f, 12.54f, 2.0f, 9.5f)
                reflectiveCurveTo(4.46f, 4.0f, 7.5f, 4.0f)
                lineToRelative(5.0f, 0.0f)
                curveToRelative(0.91f, -1.21f, 2.36f, -2.0f, 4.0f, -2.0f)
                curveTo(17.33f, 2.0f, 18.0f, 2.67f, 18.0f, 3.5f)
                curveToRelative(0.0f, 0.21f, -0.04f, 0.4f, -0.12f, 0.58f)
                curveToRelative(-0.14f, 0.34f, -0.26f, 0.73f, -0.32f, 1.15f)
                lineToRelative(2.27f, 2.27f)
                horizontalLineTo(22.0f)
                close()
                moveTo(20.0f, 9.5f)
                horizontalLineToRelative(-1.0f)
                lineTo(15.5f, 6.0f)
                curveToRelative(0.0f, -0.65f, 0.09f, -1.29f, 0.26f, -1.91f)
                curveTo(14.79f, 4.34f, 14.0f, 5.06f, 13.67f, 6.0f)
                lineTo(7.5f, 6.0f)
                curveTo(5.57f, 6.0f, 4.0f, 7.57f, 4.0f, 9.5f)
                curveToRelative(0.0f, 1.88f, 1.22f, 6.65f, 2.01f, 9.5f)
                lineTo(8.0f, 19.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(2.0f)
                lineToRelative(2.01f, 0.0f)
                lineToRelative(1.55f, -5.15f)
                lineTo(20.0f, 13.03f)
                verticalLineTo(9.5f)
                close()
            }
        }
        return _savings!!
    }

private var _savings: ImageVector? = null
