package com.samples.yo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey

/**
 * The initiating half of a multiparty flow to negotiate a set of mutually agreeable notaries.
 *
 * @property counterparties The list of parties to negotiate with.
 * @property proposedNotaries A list of notaries the requester is willing to use with these counterparties.
 */
@InitiatingFlow
@StartableByRPC
class NotaryAgreementFlow(private val counterparties: Set<Party>, private val proposedNotaries: Set<Party>) : FlowLogic<Set<Party>>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object PROPOSING : ProgressTracker.Step("Requesting notary approval from counterparties")

        fun tracker() = ProgressTracker(PROPOSING)
    }

    @Suspendable
    override fun call(): Set<Party> {
        progressTracker.currentStep = PROPOSING
        var potentialNotaries = proposedNotaries
        for (it in counterparties) {
            if (serviceHub.myInfo.legalIdentities.contains(it)) {
                continue
            }
            val counterpartySession = initiateFlow(it)
            val approvedNotaries = counterpartySession.sendAndReceive<Set<Party>>(proposedNotaries).unwrap { it }
            potentialNotaries = potentialNotaries.intersect(approvedNotaries)
            if (potentialNotaries.isEmpty()) {
                return potentialNotaries
            }
        }
        return potentialNotaries
    }
}


/**
 * The responding half of a multiparty flow to negotiate a set of mutually agreeable notaries.
 */
@InitiatedBy(NotaryAgreementFlow::class)
class NotaryAgreementResponse(val counterpartySession: FlowSession): FlowLogic<Set<Party>>() {
    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object RESPONDING : ProgressTracker.Step("Responding to notary agreement request")

        fun tracker() = ProgressTracker(RESPONDING)
    }

    @Suspendable
    override fun call(): Set<Party> {
        progressTracker.currentStep = RESPONDING
        val proposal = counterpartySession.receive<Set<Party>>().unwrap { it }
        val response = proposal.intersect(serviceHub.networkMapCache.notaryIdentities)
        counterpartySession.send(response)
        return response
    }
}
