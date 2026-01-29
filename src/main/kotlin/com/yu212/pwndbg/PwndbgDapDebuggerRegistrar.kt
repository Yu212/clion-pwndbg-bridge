package com.yu212.pwndbg

import com.intellij.openapi.diagnostic.Logger

object PwndbgDapDebuggerRegistrar {
    private val log = Logger.getInstance(PwndbgDapDebuggerRegistrar::class.java)

    const val DEFAULT_NAME = "Pwndbg GDB (DAP)"
    private const val DEFAULT_EXECUTABLE = "/usr/bin/gdb"
    // Avoid double-loading pwndbg if ~/.gdbinit already sources it.
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
            val storageClass = Class.forName("com.intellij.clion.toolchains.debugger.CLionDapDebuggersStorage")
            val stateClass = Class.forName("com.intellij.clion.toolchains.debugger.CLionDapDebuggersState")
            val settingsClass = Class.forName("com.intellij.clion.toolchains.debugger.CLionDapDebuggerSettings")
            val storageKtClass = Class.forName("com.intellij.clion.toolchains.debugger.CLionDapDebuggersStorageKt")

            val storage = storageClass.getMethod("getInstance").invoke(null)
            val state = storageClass.getMethod("getSnapshot").invoke(storage)
            val debuggers = stateClass.getMethod("getDebuggers").invoke(state) as List<*>

            val getName = settingsClass.getMethod("getName")
            val getArgs = settingsClass.getMethod("getDebuggerArguments")
            val existing = debuggers.firstOrNull { item ->
                val name = getName.invoke(item) as? String ?: return@firstOrNull false
                val args = getArgs.invoke(item) as? String ?: ""
                name == DEFAULT_NAME || args.contains("pwndbg")
            }
            if (existing != null) {
                val name = getName.invoke(existing) as? String ?: DEFAULT_NAME
                val setAttachParams = settingsClass.getMethod("setAttachParameters", String::class.java)
                setAttachParams.invoke(existing, DEFAULT_ATTACH_PARAMS)
                storageClass.getMethod("save", stateClass).invoke(storage, state)
                log.info("Pwndbg: DAP debugger already registered: $name (attach params updated)")
                return
            }

            val settings = settingsClass.getConstructor().newInstance()
            settingsClass.getMethod("setId", String::class.java).invoke(settings, "pwndbg-gdb-dap")
            settingsClass.getMethod("setName", String::class.java).invoke(settings, DEFAULT_NAME)
            settingsClass.getMethod("setDebuggerExecutable", String::class.java).invoke(settings, DEFAULT_EXECUTABLE)
            settingsClass.getMethod("setDebuggerArguments", String::class.java).invoke(settings, DEFAULT_ARGUMENTS)
            settingsClass.getMethod("setLaunchParameters", String::class.java).invoke(settings, DEFAULT_LAUNCH_PARAMS)
            settingsClass.getMethod("setAttachParameters", String::class.java).invoke(settings, DEFAULT_ATTACH_PARAMS)

            val createDebugger = storageKtClass.getMethod("createDebugger", stateClass, settingsClass)
            createDebugger.invoke(null, state, settings)

            storageClass.getMethod("save", stateClass).invoke(storage, state)
            log.info("Pwndbg: Registered DAP debugger: $DEFAULT_NAME")
        } catch (t: Throwable) {
            log.warn("Pwndbg: Failed to register DAP debugger", t)
        }
    }
}
