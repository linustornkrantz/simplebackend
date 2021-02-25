package com.prettybyte.simplebackend.lib.statemachine;

import arrow.core.Either
import arrow.core.Right
import com.prettybyte.simplebackend.lib.*
import kotlin.reflect.KClass

const val initial = "initial"

class StateMachine<T : ModelProperties, E : IEvent, ModelStates : Enum<*>>(val thisType: KClass<T>) {

    internal lateinit var modelView: IModelView<T>
    val onStateChangeListeners: MutableList<in suspend (Model<T>) -> Unit> = mutableListOf<suspend (Model<T>) -> Unit>()
    private lateinit var currentState: State<T, E, ModelStates>
    private val states = mutableListOf<State<T, E, ModelStates>>()
    private lateinit var initialState: State<T, E, ModelStates>

    internal fun setView(view: IModelView<*>) {
        modelView = view as IModelView<T>
    }

    fun initialState(init: State<T, E, ModelStates>.() -> Unit) {
        val state = State<T, E, ModelStates>(initial)
        state.init()
        initialState = state
    }

    fun state(modelState: ModelStates, init: State<T, E, ModelStates>.() -> Unit) {
        val state = State<T, E, ModelStates>(modelState.name)
        state.init()
        states.add(state)
        // TODO: verify that all states have unique names
    }

    private fun getStateByName(name: String): State<T, E, ModelStates> {
        val result = states.firstOrNull { it.name == name }
            ?: throw NoSuchElementException(name)
        return result
    }

    internal fun eventOccurred(event: E, isDryRun: Boolean, performActions: Boolean, view: IModelView<*>): Either<Problem, Model<T>?> {
        view as IModelView<T>
        // assert(event.model != null || event.name == initialState)

        val modelId = event.modelId
        val model: Model<T>? = if (modelId == null) null else view.get(modelId)

        currentState = if (model == null) initialState else getStateByName(model.state)
        val transition = currentState.getTransitionForEvent(event) ?: throw RuntimeException("eventOccurred")
        transition.currentState = currentState

        val newState = transition.enterTransition(isDryRun) { getStateByName(it.name) }

        currentState.exitState(performActions, model, event)
        var updatedModel = transition.executeEffect(model, event.getParams(), newState, event, view, isDryRun)

        newState.enterState(!isDryRun && performActions, model, event)

        // Is there any transition that triggers automatically?
        val autoTransition = newState.transitions.filter { it.triggeredIf != null && it.triggeredIf.invoke(updatedModel.properties) }.firstOrNull()
        if (autoTransition != null) {   // TODO: must allow any number of automatic transitions
            autoTransition.currentState = newState
            val newerState = autoTransition.enterTransition(isDryRun) { getStateByName(it.name) }
            newState.exitState(performActions, model, event)
            updatedModel = autoTransition.executeEffect(updatedModel, event.getParams(), newerState, event, view, isDryRun)
            newerState.enterState(!isDryRun && performActions, model, event)

            // TODO:   Tror det är bäst att State och Transition endast ska hålla data och flytta all logik till denna klassen.Svårt att få översikt annars.


        }

        return Right(updatedModel)
    }

    internal fun canHandle(event: IEvent): Boolean {
        return getAllStates().any { it.getTransitionForEvent(event) != null }
    }

    internal fun transitionExists(event: IEvent, view: IModelView<out ModelProperties>): Boolean {
        view as IModelView<T>
        val modelId = event.modelId
        val model: Model<T>? = if (modelId == null) null else view.get(modelId)
        currentState = if (model == null) initialState else getStateByName(model.state)
        val transition = currentState.getTransitionForEvent(event)
        return if (transition == null) false else true
    }

    internal fun preventedByGuards(event: E, userIdentity: UserIdentity, view: IModelView<*>): List<BlockedByGuard> {
        view as IModelView<T>
        val modelId = event.modelId
        val model: Model<T>? =
            if (modelId == null) null else view.get(modelId)
        currentState = if (model == null) initialState else getStateByName(model.state)
        val transition = currentState.getTransitionForEvent(event) ?: throw RuntimeException("eventOccurred")
        return transition.verifyGuard(event, model, userIdentity)
    }

    private fun getAllStates(): Set<State<T, E, ModelStates>> {
        val allStates = HashSet<State<T, E, ModelStates>>()
        allStates.addAll(states)
        allStates.add(initialState)
        return allStates
    }

    internal fun handlesType(type: KClass<Model<T>>): Boolean {
        return type == thisType
    }

    fun onStateChange(f: suspend (Model<T>) -> Unit) {
        onStateChangeListeners.add(f)
    }

}

inline fun <reified T : ModelProperties, E : IEvent, ModelStates : Enum<*>> stateMachine(init: StateMachine<T, E, ModelStates>.() -> Unit): StateMachine<T, E, ModelStates> {
    // TODO: validate that all states are reachable?
    val stateMachine = StateMachine<T, E, ModelStates>(T::class)
    stateMachine.init()
    // TODO: make sure all states are declared (stateMachine.states == ModelStates)
    return stateMachine
}

/*

A state can have many transitions in response to the same trigger, as long as they have nonoverlapping guards; however, this situation could create problems in the sequence of evaluation of the guards when the common trigger occurs. The UML specification[1] intentionally does not stipulate any particular order; rather, UML puts the burden on the designer to devise guards in such a way that the order of their evaluation does not matter. Practically, this means that guard expressions should have no side effects, at least none that would alter evaluation of other guards having the same trigger.
    -> guards får inte ändra på något, bara läsa

ska kunna berätta för klienten vilka triggers (events) som nu är möjliga (detta beror bara på nuvarande state, inte på guards (?). Klienten kan då t.ex. avgöra om "Delete"-knappen ska visas.
klienten ska kunna torrköra event och guards för att t.ex. validera ett formulär


Senare:
State som funktion av relation. T.ex. Ping state räknas ut genom att gå igenom dess PingMembers. Så om en PingMember ändras så ska Pings state räknas om.
State som funktion av tid.


State:
- har transitions
- har entry och exit actions


 */
