package com.samples.yo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.webserver.services.WebServerPluginRegistry
import org.hibernate.Transaction
import java.util.function.Function
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// API.
@Path("yo")
class YoApi(val rpcOps: CordaRPCOps) {
    @GET
    @Path("yo")
    @Produces(MediaType.APPLICATION_JSON)
    fun yo(@QueryParam(value = "target") target: String): Response {
        val (status, message) = try {
            // Look-up the 'target'.
            val matches = rpcOps.partiesFromName(target, exactMatch = true)

            // We only want one result!
            val to: Party = when {
                matches.isEmpty() -> throw IllegalArgumentException("Target string doesn't match any nodes on the network.")
                matches.size > 1 -> throw IllegalArgumentException("Target string matches multiple nodes on the network.")
                else -> matches.single()
            }

            // Start the flow, block and wait for the response.
            val result = rpcOps.startFlowDynamic(YoFlow::class.java, to).returnValue.getOrThrow()
            // Return the response.
            Response.Status.CREATED to "You just sent a Yo! to ${to.name} (Transaction ID: ${result.tx.id})"
        } catch (e: Exception) {
            Response.Status.BAD_REQUEST to e.message
        }
        return Response.status(status).entity(message).build()
    }

    @GET
    @Path("yos")
    @Produces(MediaType.APPLICATION_JSON)
    fun yos() = rpcOps.vaultQuery(YoState::class.java).states

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun me() = mapOf("me" to rpcOps.nodeInfo().legalIdentities.first().name)

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun peers() = mapOf("peers" to rpcOps.networkMapSnapshot().map { it.legalIdentities.first().name })
}

// Flow.
@InitiatingFlow
@StartableByRPC
class YoFlow(val target: Party, val yo: String = "Yo!", val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object CREATING : ProgressTracker.Step("Creating a new Yo!")
        object SIGNING : ProgressTracker.Step("Signing the Yo!") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object VERIFYING : ProgressTracker.Step("Verifying the Yo!")
        object FINALISING : ProgressTracker.Step("Sending the Yo!") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(CREATING, SIGNING, VERIFYING, FINALISING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = CREATING

        val me = serviceHub.myInfo.legalIdentities.first()
        val notary = notary ?: serviceHub.networkMapCache.notaryIdentities.last()
        val command = Command(YoContract.Send(), listOf(me.owningKey))
        val state = YoState(me, target, yo)
        val stateAndContract = StateAndContract(state, YO_CONTRACT_ID)
        val utx = TransactionBuilder(notary = notary).withItems(stateAndContract, command)

        progressTracker.currentStep = SIGNING
        val stx = serviceHub.signInitialTransaction(utx)
        val counterpartySession = initiateFlow(target)
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(stx, setOf(counterpartySession), SIGNING.childProgressTracker()))

        progressTracker.currentStep = FINALISING
        val finalTx = subFlow(FinalityFlow(fullySignedTx, setOf(target), FINALISING.childProgressTracker()))
        finalTx.verify(serviceHub)
        return finalTx
    }
}

@InitiatingFlow
@StartableByRPC
class YoMoveFlow(val originalYo: String, val newTarget: Party, val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object FINDING : ProgressTracker.Step("Finding the original Yo!")
        object CREATING : ProgressTracker.Step("Creating the new Yo!")
        object SIGNING : ProgressTracker.Step("Signing the Yo move!") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object VERIFYING : ProgressTracker.Step("Verifying the Yo!")
        object FINALISING : ProgressTracker.Step("Sending the Yo!") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(FINDING, CREATING, SIGNING, VERIFYING, FINALISING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        val originalYo = SecureHash.parse(originalYo)

        progressTracker.currentStep = FINDING
        val expression = builder { YoState.YoSchemaV1.PersistentYoState::yoHash.equal(originalYo.toString()) }
        val customQuery = QueryCriteria.VaultCustomQueryCriteria(expression)
        val oldYo = serviceHub.vaultService.queryBy<YoState>(customQuery).states.single()

        progressTracker.currentStep = CREATING
        val me = serviceHub.myInfo.legalIdentities.first()
        val notary = notary ?: serviceHub.networkMapCache.notaryIdentities.last()
        val newYo = oldYo.state.data.copy(origin = me, target = newTarget)
        val command = Command(YoContract.Move(), listOf(me.owningKey))
        val utx = TransactionBuilder(notary = notary)
                .withItems(command, StateAndContract(newYo, YO_CONTRACT_ID))
                .addInputState(oldYo)

        progressTracker.currentStep = SIGNING
        val stx = serviceHub.signInitialTransaction(utx)
        val newCounterpartySession = initiateFlow(newTarget)
        val oldCounterpartySession = initiateFlow(oldYo.state.data.origin)
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(stx, setOf(newCounterpartySession, oldCounterpartySession), SIGNING.childProgressTracker()))

        progressTracker.currentStep = VERIFYING

        progressTracker.currentStep = FINALISING
        val finalTx = subFlow(FinalityFlow(fullySignedTx, setOf(newTarget), FINALISING.childProgressTracker()))
        finalTx.verify(serviceHub)
        return finalTx
    }
}

@InitiatingFlow
@StartableByRPC
class YoNotaryChangeFlow(val originalYoHash: String, val newNotary: Party) : FlowLogic<List<StateAndRef<YoState>>>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object FINDING : ProgressTracker.Step("Finding the original Yo!")
        object RENOTARISING : ProgressTracker.Step("Changing notary for original Yo.")
        object VERIFYING : ProgressTracker.Step("Verifying the Yo!")
        object FINALISING : ProgressTracker.Step("Sending the Yo!") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(FINDING, RENOTARISING, VERIFYING, FINALISING)
    }

    @Suspendable
    override fun call(): List<StateAndRef<YoState>> {
        val originalYo = SecureHash.parse(originalYoHash)

        progressTracker.currentStep = FINDING
        val expression = builder { YoState.YoSchemaV1.PersistentYoState::yoHash.equal(originalYo.toString()) }
        val customQuery = QueryCriteria.VaultCustomQueryCriteria(expression)
        val oldYo = serviceHub.vaultService.queryBy<YoState>(customQuery)

        progressTracker.currentStep = RENOTARISING
        return subFlow(TransactionInputNotaryChangeFlow(
                serviceHub.validatedTransactions.getTransaction(oldYo.states.first().ref.txhash)?.toLedgerTransaction(serviceHub)
                        ?: throw FlowException("Could not get transaction"),
                setOf(newNotary)
        )).map { serviceHub.toStateAndRef<YoState>(it.ref) }
    }
}

// Contract and state.
const val YO_CONTRACT_ID = "com.samples.yo.YoContract"

class YoContract: Contract {

    // Commands
    class Send : TypeOnlyCommandData()
    class Move : TypeOnlyCommandData()

    // Contract code
    override fun verify(tx: LedgerTransaction) = requireThat {
        val command = tx.commands.single()
        when (command.value) {
            is Send -> {
                "There can be no inputs when Yo'ing other parties." using (tx.inputs.isEmpty())
                "There must be one output: The Yo!" using (tx.outputs.size == 1)
                val yo = tx.outputsOfType<YoState>().single()
                "No sending Yo's to yourself!" using (yo.target != yo.origin)
                "The Yo! must be signed by the sender." using (yo.origin.owningKey in command.signers)
            }
            is Move -> {
                "There must be one input: The original Yo" using (tx.inputs.size == 1)
                "There must be one output: The moved Yo" using (tx.outputs.size == 1)
                val input = tx.inputStates.single() as YoState
                val output = tx.outputStates.single() as YoState
                "Input and output Yo's must be equal aside from origin and target" using (input.yo == output.yo)
                "Yo must actually go to a new target" using (input.target != output.target)
                "No sending Yo's to yourself!" using (output.target != output.origin)
                "The Yo! must be signed by the mover" using (input.target.owningKey in command.signers)
            }
            else -> throw IllegalArgumentException("Failed requirement: Flow must have a valid command.")
        }
    }
}

// State.
data class YoState(val origin: Party,
                   val target: Party,
                   val yo: String = "Yo!") : ContractState, QueryableState {
    override val participants get() = listOf(target, origin)
    val yoHash: SecureHash = SecureHash.sha256(yo)
    override fun toString() = "From ${origin.name} to ${target.name}: $yo"
    override fun supportedSchemas() = listOf(YoSchemaV1)
    override fun generateMappedObject(schema: MappedSchema) = YoSchemaV1.PersistentYoState(
            origin.name.toString(), target.name.toString(), yo, yoHash.toString())

    object YoSchema

    object YoSchemaV1 : MappedSchema(YoSchema.javaClass, 1, listOf(PersistentYoState::class.java)) {
        @Entity
        @Table(name = "yos")
        class PersistentYoState(
                @Column(name = "origin")
                var origin: String = "",
                @Column(name = "target")
                var target: String = "",
                @Column(name = "yo")
                var yo: String = "",
                @Column(name = "yoHash")
                var yoHash: String = ""
        ) : PersistentState()
    }
}

class YoWebPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::YoApi))
    override val staticServeDirs = mapOf("yo" to javaClass.classLoader.getResource("yoWeb").toExternalForm())
}