package app.waylink.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class SearchTest {
    private val s = Sample.seed(freshState())
    private fun find(q: String) = Finder.search(s, q)
    private fun gos(q: String) = find(q).flatMap { g -> g.hits.map { it.go } }

    @Test fun `plain questions get the answer first`() {
        val svc = find("When is my next service?")
        assertEquals("Answer", svc.first().name)
        assertTrue(svc.first().hits.first().go.startsWith("svc:"))
        assertTrue(svc.any { it.name == "Service" })
        assertEquals("parking", find("where did I park").first().hits.first().go)
        assertEquals("page:fuel", find("how far can I go on this tank").first().hits.first().go)
        assertTrue(find("my last ride").first().hits.first().go.startsWith("trip-detail:"))
    }

    @Test fun `results come in groups and repeat nothing`() {
        val g = find("petrol")
        assertTrue(g.any { it.name == "Fuel" })
        assertTrue(gos("petrol").contains("fuel:new"))
        assertTrue(gos("petrol pump").contains("nearby:fuel"))
        val all = gos("service")
        assertEquals(all.size, all.toSet().size)
        assertTrue(g.all { it.hits.size <= 4 })
    }

    @Test fun `loose matching forgives plurals, typos and filler`() {
        assertTrue(gos("insurence").contains("glovebox") || gos("insurence").any { it.startsWith("doc:") })
        assertTrue(gos("services").any { it.startsWith("svc:") })
        assertTrue(gos("please show hospitals bro").contains("nearby:hospital"))
        assertTrue(gos("backups").contains("backup"))
    }

    @Test fun `nothing for nonsense or an empty box`() {
        assertTrue(find("zzqx").isEmpty())
        assertTrue(find("   ").isEmpty())
        assertTrue(Finder.SUGGESTIONS.all { (q, _) -> find(q).isNotEmpty() })
        assertTrue(Finder.TRY.all { find(it).isNotEmpty() })
    }

    @Test fun `stems fold word endings`() {
        assertEquals("service", Finder.stem("services"))
        assertEquals("battery", Finder.stem("batteries"))
    }
}
