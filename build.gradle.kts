plugins {
    base
}

tasks {

    val samplesWrappers by registering {
        doLast {
            val wrapperFiles = wrapper.get().run {
                listOf(scriptFile, batchScript, jarFile, propertiesFile).associateBy { it.name }
            }
            file("samples").walk().filter { it.isFile && it.name in wrapperFiles.keys }.forEach { sampleWrapperFile ->
                wrapperFiles.getValue(sampleWrapperFile.name).copyTo(sampleWrapperFile, overwrite = true)
            }
        }
    }

    wrapper {
        distributionType = Wrapper.DistributionType.ALL
        finalizedBy(samplesWrappers)
    }
}
