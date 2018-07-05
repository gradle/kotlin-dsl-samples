/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory

import com.intellij.util.ThrowableRunnable

import org.jetbrains.kotlin.idea.core.script.isScriptDependenciesUpdaterDisabled

import kotlin.reflect.KMutableProperty0


abstract class GradleImportingCodeInsightTestCase : GradleImportingTestCase() {

    private
    lateinit var codeInsightTestFixture: CodeInsightTestFixture

    override fun setUpFixtures() {
        val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
        myTestFixture = fixtureFactory.createFixtureBuilder(getName()).fixture
        codeInsightTestFixture = fixtureFactory.createCodeInsightFixture(myTestFixture)
        codeInsightTestFixture.setUp()
        ApplicationManager.getApplication().isScriptDependenciesUpdaterDisabled = true
    }

    override fun tearDownFixtures() {
        ApplicationManager.getApplication().isScriptDependenciesUpdaterDisabled = false
        codeInsightTestFixture.tearDown()
        @Suppress("unchecked_cast")
        (this::codeInsightTestFixture as KMutableProperty0<CodeInsightTestFixture?>).set(null)
        myTestFixture = null
    }

    protected
    fun runInEdtAndWait(runnable: () -> Unit) {
        EdtTestUtil.runInEdtAndWait(ThrowableRunnable { runnable() })
//        ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL)
    }

    protected
    fun checkHighlighting() =
        codeInsightTestFixture.checkHighlighting()

    protected
    fun openFileInEditor(buildFile: VirtualFile) =
        codeInsightTestFixture.openFileInEditor(buildFile)
}
