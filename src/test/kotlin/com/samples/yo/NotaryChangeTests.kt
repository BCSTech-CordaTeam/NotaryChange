package com.samples.yo
/*

import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.node.*
import com.samples.YoState.YoSchemaV1.PersistentYoState
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NotaryChangeTests {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var na: StartedMockNode
    lateinit var nb: StartedMockNode
    lateinit var evilNotary: StartedMockNode

    val NA_NAME = CordaX500Name.parse("O=OldNotary,L=London,C=GB")
    val NB_NAME = CordaX500Name.parse("O=NewNotary,L=London,C=GB")
    val EVILNOTARY_NAME = CordaX500Name.parse("O=EvilCorp,L=London,C=GB")
    val FAKENOTARY_NAME = CordaX500Name.parse("O=FakeCorp,L=London,C=GB")

    @Before
    fun setup() {
        network = MockNetwork(
                cordappPackages = listOf("net.corda.yo"),
                notarySpecs = listOf(
                        MockNetworkNotarySpec(NA_NAME, validating = true),
                        MockNetworkNotarySpec(NB_NAME, validating = true),
                        MockNetworkNotarySpec(EVILNOTARY_NAME, validating = false)
                )
        )
        a = network.createPartyNode()
        b = network.createPartyNode()
        na = network.notaryNodes.find { it.info.legalIdentities.first().name == NA_NAME }!!
        nb = network.notaryNodes.find { it.info.legalIdentities.first().name == NB_NAME }!!
        evilNotary = network.notaryNodes.find { it.info.legalIdentities.first().name == EVILNOTARY_NAME }!!
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun flowWorksCorrectly() {
        val yo = YoState(a.info.legalIdentities.first(), b.info.legalIdentities.first())
        val flow = YoFlow(b.info.legalIdentities.first())
        val future = a.startFlow(flow)
        network.runNetwork()
        val stx = future.getOrThrow()
        // Check yo transaction is stored in the storage service.
        val bTx = b.services.validatedTransactions.getTransaction(stx.id)
        assertEquals(bTx, stx)
        print("bTx == $stx\n")
        // Check yo state is stored in the vault.
        b.transaction {
            // Simple query.
            val bYoSR = b.services.vaultService.queryBy<YoState>().states.single()
            val bYo = bYoSR.state.data
            assertEquals(bYo.toString(), yo.toString())
            print("$bYo == $yo\n")
            // Using a custom criteria directly referencing schema entity attribute.
            val expression = builder { PersistentYoState::yo.equal("Yo!") }
            val customQuery = VaultCustomQueryCriteria(expression)
            val bYo2 = b.services.vaultService.queryBy<YoState>(customQuery).states.single().state.data
            assertEquals(bYo2.yo, yo.yo)
            print("$bYo2 == $yo\n")
        }

        val bYoSR = b.services.vaultService.queryBy<YoState>().states.single()
        val ncf = b.startFlow(StartChangeFlow(nb.info.legalIdentities.first(), listOf(bYoSR)))
        network.runNetwork()
        assertEquals(ncf.getOrThrow()[0].state.notary, nb.info.legalIdentities.first())

        val bYoSRNew = b.services.vaultService.queryBy<YoState>().states.single()
        try {
            val f = b.startFlow(StartChangeFlow(evilNotary.info.legalIdentities.first(), listOf(bYoSRNew)))
            network.runNetwork()
            f.getOrThrow()
            fail("Should not have reached this point.")
        } catch (e: ChangeDenied) {
            println("Flow failed because replacement rejected")
        }

        val bYoSRNewer = b.services.vaultService.queryBy<YoState>().states.single()
        try {
            val fakeNotary = getTestPartyAndCertificate(FAKENOTARY_NAME, generateKeyPair().public)
            val f = b.startFlow(StartChangeFlow(fakeNotary.party, listOf(bYoSRNewer)))
            network.runNetwork()
            f.getOrThrow()
            fail("Should not have reached this point.")
        } catch (e: StateReplacementException) {
            println("Flow failed because replacement rejected")
        }
    }
}
*/
