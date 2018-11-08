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
package my

/**
 * If you don't declare your Task, Project, Extensions, ... as an "open class",
 * Gradle 5.0 will fails at runtime with the error
 *   `Cannot create a proxy class for final class at runtime.`
 *
 * This will be tentatively fixed in Gradle 5.1 by applying automatically the "allopen" compiler plugin
 *
 * See https://github.com/gradle/kotlin-dsl/issues/390
 *
 * In the meantime you can work-around this problem by importing this file,
 * applying the Gradle configuration above, and extending `my.DefaultTask` and `my.Plugin`
 * and annotating your extension with @my.AllOpen
 *
 * ```
   // build.gradle.kts
   plugins {
        id("org.jetbrains.kotlin.plugin.allopen") version "1.3.0"
    }

    allOpen {
    annotation("my.AllOpen")
    }
 * ```
 ***/
annotation class AllOpen

@AllOpen
abstract class DefaultTask : org.gradle.api.DefaultTask()

@AllOpen
interface Plugin<T>: org.gradle.api.Plugin<T>
