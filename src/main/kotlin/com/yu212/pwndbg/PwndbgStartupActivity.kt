package com.yu212.pwndbg

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.yu212.pwndbg.ui.PwndbgToolWindowManager

class PwndbgStartupActivity: ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(PwndbgService::class.java).init()
        project.getService(PwndbgToolWindowManager::class.java).ensureInitialized()
        PwndbgDapDebuggerRegistrar.ensureRegistered()
    }
}
