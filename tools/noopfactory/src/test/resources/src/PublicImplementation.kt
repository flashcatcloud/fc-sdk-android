package com.example

import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation(publicNoOpImplementation = true)
interface PublicImplementation {
    fun doSomething()
}
