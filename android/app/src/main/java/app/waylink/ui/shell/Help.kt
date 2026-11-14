package app.waylink.ui.shell

/** Help texts behind the small (i) buttons, copied from HELP in web-v0/js/views.js. */
data class Help(val eyebrow: String, val title: String, val paras: List<String>)

val HELP: Map<String, Help> = mapOf(
    "readings" to Help("Today", "Readings", listOf("These numbers come from your bike’s cluster over Bluetooth. When the bike isn’t linked, rides you record keep the odometer and trips moving.", "Tap Trip A or Trip B to reset it. Range is the fuel left times your recent mileage, so it gets better after a few full tanks.")),
    "reminders" to Help("Logbook", "Reminders", listOf("Each job is due after a distance or a time, whichever comes first.", "The bar shows how much is left and empties as the job gets closer. Green is fine, amber means due soon, and a full red bar means overdue. Tap a job to mark it done or change how often it repeats.")),
    "mechanic" to Help("Logbook", "For the mechanic", listOf("Notes to show at your next service: a noise, a loose part, a question about price. Tick each one off once it’s sorted.")),
    "visits" to Help("Logbook", "Workshop visits", listOf("A record of each service: what was done, where, the odometer and the bill. Handy when you sell the bike or claim warranty.")),
    "mileage" to Help("Fuel", "How mileage is worked out", listOf("Fill the tank to the brim, ride, then fill to the brim again. The litres of the second fill, plus any top-ups in between, divided by the kilometres ridden, is your mileage.", "Top-ups alone can’t give a figure, so log them as top-ups and the next full tank does the maths.")),
    "cost" to Help("Fuel", "Cost", listOf("What you’ve spent on fuel and what each kilometre costs you, from the fill-ups you logged.")),
    "rehearse" to Help("Route", "Rehearse a route", listOf("Plays a sample ride on your bike’s display, turn by turn, without moving. Use it to learn how the arrows and countdown look before a real trip.")),
    "testturn" to Help("Route", "Test one turn", listOf("Sends a single arrow to the bike. Pick a turn, slide the distance and add a street name to see exactly how it shows.", "Under 100 m the arrow blinks, just as it will on the road.")),
    "display" to Help("Route", "Display", listOf("Choices for what the bike shows while you navigate.")),
    "showbike" to Help("Alerts", "Show on the bike", listOf("Choose which alerts reach your bike’s cluster. Only a name and a count are sent, never the message itself.")),
    "send" to Help("Alerts", "Send to the bike", listOf("Browsers can’t see your phone’s calls or chats, so this lets you send one by hand to try the cluster.", "Ring shows an incoming call, Missed adds a missed call, and Chat shows a WhatsApp alert with the name you typed.")),
    "handlebar" to Help("Alerts", "Handlebar button", listOf("The bike’s handlebar button reports presses to this app.", "A tap answers a call shown here; a long press declines or ends it. With no call, a tap means play or pause and a long press means next track. A browser can’t control your music app, so those are logged in Recent.")),
    "riding" to Help("Settings", "Riding", listOf("How ride mode behaves: whether it opens by itself when you start moving, whether rides record themselves, and whether the screen stays on.")),
    "papers" to Help("Settings", "Records", listOf("Your bike’s service logbook, fuel log and documents, all kept on this phone.")),
    "backup" to Help("Settings", "Data & region", listOf("Your country sets the currency, fuel price and emergency numbers. Everything else lives on this phone only. Export a backup before you change phones or clear your browser, then restore it on the new one.")),
    "preview" to Help("Developer", "Preview states", listOf("Shows the app as a brand-new rider would see it, or with extreme data: very long names, huge numbers and everything overdue. Your real data is put aside and comes back when you tap Exit.")),
    "developer" to Help("Settings", "Developer", listOf("Tools for trying the app without a bike, and for seeing the raw Bluetooth messages it sends.")),
    "medical" to Help("Profile", "Medical ID", listOf("Shown on the SOS screen so whoever helps you knows your blood group, allergies and who to call. It stays on this phone.")),
)
