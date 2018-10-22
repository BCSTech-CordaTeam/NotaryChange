package com.samples.yo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * A flow which takes a committed ledger transaction and repoints all the inputs and outputs to a notary which all nodes mutually agree on.
 *
 * Note that the coordination that is present in this flow is *cooperative*; that is, the NotaryChangeFlow approval process itself
 * is not changed and there is nothing in principle stopping the requesting flow from requesting a notary negotiation and then simply
 * submitting a different notary change.
 *
 * @property inputTransaction A transaction for which we will change the notaries of inputs and outputs.
 * @property proposedNotaries A set of notaries which the requesting node is willing to repoint to.
 */
@InitiatingFlow
@StartableByRPC
class TransactionInputNotaryChangeFlow(private val inputTransaction: LedgerTransaction, private val proposedNotaries: Set<Party>) : FlowLogic<List<StateAndRef<ContractState>>>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object AGREEING : ProgressTracker.Step("Negotiating an agreeable shared notary.")
        object CHANGING : ProgressTracker.Step("Changing the notary for an input state.")

        fun tracker() = ProgressTracker(AGREEING, CHANGING)
    }

    @Suspendable
    override fun call(): List<StateAndRef<ContractState>> {
        progressTracker.currentStep = AGREEING
        val newNotary = notaryAgreement()

        progressTracker.currentStep = CHANGING
        val inTx = inputTransaction.inputs.mapNotNull {
            if (serviceHub.keyManagementService.filterMyKeys(it.state.data.participants.map { it.owningKey }).count() > 0) {
                subFlow(NotaryChangeFlow(it, newNotary))
            } else {
                null
            }
        }
        val outTx = inputTransaction.outRefsOfType<ContractState>().mapNotNull {
            if (serviceHub.keyManagementService.filterMyKeys(it.state.data.participants.map { it.owningKey }).count() > 0) {
                subFlow(NotaryChangeFlow(it, newNotary))
            } else {
                null
            }
        }

        return inTx + outTx
    }

    @Suspendable
    private fun notaryAgreement(): Party {
        val counterparties = sequenceOf(
                inputTransaction.inputs.flatMap { it.state.data.participants },
                inputTransaction.outputs.flatMap { it.data.participants }
        ).flatten().map {
            serviceHub.identityService.wellKnownPartyFromAnonymous(it)
                    ?: throw FlowException("Could not get well-known party from participant in input.")
        }

        return subFlow(NotaryAgreementFlow(counterparties.toSet(), proposedNotaries)).first()
    }
}

