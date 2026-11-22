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

package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Rounded.Campaign: ImageVector
    get() {
        if (_campaign != null) {
            return _campaign!!
        }
        _campaign = materialIcon(name = "Rounded.Campaign") {
            materialPath {
                moveTo(18.0f, 12.0f)
                lineTo(18.0f, 12.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                curveTo(18.45f, 11.0f, 18.0f, 11.45f, 18.0f, 12.0f)
                close()
            }
            materialPath {
                moveTo(16.59f, 16.82f)
                curveToRelative(-0.33f, 0.44f, -0.24f, 1.05f, 0.2f, 1.37f)
                curveToRelative(0.53f, 0.39f, 1.09f, 0.81f, 1.62f, 1.21f)
                curveToRelative(0.44f, 0.33f, 1.06f, 0.24f, 1.38f, -0.2f)
                curveToRelative(0.0f, -0.01f, 0.01f, -0.01f, 0.01f, -0.02f)
                curveToRelative(0.33f, -0.44f, 0.24f, -1.06f, -0.2f, -1.38f)
                curveToRelative(-0.53f, -0.4f, -1.09f, -0.82f, -1.61f, -1.21f)
                curveToRelative(-0.44f, -0.33f, -1.06f, -0.23f, -1.39f, 0.21f)
                curveTo(16.6f, 16.81f, 16.59f, 16.82f, 16.59f, 16.82f)
                close()
            }
            materialPath {
                moveTo(19.81f, 4.81f)
                curveToRelative(0.0f, -0.01f, -0.01f, -0.01f, -0.01f, -0.02f)
                curveToRelative(-0.33f, -0.44f, -0.95f, -0.53f, -1.38f, -0.2f)
                curveToRelative(-0.53f, 0.4f, -1.1f, 0.82f, -1.62f, 1.22f)
                curveToRelative(-0.44f, 0.33f, -0.52f, 0.95f, -0.19f, 1.38f)
                curveToRelative(0.0f, 0.01f, 0.01f, 0.01f, 0.01f, 0.02f)
                curveToRelative(0.33f, 0.44f, 0.94f, 0.53f, 1.38f, 0.2f)
                curveToRelative(0.53f, -0.39f, 1.09f, -0.82f, 1.62f, -1.22f)
                curveTo(20.05f, 5.87f, 20.13f, 5.25f, 19.81f, 4.81f)
                close()
            }
            materialPath {
                moveTo(8.0f, 9.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(3.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(1.0f)
                lineToRelative(5.0f, 3.0f)
                verticalLineTo(6.0f)
                lineTo(8.0f, 9.0f)
                close()
            }
            materialPath {
                moveTo(15.5f, 12.0f)
                curveToRelative(0.0f, -1.33f, -0.58f, -2.53f, -1.5f, -3.35f)
                verticalLineToRelative(6.69f)
                curveTo(14.92f, 14.53f, 15.5f, 13.33f, 15.5f, 12.0f)
                close()
            }
        }
        return _campaign!!
    }

private var _campaign: ImageVector? = null
