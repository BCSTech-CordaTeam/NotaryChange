package com.samples.yo

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.*
import com.samples.yo.YoState.YoSchemaV1.PersistentYoState
import net.corda.core.flows.NotaryChangeFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

val OLD_NOTARY_NAME = CordaX500Name("Federal Bank", "New York", "US")
val NEW_NOTARY_NAME = CordaX500Name("Bank of England", "London", "GB")

class NotaryChangeScenario {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode
    lateinit var oldNotary: StartedMockNode
    lateinit var newNotary: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(cordappPackages = listOf("com.samples.yo"), notarySpecs = listOf(
                MockNetworkNotarySpec(
                        NEW_NOTARY_NAME,
                        validating = false
                ),
                MockNetworkNotarySpec(
                        OLD_NOTARY_NAME,
                        validating = false
                )))
        a = network.createPartyNode()
        b = network.createPartyNode()
        c = network.createPartyNode()
        oldNotary = network.notaryNodes.first { it.info.legalIdentities.first().name == OLD_NOTARY_NAME }
        newNotary = network.notaryNodes.first { it.info.legalIdentities.first().name == NEW_NOTARY_NAME }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun moveScenario() {
        // A sends an extremely important binding legal contract to B
        val flow = YoFlow(b.info.legalIdentities.first(), "I'll give you $100,000,000", notary = oldNotary.info.legalIdentities.first())
        val future = a.startFlow(flow)
        network.runNetwork()
        val stx = future.getOrThrow()
        val bTx = b.services.validatedTransactions.getTransaction(stx.id)
        assertEquals(bTx, stx)

        // B decides to move the transaction to their associated entity, C. C needs to use the BoE notary, so it switches over.
        val moveFlow = YoMoveWithNotaryChangeFlow(bTx!!.id.toString(), c.info.legalIdentities.first(), newNotary.info.legalIdentities.first())
        val moveFuture = b.startFlow(moveFlow)
        network.runNetwork()
        val mstx = moveFuture.getOrThrow()
        val cTx = c.services.validatedTransactions.getTransaction(mstx.id)
        assertEquals(cTx, mstx)
        assertEquals(cTx!!.notary, newNotary.info.legalIdentities.first())
    }
}

