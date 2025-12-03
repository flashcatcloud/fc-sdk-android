package com.example

import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation
interface OverloadedInterface {

    @Deprecated("foobar")
    fun doSomething(i: Int)

    fun doSomething(i: String)
}
