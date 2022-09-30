/*
 *
 *  Copyright (c) 2022 mobile.dev inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package maestro.orchestra.yaml

import com.fasterxml.jackson.annotation.JsonCreator

data class YamlInputTextRandom(
    // specify the input method type: text (default), number, textCapCharacters phone, textCapWords, textMultiLine, textImeMultiLine, ... 
    val inputType: String?,
    // length of random text, default 8
    val length: Int?,
) {
    companion object {

        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun parse(type: String): YamlInputTextRandom {
            return YamlInputTextRandom(
                inputType = type,
                length = 8,
            )
        }
    }
}
