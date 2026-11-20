package com.streamline.app.data.magnet

import com.streamline.app.domain.model.MagnetPage
import com.streamline.app.domain.model.MagnetSource
import com.streamline.app.domain.model.MagnetTitle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMagnetCatalogRepositoryTest {

    private class FakeProvider(private val page: MagnetPage) : MagnetSourceProvider {
        override suspend fun browse(query: String, page: Int): MagnetPage = this.page
    }

    private fun title(
        id: String,
        source: MagnetSource,
        hash: String,
        seeds: Int,
        year: Int? = null,
        titleText: String = id,
    ) = MagnetTitle(
        id = id,
        source = source,
        title = titleText,
        magnetUrl = "magnet:?xt=urn:btih:$hash",
        seeds = seeds,
        year = year,
    )

    @Test
    fun `all sources merges, de-duplicates by info hash and sorts by seeds`() = runBlocking {
        val shared = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val first = MagnetPage(
            items = listOf(
                title("yts:1", MagnetSource.YTS, shared, 10),
                title("yts:2", MagnetSource.YTS, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", 40),
            ),
            page = 1,
            hasMore = false,
            totalResults = 2,
        )
        val second = MagnetPage(
            items = listOf(
                title("tpb:1", MagnetSource.TPB, shared, 100),
                title("tpb:2", MagnetSource.TPB, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", 20),
            ),
            page = 1,
            hasMore = false,
            totalResults = 2,
        )
        val repository = DefaultMagnetCatalogRepository(
            providers = mapOf(
                MagnetSource.YTS to FakeProvider(first),
                MagnetSource.TPB to FakeProvider(second),
            ),
        )

        val page = repository.browse(MagnetSource.ALL, "avengers", 1)

        assertEquals(3, page.items.size)
        assertEquals("yts:2", page.items[0].id)
        assertEquals("tpb:2", page.items[1].id)
        assertTrue(page.items[2].id == "yts:1")
        assertEquals(3, page.totalResults)
    }

    @Test
    fun `all with no query includes every year and skips query-only providers`() = runBlocking {
        val yts = FakeProvider(
            MagnetPage(
                items = listOf(
                    title("yts:old", MagnetSource.YTS, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 10, 2019),
                    title("yts:new", MagnetSource.YTS, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", 20, 2025),
                ),
                page = 1,
                hasMore = false,
            ),
        )
        var btdigCalled = false
        val btdig = object : MagnetSourceProvider {
            override suspend fun browse(query: String, page: Int): MagnetPage {
                btdigCalled = true
                return MagnetPage(emptyList(), page, false)
            }
        }
        val repository = DefaultMagnetCatalogRepository(
            providers = mapOf(
                MagnetSource.YTS to yts,
                MagnetSource.BTDIGG to btdig,
            ),
        )

        val page = repository.browse(MagnetSource.ALL, "", 1)

        assertEquals(2, page.items.size)
        assertEquals(listOf(2025, 2019), page.items.map { it.year })
        assertTrue(!btdigCalled)
    }

    @Test
    fun `all latest only queries paging providers after page one`() = runBlocking {
        var tpbCalls = 0
        val yts = object : MagnetSourceProvider {
            override suspend fun browse(query: String, page: Int): MagnetPage = MagnetPage(
                items = listOf(
                    title(
                        "yts:$page",
                        MagnetSource.YTS,
                        page.toString().padEnd(40, 'A'),
                        seeds = 10,
                    ),
                ),
                page = page,
                hasMore = page == 1,
            )
        }
        val tpb = object : MagnetSourceProvider {
            override suspend fun browse(query: String, page: Int): MagnetPage {
                tpbCalls++
                return MagnetPage(
                    items = listOf(
                        title("tpb:$page", MagnetSource.TPB, page.toString().padEnd(40, 'B'), 20),
                    ),
                    page = page,
                    hasMore = true,
                )
            }
        }
        val repository = DefaultMagnetCatalogRepository(
            providers = mapOf(
                MagnetSource.YTS to yts,
                MagnetSource.TPB to tpb,
            ),
        )

        val first = repository.browse(MagnetSource.ALL, "", 1)
        val second = repository.browse(MagnetSource.ALL, "", 2)

        assertEquals(2, first.items.size)
        assertEquals("yts:2", second.items.single().id)
        assertEquals(1, tpbCalls)
    }

    @Test
    fun `av1 releases are filtered from results`() = runBlocking {
        val provider = FakeProvider(
            MagnetPage(
                items = listOf(
                    title("av1", MagnetSource.YTS, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 100, titleText = "Movie 1080p AV1"),
                    title("h264", MagnetSource.YTS, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", 10, titleText = "Movie 1080p x264"),
                ),
                page = 1,
                hasMore = false,
            ),
        )
        val repository = DefaultMagnetCatalogRepository(
            providers = mapOf(MagnetSource.YTS to provider),
        )

        val page = repository.browse(MagnetSource.YTS, "", 1)

        assertEquals(listOf("h264"), page.items.map { it.id })
    }

    @Test
    fun `identical provider pages are served from cache`() = runBlocking {
        var calls = 0
        val provider = object : MagnetSourceProvider {
            override suspend fun browse(query: String, page: Int): MagnetPage {
                calls++
                return MagnetPage(
                    items = listOf(
                        title("yts:1", MagnetSource.YTS, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 5),
                    ),
                    page = page,
                    hasMore = false,
                )
            }
        }
        val repository = DefaultMagnetCatalogRepository(
            providers = mapOf(MagnetSource.YTS to provider),
        )

        repository.browse(MagnetSource.YTS, "dune", 1)
        repository.browse(MagnetSource.YTS, "dune", 1)

        assertEquals(1, calls)
    }

    @Test
    fun `all sources keeps working when one provider fails`() = runBlocking {
        val working = FakeProvider(
            MagnetPage(
                items = listOf(
                    title("yts:1", MagnetSource.YTS, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 5),
                ),
                page = 1,
                hasMore = false,
            ),
        )
        val failing = object : MagnetSourceProvider {
            override suspend fun browse(query: String, page: Int): MagnetPage {
                error("offline")
            }
        }
        val repository = DefaultMagnetCatalogRepository(
            providers = mapOf(
                MagnetSource.YTS to working,
                MagnetSource.TPB to failing,
            ),
        )

        val page = repository.browse(MagnetSource.ALL, "dune", 1)

        assertEquals(1, page.items.size)
        assertEquals("yts:1", page.items.single().id)
        assertTrue(page.warnings.any { it.contains("Pirate Bay") })
    }
}
