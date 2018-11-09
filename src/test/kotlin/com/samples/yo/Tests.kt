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
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

val NOTARY_NAME = CordaX500Name("NotaryA", "New York", "US")
val OTHER_NOTARY_NAME = CordaX500Name("NotaryB", "New York", "US")

class YoFlowTests {
    lateinit var network: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode
    lateinit var n: StartedMockNode
    lateinit var on: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(cordappPackages = listOf("com.samples.yo"), notarySpecs = listOf(
                MockNetworkNotarySpec(NOTARY_NAME, validating = false),
                MockNetworkNotarySpec(OTHER_NOTARY_NAME, validating = false)
        ))
        a = network.createPartyNode()
        b = network.createPartyNode()
        c = network.createPartyNode()
        n = network.notaryNodes.find { it.info.legalIdentities.first().name == NOTARY_NAME } ?: throw Exception("Could not find main notary.")
        on = network.notaryNodes.find { it.info.legalIdentities.first().name == OTHER_NOTARY_NAME } ?: throw Exception("Could not find other notary.")
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
            val bYo = b.services.vaultService.queryBy<YoState>().states.single().state.data
            assertEquals(bYo.toString(), yo.toString())
            print("$bYo == $yo\n")
            // Using a custom criteria directly referencing schema entity attribute.
            val expression = builder { PersistentYoState::yo.equal("Yo!") }
            val customQuery = VaultCustomQueryCriteria(expression)
            val bYo2 = b.services.vaultService.queryBy<YoState>(customQuery).states.single().state.data
            assertEquals(bYo2.yo, yo.yo)
            print("$bYo2 == $yo\n")
        }
    }

    @Test
    fun moveFlowWorksCorrectly() {
        val yo = YoState(a.info.legalIdentities.first(), b.info.legalIdentities.first())
        val flow = YoFlow(b.info.legalIdentities.first())
        val future = a.startFlow(flow)
        network.runNetwork()
        val stx = future.getOrThrow()
        // Check yo transaction is stored in the storage service.
        val bTx = b.services.validatedTransactions.getTransaction(stx.id)
        assertEquals(bTx, stx)
        b.transaction {
            // Check yo state is stored in the vault.
            // Simple query.
            val bYo = b.services.vaultService.queryBy<YoState>().states.single().state.data
            assertEquals(bYo.toString(), yo.toString())
        }
        val moveFlow = YoMoveFlow(bTx!!.id.toString(), c.info.legalIdentities.first())
        val bfuture = b.startFlow(moveFlow)
        network.runNetwork()
        val bstx = bfuture.getOrThrow()
        val cTx = c.services.validatedTransactions.getTransaction(bstx.id)
        assertEquals(cTx, bstx)
        c.transaction {
            // Check yo state is stored in the vault.
            // Simple query.
            val cYo = c.services.vaultService.queryBy<YoState>().states.single().state.data
            val newYo = yo.copy(origin = b.info.legalIdentities.first(), target = c.info.legalIdentities.first())
            assertEquals(cYo.toString(), newYo.toString())
        }
    }

    @Test
    fun moveAndChangeFlowWorksCorrectly() {
        val yo = YoState(a.info.legalIdentities.first(), b.info.legalIdentities.first())
        val flow = YoFlow(b.info.legalIdentities.first(), notary = n.info.legalIdentities.first())
        val future = a.startFlow(flow)
        network.runNetwork()
        val stx = future.getOrThrow()

        // Check yo transaction is stored in the storage service.
        val bTx = b.services.validatedTransactions.getTransaction(stx.id)
        // Check bTx is using the main notary.
        assertEquals(bTx, stx)
        assertEquals(bTx!!.notary, n.info.legalIdentities.first())
        b.transaction {
            // Check yo state is stored in the vault.
            // Simple query.
            val bYo = b.services.vaultService.queryBy<YoState>().states.single().state.data
            assertEquals(bYo.toString(), yo.toString())
        }

        val moveAndChangeFlow = YoMoveWithNotaryChangeFlow(bTx.id.toString(), c.info.legalIdentities.first(), on.info.legalIdentities.first())
        val bfuture = b.startFlow(moveAndChangeFlow)
        network.runNetwork()
        val bstx = bfuture.getOrThrow()
        val cTx = c.services.validatedTransactions.getTransaction(bstx.id)
        assertEquals(cTx, bstx)
        // Check cTx is using the new notary.
        assertEquals(cTx!!.notary, on.info.legalIdentities.first())
        c.transaction {
            // Check yo state is stored in the vault.
            // Simple query.
            val cYo = c.services.vaultService.queryBy<YoState>().states.single().state.data
            val newYo = yo.copy(origin = b.info.legalIdentities.first(), target = c.info.legalIdentities.first())
            assertEquals(cYo.toString(), newYo.toString())
        }
    }
}

class YoContractTests {
    private val ledgerServices = MockServices(listOf("com.samples.yo", "net.corda.testing.contracts"))
    private val alice = TestIdentity(CordaX500Name("Alice", "New York", "US"))
    private val bob = TestIdentity(CordaX500Name("Bob", "Tokyo", "JP"))
    private val claire = TestIdentity(CordaX500Name("Claire", "Nuku'alofa", "TO"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))

    @Test
    fun yoTransactionMustBeWellFormed() {
        // A pre-made Yo to Bob.
        val yo = YoState(alice.party, bob.party)
        // Tests.
        ledgerServices.ledger {
            // Input state present.
            transaction {
                input(DummyContract.PROGRAM_ID, DummyState())
                command(alice.publicKey, YoContract.Send())
                output(YO_CONTRACT_ID, yo)
                this.failsWith("There can be no inputs when Yo'ing other parties.")
            }
            // Wrong command.
            transaction {
                output(YO_CONTRACT_ID, yo)
                command(alice.publicKey, DummyCommandData)
                this.failsWith("")
            }
            // Command signed by wrong key.
            transaction {
                output(YO_CONTRACT_ID, yo)
                command(miniCorp.publicKey, YoContract.Send())
                this.failsWith("The Yo! must be signed by the sender.")
            }
            // Sending to yourself is not allowed.
            transaction {
                output(YO_CONTRACT_ID, YoState(alice.party, alice.party))
                command(alice.publicKey, YoContract.Send())
                this.failsWith("No sending Yo's to yourself!")
            }
            transaction {
                output(YO_CONTRACT_ID, yo)
                command(alice.publicKey, YoContract.Send())
                this.verifies()
            }
        }
    }

    @Test
    fun yoMoveTransactionMustBeWellFormed() {
        // A pre-made Yo to Bob.
        val yo = YoState(alice.party, bob.party)
        // Tests.
        ledgerServices.ledger {
            // Target should actually be different.
            transaction {
                input(YO_CONTRACT_ID, yo)
                output(YO_CONTRACT_ID, YoState(bob.party, bob.party))
                command(bob.publicKey, YoContract.Move())
                this.failsWith("Yo must actually go to a new target")
            }
            // Needs an input.
            transaction {
                output(YO_CONTRACT_ID, YoState(bob.party, claire.party))
                command(bob.publicKey, YoContract.Move())
                this.failsWith("There must be one input: The original Yo")
            }
            // Needs and output.
            transaction {
                input(YO_CONTRACT_ID, yo)
                command(bob.publicKey, YoContract.Move())
                this.failsWith("There must be one output: The moved Yo")
            }
            // Can't change the yo.
            transaction {
                input(YO_CONTRACT_ID, yo)
                output(YO_CONTRACT_ID, YoState(bob.party, claire.party, "What"))
                command(bob.publicKey, YoContract.Move())
                this.failsWith("Input and output Yo's must be equal aside from origin and target")
            }
            // Signed by the right person.
            transaction {
                input(YO_CONTRACT_ID, yo)
                output(YO_CONTRACT_ID, YoState(bob.party, claire.party))
                command(alice.publicKey, YoContract.Move())
                this.failsWith("The Yo! must be signed by the mover")
            }
            transaction {
                input(YO_CONTRACT_ID, yo)
                output(YO_CONTRACT_ID, YoState(bob.party, claire.party))
                command(bob.publicKey, YoContract.Move())
                this.verifies()
            }
        }
    }
}