package com.samples.yo

/*

fun main(args: Array<String>) {
    YoRPC().main(args)
}

private class YoRPC {
    companion object {
        val logger: Logger = loggerFor<YoRPC>()
    }

    fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: YoRPC <node address:port>" }
        val nodeAddress = NetworkHostAndPort.parse(args[0])
        val client = CordaRPCClient(nodeAddress)
        // Can be amended in the com.template.MainKt file.
        val proxy = client.start("user1", "test").proxy
        // Grab all signed transactions and all future signed transactions.
        val (transactions, futureTransactions) = proxy.internalVerifiedTransactionsFeed()
        val pb = proxy.partiesFromName("PartyB", false)
        val na = proxy.notaryPartyFromX500Name(CordaX500Name.parse("O=OldNotary,L=London,C=GB"))
        val nb = proxy.notaryPartyFromX500Name(CordaX500Name.parse("O=NewNotary,L=London,C=GB"))
        val evilNotary = proxy.notaryPartyFromX500Name(CordaX500Name.parse("O=EvilCorp,L=London,C=GB"))
        val yo = YoState(proxy.nodeInfo().legalIdentities.first(), pb.first())
        val future = proxy.startFlow(::YoFlow, pb.first())
        val stx = future.returnValue.getOrThrow(Duration.ofMillis(5000))
        // Check yo transaction is stored in the storage service.

        println("Yo created with notary ${stx.notary}")

        val aYoSR = proxy.vaultQueryBy<YoState>().states.first()
        assertEquals(aYoSR.state.notary, na!!)
        println("Yo sent to ${aYoSR.state.data.target} with notary ${aYoSR.state.notary}")
        val ncf = proxy.startFlow(::NotaryChangeFlow.Requester, nb!!, listOf(aYoSR)).returnValue.getOrThrow(Duration.ofMillis(5000))
        assertEquals(ncf[0].state.notary, nb!!)

        val aYoSRNew = proxy.vaultQueryBy<YoState>().states.first()
        println("Yo sent to ${aYoSRNew.state.data.target} changed notary to ${aYoSRNew.state.notary}")
        try {
            proxy.startFlow(::NotaryChangeFlow.Requester, evilNotary!!, listOf(aYoSRNew))
            throw Exception("Should not have reached this.")
        } catch (e: StateReplacementException) {
            println("Flow failed because replacement rejected, as expected")
        }
    }
}
*/