package com.yu212.pwndbg

import com.intellij.clion.toolchains.debugger.CLionDapDebuggerSettings
import com.intellij.clion.toolchains.debugger.CLionDapDebuggersStorage
import com.intellij.clion.toolchains.debugger.createDebugger
import com.intellij.openapi.diagnostic.Logger

object DapDebuggerRegistrar {
    private val log = Logger.getInstance(DapDebuggerRegistrar::class.java)

    const val DEFAULT_NAME = "Pwndbg GDB (DAP)"
    private const val DEFAULT_EXECUTABLE = "/usr/bin/gdb"

    private const val DEFAULT_ARGUMENTS =
        "-i dap -q " +
                "-ex \"set inferior-tty /tmp/ttyPWN\" " +
                "-ex \"python import sys, gdb; 'pwndbg' not in sys.modules and gdb.execute('source /usr/share/pwndbg/gdbinit.py')\""

    private val DEFAULT_LAUNCH_PARAMS = $$"""
        {
          "program": "$Executable$",
          "cwd": "$WorkingDir$",
          "args": $Arguments$,
          "env": $Environment$,
          "stopAtBeginningOfMainSubprogram": true
        }
    """.trimIndent()

    private val DEFAULT_ATTACH_PARAMS = $$"""
        {
          "pid": $Pid$,
          "stopAtBeginningOfMainSubprogram": true
        }
    """.trimIndent()

    fun ensureRegistered() {
        try {
            val storage = CLionDapDebuggersStorage.getInstance()
            val state = storage.getSnapshot()
            val existing = state.debuggers.firstOrNull { it.name == DEFAULT_NAME }
            if (existing != null) {
                log.info("Pwndbg: DAP debugger already registered")
                return
            }
            val settings = CLionDapDebuggerSettings().apply {
                id = "pwndbg-gdb-dap"
                name = DEFAULT_NAME
                debuggerExecutable = DEFAULT_EXECUTABLE
                debuggerArguments = DEFAULT_ARGUMENTS
                launchParameters = DEFAULT_LAUNCH_PARAMS
                attachParameters = DEFAULT_ATTACH_PARAMS
            }
            state.createDebugger(settings)
            storage.save(state)
            log.info("Pwndbg: Registered DAP debugger: $DEFAULT_NAME")
        } catch (t: Throwable) {
            log.warn("Pwndbg: Failed to register DAP debugger", t)
        }
    }
}
