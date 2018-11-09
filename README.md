# NotaryChange
An implementation of the Corda Science Project for notary change transactions.

# Running a Scenario

First, compile the project with `gradlew deployNodes`, and run the nodes with the files in `build/nodes`.


In our scenario, PartyA will begin by sending a Yo to PartyB containing an extremely important legal agreement, which we can perform on PartyA's Corda Shell with  

`start com.samples.yo.YoFlow target: PartyB, yo: "I'll give you $100,000,000", notary: Federal Reserve`

We note that, for regulatory reasons or otherwise, PartyA elects to use a notarisation service from the Federal Reserve for this transaction.

We can check this ran successfully on PartyB's shell with  

`run vaultQuery contractStateType: com.samples.yo.YoState`

We make note of the txHash field here, which we will use to refer to this yo from now on.


PartyB decides that its associated entity PartyC should handle this vital agreement, and so elects to move the state to them.

PartyC operates in a regulatory environment where this transaction must be notarised by a notarisation service from the Bank of England, and the MoveFlow requires states from the original Yo, so we must first move those states to this notary before we can proceed.

As part of the negotiation procedure of this CordApp, it was decided that notary changes should be explicit rather than implicit to avoid errors accidentally causing a switch of notary and bringing regulatory troubles, so we'll use the version of YoMove that explicitly changes the previous inputs' notaries

On PartyB, we run:

`start com.samples.yo.YoMoveWithNotaryChangeFlow originalYo: <txHash>, newTarget: PartyC, newNotary: Bank of England`
 
Once again, we'll check that this worked by querying the vault in PartyC's shell.

`run vaultQuery contractStateType: com.samples.yo.YoState`

And we can verify that the notary change has also worked on PartyB's side, and that the previous YoState is no longer active, with the same.

`run vaultQuery contractStateType: com.samples.yo.YoState`

# Yo Changes

The Cordapp we're using is based on the Yo! Cordapp. For those familiar with that cordapp, it might be helpful to note the following changes:

 - States & Flow updated so that both parties are signing participants of the YoStates.
 - Addition of a move command.
 - Addition of a move with notary change command.

# More Details & Automation

We implement this same scenario in the [ScenarioTest.kt](src/test/kotlin/com/samples/yo/ScenarioTest.kt) file, for a bit more insight into how this would look with parts of the Corda API.
If you'd like more insight into how the actual notary change process occurs, seek out YoMoveWithNotaryChange in [Yo.kt](src/main/kotlin/com/samples/yo/Yo.kt).

