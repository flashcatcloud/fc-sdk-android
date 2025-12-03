package com.example

import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation
interface ExperimentalInterface {
    @ExperimentalApi
    fun doSomethingExperimental()
}
