/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package com.prettybyte.simplebackend

import com.prettybyte.simplebackend.lib.IEvent
import com.prettybyte.simplebackend.lib.Model
import com.prettybyte.simplebackend.lib.ModelProperties
import kotlin.test.Test

class LibraryTest {
    @Test
    fun testSomeLibraryMethod() {
        //  val classUnderTest = State<Game, IEvent, GameStates,V>("my state")

    }
}

fun printGame(model: Model<Game>?, event: IEvent) {
    if (model == null) {
        println("Game is null")
    } else {
        println(model.toString())
    }
}

class Game() : ModelProperties()

sealed class Event : IEvent

enum class GameStates