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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

// Material Icons « directions_bus », outlined (google/material-design-icons, Apache 2.0).
public val Icons.Outlined.DirectionsBus: ImageVector
    get() {
        if (_directionsBus != null) {
            return _directionsBus!!
        }
        _directionsBus = materialIcon(name = "Outlined.DirectionsBus") {
            addPath(
                pathData = addPathNodes(
                    "M12 2c-4.42 0-8 .5-8 4v10c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1" +
                        "c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4z" +
                        "m5.66 2.99H6.34C6.89 4.46 8.31 4 12 4s5.11.46 5.66.99zm.34 2V10H6V6.99h12z" +
                        "m-.34 9.74l-.29.27H6.63l-.29-.27C6.21 16.62 6 16.37 6 16v-4h12v4c0 .37-.21.62-.34.73z" +
                        "M8.5 14.5m-1.5 0a1.5 1.5 0 1 1 3 0a1.5 1.5 0 1 1 -3 0" +
                        "M15.5 14.5m-1.5 0a1.5 1.5 0 1 1 3 0a1.5 1.5 0 1 1 -3 0",
                ),
                fill = SolidColor(Color.Black),
            )
        }
        return _directionsBus!!
    }

private var _directionsBus: ImageVector? = null
