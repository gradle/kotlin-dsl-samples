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
package org.gradle.kotlin.dsl

import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.tasks.TaskContainer


/**
 * Creates a [Task] with the given [name] and type, passing the given arguments to the [javax.inject.Inject]-annotated constructor,
 * and adds it to this project tasks container.
 */
inline fun <reified T : Task> TaskContainer.create(name: String, vararg arguments: Any) =
    create(name, T::class.java, *arguments)


/**
 * Locates a task by name and casts it to the expected type [T].
 *
 * If a task with the given [name] is not found, [UnknownDomainObjectException] is thrown.
 * If the task is found but cannot be cast to the expected type [T], [IllegalStateException] is thrown.
 *
 * @param name task name
 * @return task, never null
 * @throws [UnknownDomainObjectException] When the given task is not found.
 * @throws [IllegalStateException] When the given task cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> TaskContainer.getByName(name: String) =
    getByName(name).let {
        it as? T
            ?: throw IllegalStateException(
                "Task '$name' of type '${it::class.java.name}' cannot be cast to '${T::class.qualifiedName}'.")
    }


/**
 * Locates a task by name, casts it to the expected type [T] then configures it.
 *
 * If a task with the given [name] is not found, [UnknownDomainObjectException] is thrown.
 * If the task is found but cannot be cast to the expected type [T], [IllegalStateException] is thrown.
 *
 * @param name task name
 * @param configure configuration action to apply to the task before returning it
 * @return task, never null
 * @throws [UnknownDomainObjectException] When the given task is not found.
 * @throws [IllegalStateException] When the given task cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> TaskContainer.getByName(name: String, configure: T.() -> Unit) =
    getByName<T>(name).also(configure)
