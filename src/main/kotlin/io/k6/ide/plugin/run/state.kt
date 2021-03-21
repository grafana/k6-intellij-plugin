package com.devexperts.K6.plug.idea.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.FileUrlProvider
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import io.k6.ide.plugin.actions.TOKEN_ENV_NAME
import io.k6.ide.plugin.run.K6RunConfig
import io.k6.ide.plugin.run.RunType
import io.k6.ide.plugin.settings.K6Settings
import java.io.File
import java.nio.charset.Charset
import java.util.*

class K6ConsoleProperties(config: K6RunConfig, executor: Executor): SMTRunnerConsoleProperties(config, "k6", executor)

class K6RunState(val myEnv: ExecutionEnvironment, val myRunConfiguration: K6RunConfig) : RunProfileState {
    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
        val myConsoleProperties = K6ConsoleProperties(myRunConfiguration, myEnv.executor)
        val createConsole = SMTestRunnerConnectionUtil.createConsole( myConsoleProperties.testFrameworkName, myConsoleProperties)
        val data = myRunConfiguration.data
        val generalCommandLine = GeneralCommandLine("k6", if (data.type == RunType.local) "run" else "cloud", data.script,
                    *translateCommandline(data.additionalParams ?: ""))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.SYSTEM)
        val commandLine = if (data.pty) PtyCommandLine(generalCommandLine).withInitialColumns(120) else generalCommandLine
        commandLine.charset = Charset.forName("UTF-8")
        commandLine.environment.putAll(data.envs)
        if (data.type == RunType.cloud) {
            K6Settings.instance.cloudToken.takeIf { it.isNotBlank() }?.let { commandLine.environment.put(TOKEN_ENV_NAME, it)}
        }
        myEnv.project.guessProjectDir()?.let {
            commandLine.setWorkDirectory(it.path)
        }

        val processHandler = KillableColoredProcessHandler(commandLine)
        createConsole.attachToProcess(processHandler)
        processHandler.setHasPty(true)
        val smTestProxy = (createConsole as SMTRunnerConsoleView).resultsViewer.root as SMTestProxy.SMRootTestProxy
        smTestProxy.setTestsReporterAttached()
        smTestProxy.setPrinter(null)
        smTestProxy.setStarted()
        val child = SMTestProxy(File(data.script!!).name, false, data.script?.let { LocalFileSystem.getInstance().findFileByPath(it)?.url })
        smTestProxy.addChild(child)
        child.setStarted()
        child.locator = FileUrlProvider.INSTANCE
        child.setPrinter(null)
        smTestProxy.setSuiteStarted()
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                if (event.exitCode != 0) {
                    child.setTestFailed("Test failed", null, false)
                    smTestProxy.setTestFailed("Test failed", null, false)
                }
            }

            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                child.setFinished()
                smTestProxy.setFinished()
            }
        })
        return DefaultExecutionResult(createConsole, processHandler)
    }
}

/* this function was taken from the ant lib (ant:ant:1.6.5) */
fun translateCommandline(toProcess: String): Array<String> {
    if (toProcess.isEmpty()) {
        return emptyArray()
    }

    val normal = 0
    val inQuote = 1
    val inDoubleQuote = 2
    var state = normal
    val tok = StringTokenizer(toProcess, "\"\' ", true)
    val result = mutableListOf<String>()
    var current = StringBuffer()
    var lastTokenHasBeenQuoted = false
    while (tok.hasMoreTokens()) {
        val nextTok = tok.nextToken()
        when (state) {
            inQuote -> if ("\'" == nextTok) {
                lastTokenHasBeenQuoted = true
                state = normal
            } else {
                current.append(nextTok)
            }
            inDoubleQuote -> if ("\"" == nextTok) {
                lastTokenHasBeenQuoted = true
                state = normal
            } else {
                current.append(nextTok)
            }
            else -> {
                if ("\'" == nextTok) {
                    state = inQuote
                } else if ("\"" == nextTok) {
                    state = inDoubleQuote
                } else if (" " == nextTok) {
                    if (lastTokenHasBeenQuoted || current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuffer()
                    }
                } else {
                    current.append(nextTok)
                }
                lastTokenHasBeenQuoted = false
            }
        }
    }
    if (lastTokenHasBeenQuoted || current.isNotEmpty()) {
        result.add(current.toString())
    }
    if (state == inQuote || state == inDoubleQuote) {
        error("unbalanced quotes in $toProcess")
    }
    return result.toTypedArray()
}
