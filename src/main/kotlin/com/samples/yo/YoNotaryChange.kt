package com.samples.yo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * A flow which takes a partially-complete TransactionBuilder transaction and repoints all the inputs to a notary which all nodes mutually agree on.
 *
 * Note that the coordination that is present in this flow is *cooperative*; that is, the NotaryChangeFlow approval process itself
 * is not changed and there is nothing in principle stopping the requesting flow from requesting a notary negotiation and then simply
 * submitting a different notary change.
 *
 * @property inputTransaction An unsigned transaction which we wish to repoint the inputs for.
 * @property proposedNotaries A set of notaries which the requesting node is willing to repoint to.
 */
@InitiatingFlow
@StartableByRPC
class TransactionInputNotaryChangeFlow<T : ContractState>(private val inputTransaction: TransactionBuilder, private val proposedNotaries: Set<Party>) : FlowLogic<Void?>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object AGREEING : ProgressTracker.Step("Negotiating an agreeable shared notary.")
        object CHANGING : ProgressTracker.Step("Changing the notary for an input state.")

        fun tracker() = ProgressTracker(AGREEING, CHANGING)
    }

    @Suspendable
    override fun call(): Void? {
        progressTracker.currentStep = AGREEING
        val newNotary = notaryAgreement()

        progressTracker.currentStep = CHANGING
        inputTransaction.inputStates().map { inputStateRef ->
            val inputStateAndRef = serviceHub.toStateAndRef<T>(inputStateRef)
            subFlow(NotaryChangeFlow(inputStateAndRef, newNotary))
        }

        return null
    }

    private fun notaryAgreement(): Party {
        val counterparties = sequenceOf(
                inputTransaction.inputStates().flatMap { serviceHub.toStateAndRef<T>(it).state.data.participants },
                inputTransaction.outputStates().flatMap { it.data.participants }
        ).flatten().map {
            serviceHub.identityService.wellKnownPartyFromAnonymous(it)
                    ?: throw FlowException("Could not get well-known party from participant in input.")
        }.toList()

        return subFlow(NotaryAgreementFlow(counterparties, proposedNotaries)).first()
    }
}

