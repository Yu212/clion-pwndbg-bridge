package com.yu212.pwndbg

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class PwndbgStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(PwndbgService::class.java).init()
        PwndbgDapDebuggerRegistrar.ensureRegistered()
    }
}
