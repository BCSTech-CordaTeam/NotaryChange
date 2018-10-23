# NotaryChange
An implementation of the Corda Science Project for notary change transactions.

# Running a Scenario

First, compile the project with `gradlew deployNodes`, and run the nodes with the files in `build/nodes`.


In our scenario, PartyA will begin by sending a Yo to PartyB containing an extremely important legal agreement, which we can perform on PartyA's Corda Shell with  

`start com.samples.yo.YoFlow target: PartyB, yo: "I'll give you $100,000,000", notary: OldNotary`


We can check this ran successfully on PartyB's shell with  

`run vaultQuery contractStateType: com.samples.yo.YoState`


We make note of the yoHash field here, which we will use to refer to this yo from now on.


PartyB decides that its associated entity PartyC should handle this vital agreement, and so elects to move the state to them, which we do with  

`start com.samples.yo.YoMoveFlow originalYo: 5B104743A14A6F3B5FE1D4D7B0CEA408BFC079116DE9A4CD05AED7A14257D3B9, newTarget: PartyC, notary: OldNotary`

 
Once again, we'll check that this worked by querying the vault in PartyC's shell  

`run vaultQuery contractStateType: com.samples.yo.YoState`


PartyC realises that the notarisation of this transaction should be transferred from the fed to the bank of england, and so coordinates to change the notary. We do this with  

`start com.samples.yo.YoNotaryChangeFlow originalYoHash: 5B104743A14A6F3B5FE1D4D7B0CEA408BFC079116DE9A4CD05AED7A14257D3B9, newNotary: NewNotary`

This coordinates with PartyB to agree on our new notary and then uses Corda's NotaryChangeFlow to make the actual change.


Finally, we can verify the notary change by querying the states in PartyC and PartyB again

`run vaultQuery contractStateType: com.samples.yo.YoState`


# More Details & Automation

We implement this same scenario in the [ScenarioTest.kt](src/test/kotlin/com/samples/yo/ScenarioTest.kt) file, for a bit more insight into how this would look with parts of the Corda API.
If you'd like more insight into how the actual notary change process occurs, seek out YoNotaryChangeFlow in [Yo.kt](src/main/kotlin/com/samples/yo/Yo.kt).

