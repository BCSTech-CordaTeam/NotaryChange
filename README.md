# NotaryChange
An implementation of the Corda Science Project for notary change transactions

In PartyA
`start com.samples.yo.YoFlow target: "O=PartyB,L=New York,C=US", yo: "I will give you $100,000,000", notary: "O=OldNotary,L=London,C=GB"`

 + In PartyB: `run vaultQuery contractStateType: com.samples.yo.YoState`
 + Get YoHash, then `start com.samples.yo.YoMoveFlow originalYo: C3841C4245AF16F537B37773F2CE113EE75A2856CCA974F30B8986ADFB841B39, newTarget: "O=PartyC,L=New York,C=US", notary: "O=OldNotary,L=London,C=GB"`
 + 