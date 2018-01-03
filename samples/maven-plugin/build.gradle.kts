plugins {
    java
    maven
}

group = "org.gradle.kotlin-dsl"

version = "1.0"

tasks {

    val sourcesJar by creating(Jar::class) {
        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        classifier = "sources"
        from(java.sourceSets["main"].allSource)
    }

    val javadocJar by creating(Jar::class) {
        dependsOn(JavaPlugin.JAVADOC_TASK_NAME)
        classifier = "javadoc"
        from(java.docsDir)
    }

    artifacts {
        add("archives", sourcesJar)
        add("archives", javadocJar)
    }

    "uploadArchives"(Upload::class) {

        repositories {

            withConvention(MavenRepositoryHandlerConvention::class) {

                mavenDeployer {

                    withGroovyBuilder {
                        "repository"("url" to uri("$buildDir/m2/releases"))
                        "snapshotRepository"("url" to uri("$buildDir/m2/snapshots"))
                    }

                    pom.project {
                        withGroovyBuilder {
                            "parent" {
                                "groupId"("org.gradle")
                                "artifactId"("kotlin-dsl")
                                "version"("1.0")
                            }
                            "licenses" {
                                "license" {
                                    "name"("The Apache Software License, Version 2.0")
                                    "url"("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                    "distribution"("repo")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
