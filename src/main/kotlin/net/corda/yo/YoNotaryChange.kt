package net.corda.yo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.contracts.StateAndRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.node.services.NotaryChangeHandler

/**
 * A flow to perform a NotaryChange on a list of input states, coordinating with the other parties for their approval ahead of time.
 *
 * Note that the coordination that is present in this flow is *cooperative*; that is, the NotaryChangeFlow approval process itself
 * is not changed and there is nothing in principle stopping the requesting flow from submitting a change proposal and then simply
 * submitting a different notary change later.
 *
 * @property newNotary The notary to switch all input states to.
 * @property inputStates A list of input states to change the notary of.
 */
@InitiatingFlow
@StartableByRPC
class StartChangeFlow<T : ContractState>(private val newNotary: Party, private val inputStates: List<StateAndRef<T>>) : FlowLogic<List<StateAndRef<T>>>() {

    override val progressTracker: ProgressTracker = StartChangeFlow.tracker()

    companion object {
        object CHANGING : ProgressTracker.Step("Changing the notary for an input state.")

        fun tracker() = ProgressTracker(CHANGING)
    }

    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        progressTracker.currentStep = CHANGING
        return inputStates.map { inputStateAndRef ->
            inputStateAndRef.state.data.participants.forEach { unknownParty ->
                val knownParty = serviceHub.identityService.wellKnownPartyFromAnonymous(unknownParty)
                        ?: throw FlowException("Could not get well-known party from participant in input.")
                val counterpartySession = initiateFlow(knownParty)

                counterpartySession.send(NotaryChangeRequestData(inputStateAndRef, newNotary))

                counterpartySession.receive(NotaryChangeRequestAcceptance::class.java).unwrap {
                    if (!it.accepted) {
                        throw ChangeDenied("Change request was not accepted.")
                    } else if (it.request.input != inputStateAndRef || it.request.notary != newNotary) {
                        throw FlowException("Change request data was corrupted or not sent back correctly.")
                    } else {
                        it
                    }
                }
            }
            subFlow(NotaryChangeFlow(inputStateAndRef, newNotary))
        }
    }
}


/**
 * The Initiated half of a flow to perform a NotaryChange on a list of input states. This half validates an individual change request on behalf of one participant.
 *
 * Note that the coordination that is present in this flow is *cooperative*; that is, the NotaryChangeFlow approval process itself
 * is not changed and there is nothing in principle stopping the requesting flow from submitting a change proposal and then simply
 * submitting a different notary change later.
 */
@InitiatedBy(StartChangeFlow::class)
class VerifyChangeFlow<T: ContractState>(val counterpartySession: FlowSession): FlowLogic<Void?>() {
    override val progressTracker: ProgressTracker = VerifyChangeFlow.tracker()

    companion object {
        object CHECKING : ProgressTracker.Step("Checking the proposed modifications for validity.")

        fun tracker() = ProgressTracker(CHECKING)
    }

    @Suspendable
    override fun call(): Void? {
        // We don't need to check the validity of this change from a ledger point of view because the NotaryChangeFlow will.
        progressTracker.currentStep = CHECKING
        // We don't perform unwrap checking here because validity checking is implicit in the rest of the flow.
        val proposal = counterpartySession.receive<NotaryChangeRequestData<T>>().unwrap { it }
        // Essentially the only check we do in this demo validation is to blacklist a specific corporation.
        val acceptance = if (proposal.notary.name.organisation != "EvilCorp") {
            NotaryChangeRequestAcceptance(proposal, true)
        } else {
            NotaryChangeRequestAcceptance(proposal, false)
        }
        counterpartySession.send(acceptance)
        return null
    }
}

@CordaSerializable
data class NotaryChangeRequestData<T: ContractState>(val input: StateAndRef<T>, val notary: Party)
@CordaSerializable
data class NotaryChangeRequestAcceptance<T: ContractState>(val request: NotaryChangeRequestData<T>, val accepted: Boolean)

data class ChangeDenied(override val message: String?) : FlowException(message)