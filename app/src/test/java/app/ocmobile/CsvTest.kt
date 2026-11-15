package app.ocmobile

import app.ocmobile.ui.components.parseCsv
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun plainRows() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), parseCsv("a,b\n1,2\n", ','))
    }

    @Test fun quotesCommasAndBreaks() {
        val rows = parseCsv("name,note\r\n\"Smith, J\",\"said \"\"hi\"\"\nthen left\"\r\n", ',')
        assertEquals(listOf("name", "note"), rows[0])
        assertEquals(listOf("Smith, J", "said \"hi\"\nthen left"), rows[1])
        assertEquals(2, rows.size)
    }

    @Test fun tabsAndEmptyCells() {
        assertEquals(listOf(listOf("x", "", "z")), parseCsv("x\t\tz", '\t'))
    }
}
