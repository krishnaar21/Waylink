package app.waylink.ui.shell

/** Help texts behind the small (i) buttons, copied from HELP in web-v0/js/views.js. */
data class Help(val eyebrow: String, val title: String, val paras: List<String>)

val HELP: Map<String, Help> = mapOf(
    "readings" to Help("Today", "Readings", listOf("These numbers come from your bike’s cluster over Bluetooth. When the bike isn’t linked, rides you record keep the odometer and trips moving.", "Tap Trip A or Trip B to reset it. Range is the fuel left times your recent mileage, so it gets better after a few full tanks.")),
    "reminders" to Help("Service", "Reminders", listOf("Each job is due after a distance or a time, whichever comes first.", "The bar shows how much is left and empties as the job gets closer. Green is fine, amber means due soon, and a full red bar means overdue. Tap a job to mark it done or change how often it repeats.")),
    "mechanic" to Help("Service", "For the mechanic", listOf("Notes to show at your next service: a noise, a loose part, a question about price. Tick each one off once it’s sorted.")),
    "visits" to Help("Service", "Workshop visits", listOf("A record of each service: what was done, where, the odometer and the bill. Handy when you sell the bike or claim warranty.")),
    "mileage" to Help("Fuel", "How mileage is worked out", listOf("Fill the tank to the brim, ride, then fill to the brim again. The litres of the second fill, plus any top-ups in between, divided by the kilometres ridden, is your mileage.", "Top-ups alone can’t give a figure, so log them as top-ups and the next full tank does the maths.")),
    "brim" to Help("Fuel", "Filling to the brim", listOf("Ask for the tank to be filled until the fuel reaches the bottom of the filler neck, not just until the nozzle clicks off. That top line is the same every time, so the next full tank tells exactly how much you used.", "Stopping at the first click leaves a different gap each time, and mileage drifts. Top-ups are fine: log them with this switch off.")),
    "cost" to Help("Fuel", "Cost", listOf("What you’ve spent on fuel and what each kilometre costs you, from the fill-ups you logged.")),
    "rehearse" to Help("Route", "Rehearse a route", listOf("Plays a sample ride on your bike’s display, turn by turn, without moving. Use it to learn how the arrows and countdown look before a real trip.")),
    "testturn" to Help("Route", "Test one turn", listOf("Sends a single arrow to the bike. Pick a turn, slide the distance and add a street name to see exactly how it shows.", "Under 100 m the arrow blinks, just as it will on the road.")),
    "display" to Help("Route", "Display", listOf("Choices for what the bike shows while you navigate.")),
    "showbike" to Help("Alerts", "Show on the bike", listOf("Choose which calls and chats reach your bike’s display. Waylink reads them from the phone’s notifications, so they arrive on their own.", "Only names and counts reach your bike. Message text never leaves this phone.")),
    "send" to Help("Alerts", "Test the display", listOf("Real calls and chats reach the bike on their own. Use these buttons to see how the display shows them, with any name you like.", "Ring shows a ringing call, Missed shows a missed call and Chat shows a WhatsApp alert. Tests aren’t saved to your counts or history.")),
    "handlebar" to Help("Alerts", "Handlebar button", listOf("The M button on the left switch (on some bikes a phone icon) tells this app when it’s pressed, but only while a call rings or is on.", "A press under 1 second answers; holding it over 3 seconds declines or ends the call. Waylink then presses Answer or Decline on the phone’s own call screen. With no call, M only changes the display’s screens.")),
    "riding" to Help("Settings", "Riding", listOf("How ride mode behaves: whether it opens by itself when you start moving, whether rides record themselves, and whether the screen stays on.")),
    "papers" to Help("Settings", "Records", listOf("Your bike’s service record, fuel log and papers, all kept on this phone.")),
    "backup" to Help("Settings", "Data & region", listOf("Your country sets the currency, fuel price and emergency numbers. Everything else lives on this phone, unless you link a computer, which gets a locked copy without your private details. Export a backup before you change phones or reinstall the app, then restore it on the new one.")),
    "preview" to Help("Developer", "Preview states", listOf("Shows the app as a brand-new rider would see it, or with extreme data: very long names, huge numbers and everything overdue. Your real data is put aside and comes back when you tap Exit.")),
    "developer" to Help("Settings", "Developer", listOf("Tools for trying the app without a bike, and for seeing the raw Bluetooth messages it sends.")),
    "medical" to Help("Profile", "Medical ID", listOf("Shown on the SOS screen so whoever helps you knows your blood group, allergies and who to call. It stays on this phone.")),
)
