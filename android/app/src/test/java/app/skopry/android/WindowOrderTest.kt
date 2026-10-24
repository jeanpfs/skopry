package app.skopry.android

import app.skopry.android.client.WindowOrder
import app.skopry.android.protocol.WindowInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WindowOrderTest {
    private fun w(id: Long, bundle: String) = WindowInfo(id, "t$id", bundle, bundle, 800, 600)
    private val windows = listOf(w(1, "safari"), w(2, "opera"), w(3, "warp"), w(4, "opera"), w(5, "mail"))

    @Test fun theFavoritesWindowsComeFirstInTheirOwnOrder() {
        assertEquals(listOf(2L, 4L, 1L, 3L, 5L), WindowOrder.favoriteFirst(windows, "opera").map { it.id })
    }

    @Test fun theRestKeepTheMacsOrder() {
        assertEquals(listOf(3L, 1L, 2L, 4L, 5L), WindowOrder.favoriteFirst(windows, "warp").map { it.id })
    }

    @Test fun noFavoriteOrAClosedOneChangesNothing() {
        assertSame(windows, WindowOrder.favoriteFirst(windows, null))
        assertSame(windows, WindowOrder.favoriteFirst(windows, ""))
        assertSame(windows, WindowOrder.favoriteFirst(windows, "textedit"))
    }
}
