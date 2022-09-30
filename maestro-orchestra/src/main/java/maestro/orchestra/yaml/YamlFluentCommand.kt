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
import maestro.KeyCode
import maestro.Point
import maestro.orchestra.AssertCommand
import maestro.orchestra.BackPressCommand
import maestro.orchestra.ClearKeychainCommand
import maestro.orchestra.ElementSelector
import maestro.orchestra.ElementTrait
import maestro.orchestra.EraseTextCommand
import maestro.orchestra.HideKeyboardCommand
import maestro.orchestra.InputTextCommand
import maestro.orchestra.InputTextRandomCommand
import maestro.orchestra.LaunchAppCommand
import maestro.orchestra.MaestroCommand
import maestro.orchestra.OpenLinkCommand
import maestro.orchestra.PressKeyCommand
import maestro.orchestra.ScrollCommand
import maestro.orchestra.StopAppCommand
import maestro.orchestra.SwipeCommand
import maestro.orchestra.TakeScreenshotCommand
import maestro.orchestra.TapOnElementCommand
import maestro.orchestra.TapOnPointCommand
import maestro.orchestra.error.InvalidInitFlowFile
import maestro.orchestra.error.SyntaxError
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

data class YamlFluentCommand(
    val tapOn: YamlElementSelectorUnion? = null,
    val longPressOn: YamlElementSelectorUnion? = null,
    val assertVisible: YamlElementSelectorUnion? = null,
    val assertNotVisible: YamlElementSelectorUnion? = null,
    val action: String? = null,
    val inputText: String? = null,
    val inputTextRandom: YamlInputTextRandom? = null,
    val launchApp: YamlLaunchApp? = null,
    val swipe: YamlElementSelectorUnion? = null,
    val openLink: String? = null,
    val pressKey: String? = null,
    val eraseText: YamlEraseText? = null,
    val takeScreenshot: YamlTakeScreenshot? = null,
    val extendedWaitUntil: YamlExtendedWaitUntil? = null,
    val stopApp: YamlStopApp? = null,
    val clearState: YamlClearState? = null,
    val runFlow: YamlRunFlow? = null,
) {

    @SuppressWarnings("ComplexMethod")
    fun toCommands(flowPath: Path, appId: String): List<MaestroCommand> {
        return when {
            launchApp != null -> listOf(launchApp(launchApp, appId))
            tapOn != null -> listOf(tapCommand(tapOn))
            longPressOn != null -> listOf(tapCommand(longPressOn, longPress = true))
            assertVisible != null -> listOf(MaestroCommand(AssertCommand(visible = toElementSelector(assertVisible))))
            assertNotVisible != null -> listOf(MaestroCommand(AssertCommand(notVisible = toElementSelector(assertNotVisible))))
            inputText != null -> listOf(MaestroCommand(InputTextCommand(inputText)))
            inputTextRandom != null -> listOf(inputTextRandom(inputTextRandom))
            swipe != null -> listOf(swipeCommand(swipe))
            openLink != null -> listOf(MaestroCommand(OpenLinkCommand(openLink)))
            pressKey != null -> listOf(MaestroCommand(PressKeyCommand(code = KeyCode.getByName(pressKey) ?: throw SyntaxError("Unknown key name: $pressKey"))))
            eraseText != null -> listOf(MaestroCommand(EraseTextCommand(charactersToErase = eraseText.charactersToErase)))
            action != null -> listOf(
                when (action) {
                    "back" -> MaestroCommand(BackPressCommand())
                    "hide keyboard" -> MaestroCommand(HideKeyboardCommand())
                    "scroll" -> MaestroCommand(ScrollCommand())
                    "clearKeychain" -> MaestroCommand(ClearKeychainCommand())
                    else -> error("Unknown navigation target: $action")
                }
            )
            takeScreenshot != null -> listOf(MaestroCommand(TakeScreenshotCommand(takeScreenshot.path)))
            extendedWaitUntil != null -> listOf(extendedWait(extendedWaitUntil))
            stopApp != null -> listOf(
                MaestroCommand(
                    StopAppCommand(
                        appId = stopApp.appId ?: appId,
                    )
                )
            )
            clearState != null -> listOf(
                MaestroCommand(
                    maestro.orchestra.ClearStateCommand(
                        appId = clearState.appId ?: appId,
                    )
                )
            )
            runFlow != null -> runFlow(flowPath, runFlow)
            else -> throw SyntaxError("Invalid command: No mapping provided for $this")
        }
    }

    fun getWatchFiles(flowPath: Path): List<Path> {
        return when {
            runFlow != null -> getRunFlowWatchFiles(flowPath, runFlow)
            else -> return emptyList()
        }
    }

    private fun getRunFlowWatchFiles(flowPath: Path, runFlow: YamlRunFlow): List<Path> {
        val runFlowPath = getRunFlowPath(flowPath, runFlow.path)
        return listOf(runFlowPath) + YamlCommandReader.getWatchFiles(runFlowPath)
    }

    private fun runFlow(flowPath: Path, command: YamlRunFlow): List<MaestroCommand> {
        val runFlowPath = getRunFlowPath(flowPath, command.path)
        return YamlCommandReader.readCommands(runFlowPath)
    }

    private fun getRunFlowPath(flowPath: Path, runFlowPath: String): Path {
        val initFlowPath = flowPath.fileSystem.getPath(runFlowPath)
        val resolvedInitFlowPath = if (initFlowPath.isAbsolute) {
            initFlowPath
        } else {
            flowPath.resolveSibling(initFlowPath).toAbsolutePath()
        }
        if (resolvedInitFlowPath.equals(flowPath.toAbsolutePath())) {
            throw InvalidInitFlowFile("initFlow file can't be the same as the Flow file: ${resolvedInitFlowPath.toUri()}", resolvedInitFlowPath)
        }
        if (!resolvedInitFlowPath.exists()) {
            throw InvalidInitFlowFile("initFlow file does not exist: ${resolvedInitFlowPath.toUri()}", resolvedInitFlowPath)
        }
        if (resolvedInitFlowPath.isDirectory()) {
            throw InvalidInitFlowFile("initFlow file can't be a directory: ${resolvedInitFlowPath.toUri()}", resolvedInitFlowPath)
        }
        return resolvedInitFlowPath
    }

    private fun extendedWait(command: YamlExtendedWaitUntil): MaestroCommand {
        if (command.visible == null && command.notVisible == null) {
            throw SyntaxError("extendedWaitUntil expects either `visible` or `notVisible` to be provided")
        }

        return MaestroCommand(
            AssertCommand(
                visible = command.visible?.let { toElementSelector(it) },
                notVisible = command.notVisible?.let { toElementSelector(it) },
                timeout = command.timeout,
            )
        )
    }

    private fun launchApp(command: YamlLaunchApp, appId: String): MaestroCommand {
        return MaestroCommand(
            LaunchAppCommand(
                appId = command.appId ?: appId,
                clearState = command.clearState,
                clearKeychain = command.clearKeychain,
            )
        )
    }

    private fun inputTextRandom(command: YamlInputTextRandom): MaestroCommand {
        return MaestroCommand(InputTextRandomCommand(
            inputType = command.inputType,
            length = command.length,
        ),)
    }

    private fun tapCommand(
        tapOn: YamlElementSelectorUnion,
        longPress: Boolean = false,
    ): MaestroCommand {
        val retryIfNoChange = (tapOn as? YamlElementSelector)?.retryTapIfNoChange ?: true
        val waitUntilVisible = (tapOn as? YamlElementSelector)?.waitUntilVisible ?: true
        val point = (tapOn as? YamlElementSelector)?.point

        return if (point != null) {
            val points = point.split(",")
                .map {
                    it.trim().toInt()
                }

            MaestroCommand(
                TapOnPointCommand(
                    x = points[0],
                    y = points[1],
                    retryIfNoChange = retryIfNoChange,
                    waitUntilVisible = waitUntilVisible,
                    longPress = longPress,
                )
            )
        } else {
            MaestroCommand(
                command = TapOnElementCommand(
                    selector = toElementSelector(tapOn),
                    retryIfNoChange = retryIfNoChange,
                    waitUntilVisible = waitUntilVisible,
                    longPress = longPress,
                )
            )
        }
    }

    private fun longPressCommand(tapOn: YamlElementSelectorUnion): MaestroCommand {
        val retryIfNoChange = (tapOn as? YamlElementSelector)?.retryTapIfNoChange ?: true
        val waitUntilVisible = (tapOn as? YamlElementSelector)?.waitUntilVisible ?: true
        val point = (tapOn as? YamlElementSelector)?.point

        return if (point != null) {
            val points = point.split(",")
                .map {
                    it.trim().toInt()
                }

            MaestroCommand(
                TapOnPointCommand(
                    x = points[0],
                    y = points[1],
                    retryIfNoChange = retryIfNoChange,
                    waitUntilVisible = waitUntilVisible,
                )
            )
        } else {
            MaestroCommand(
                command = TapOnElementCommand(
                    selector = toElementSelector(tapOn),
                    retryIfNoChange = retryIfNoChange,
                    waitUntilVisible = waitUntilVisible,
                )
            )
        }
    }

    private fun swipeCommand(tapOn: YamlElementSelectorUnion): MaestroCommand {
        val start = (tapOn as? YamlElementSelector)?.start
        val end = (tapOn as? YamlElementSelector)?.end
        val startPoint: Point?
        val endPoint: Point?

        if (start != null) {
            val points = start.split(",")
                .map {
                    it.trim().toInt()
                }

            startPoint = Point(points[0], points[1])
        } else {
            throw IllegalStateException("No start point configured for swipe action")
        }

        if (end != null) {
            val points = end.split(",")
                .map {
                    it.trim().toInt()
                }

            endPoint = Point(points[0], points[1])
        } else {
            throw IllegalStateException("No end point configured for swipe action")
        }

        return MaestroCommand(SwipeCommand(startPoint, endPoint))
    }

    private fun toElementSelector(selectorUnion: YamlElementSelectorUnion): ElementSelector {
        return if (selectorUnion is StringElementSelector) {
            ElementSelector(
                textRegex = selectorUnion.value,
            )
        } else if (selectorUnion is YamlElementSelector) {
            toElementSelector(selectorUnion)
        } else {
            throw IllegalStateException("Unknown selector type: $selectorUnion")
        }
    }

    private fun toElementSelector(selector: YamlElementSelector): ElementSelector {
        val size = if (selector.width != null || selector.height != null) {
            ElementSelector.SizeSelector(
                width = selector.width,
                height = selector.height,
                tolerance = selector.tolerance,
            )
        } else {
            null
        }

        return ElementSelector(
            textRegex = selector.text,
            idRegex = selector.id,
            size = size,
            below = selector.below?.let { toElementSelector(it) },
            above = selector.above?.let { toElementSelector(it) },
            leftOf = selector.leftOf?.let { toElementSelector(it) },
            rightOf = selector.rightOf?.let { toElementSelector(it) },
            containsChild = selector.containsChild?.let { toElementSelector(it) },
            traits = selector.traits
                ?.split(" ")
                ?.map { ElementTrait.valueOf(it.replace('-', '_').uppercase()) },
            index = selector.index,
            optional = selector.optional ?: false,
        )
    }

    companion object {

        @Suppress("unused")
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun parse(stringCommand: String): YamlFluentCommand {
            return when (stringCommand) {
                "launchApp" -> YamlFluentCommand(
                    launchApp = YamlLaunchApp(
                        appId = null,
                        clearState = null,
                        clearKeychain = null,
                    )
                )

                "stopApp" -> YamlFluentCommand(
                    stopApp = YamlStopApp()
                )

                "clearState" -> YamlFluentCommand(
                    clearState = YamlClearState()
                )

                "clearKeychain" -> YamlFluentCommand(
                    action = "clearKeychain"
                )

                "eraseText" -> YamlFluentCommand(
                    eraseText = YamlEraseText(charactersToErase = 50)
                )

                "back" -> YamlFluentCommand(
                    action = "back"
                )

                "hide keyboard", "hideKeyboard" -> YamlFluentCommand(
                    action = "hide keyboard"
                )

                "scroll" -> YamlFluentCommand(
                    action = "scroll"
                )

                "inputTextRandom" -> YamlFluentCommand(
                    inputTextRandom = YamlInputTextRandom(inputType = "text", length = 8,)
                )

                else -> throw SyntaxError("Invalid command: \"$stringCommand\"")
            }
        }
    }
}
