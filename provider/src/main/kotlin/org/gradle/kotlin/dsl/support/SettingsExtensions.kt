/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.kotlin.dsl.support

import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal

import org.gradle.internal.Cast.uncheckedCast

import java.lang.reflect.InvocationTargetException

import kotlin.reflect.KProperty

inline
fun <reified T : Any> Settings.serviceOf(): T =
    (gradle as GradleInternal).services[T::class.java]!!

operator fun <T : String?> Settings.getValue(any: Any?, property: KProperty<*>): T {
    if (property.returnType.isMarkedNullable) {
        val exists = javaClass.getMethod("hasProperty", String::class.java).invoke(this, property.name) as Boolean
        if (!exists)
            return uncheckedCast(null)
    }
    val value = try {
        javaClass.getMethod("getProperty", String::class.java).invoke(this, property.name)
    } catch (e: InvocationTargetException) {
        throw e.cause!!
    }
    return uncheckedCast(value as String)
}
