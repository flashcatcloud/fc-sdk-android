package com.example

import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation
interface EnumInterface {
    enum class WeekDay { MON, TUE, WED, THU, FRI, SAT, SUN }

    fun getWeekDay(): WeekDay

    val weekDay: WeekDay
}
