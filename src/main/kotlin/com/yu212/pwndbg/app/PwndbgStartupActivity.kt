package com.yu212.pwndbg.app

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.yu212.pwndbg.debug.DapDebuggerRegistrar

class PwndbgStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(PwndbgService::class.java).init()
        project.getService(PwndbgToolWindowManager::class.java).ensureInitialized()
        DapDebuggerRegistrar.ensureRegistered()
    }
}
