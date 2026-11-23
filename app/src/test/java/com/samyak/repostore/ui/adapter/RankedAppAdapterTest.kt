package com.samyak.repostore.ui.adapter

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.R
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.GitHubRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RankedAppAdapterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_RepoStore
        )
    }

    private fun createDummyAppItem(id: Long, name: String, stars: Int = 10): AppItem {
        val repo = GitHubRepo(
            id = id,
            name = name,
            fullName = "owner/$name",
            description = "Test Description",
            htmlUrl = "https://github.com/owner/$name",
            stars = stars,
            forks = 0,
            language = "Kotlin",
            updatedAt = "2026-01-01T00:00:00Z",
            createdAt = "2025-01-01T00:00:00Z",
            archived = false,
            owner = GitHubRepo.Owner(
                login = "owner",
                avatarUrl = "https://example.com/avatar.png",
                htmlUrl = "https://github.com/owner"
            ),
            topics = emptyList(),
            defaultBranch = "main"
        )
        return AppItem(repo = repo, latestRelease = null, tag = null)
    }

    @Test
    fun `onCurrentListChanged notifies item range changed with PAYLOAD_RANK`() {
        val adapter = RankedAppAdapter { }
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        adapter.onAttachedToRecyclerView(recyclerView)

        val notifiedPayloads = mutableListOf<Any?>()
        var notifiedPosition = -1
        var notifiedCount = -1

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                notifiedPosition = positionStart
                notifiedCount = itemCount
                notifiedPayloads.add(payload)
            }
        })

        val app1 = createDummyAppItem(1L, "App1")
        val app2 = createDummyAppItem(2L, "App2")

        val latch1 = CountDownLatch(1)
        adapter.submitList(listOf(app1, app2)) {
            latch1.countDown()
        }
        shadowOf(Looper.getMainLooper()).idle()
        latch1.await(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        // Re-ordering: app2 first, app1 second
        val latch2 = CountDownLatch(1)
        adapter.submitList(listOf(app2, app1)) {
            latch2.countDown()
        }
        shadowOf(Looper.getMainLooper()).idle()
        latch2.await(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, notifiedPosition)
        assertEquals(2, notifiedCount)
        assertTrue(notifiedPayloads.contains(RankedAppAdapter.PAYLOAD_RANK))
    }

    @Test
    fun `onBindViewHolder with PAYLOAD_RANK updates rank view directly`() {
        val adapter = RankedAppAdapter { }
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val viewHolder = adapter.onCreateViewHolder(recyclerView, 0)

        val app1 = createDummyAppItem(1L, "App1")
        val latch = CountDownLatch(1)
        adapter.submitList(listOf(app1)) {
            latch.countDown()
        }
        shadowOf(Looper.getMainLooper()).idle()
        latch.await(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        // Initial bind at rank 1
        adapter.onBindViewHolder(viewHolder, 0)
        assertEquals("1", viewHolder.binding.tvRank.text.toString())

        // Partial bind with PAYLOAD_RANK to rank 5
        adapter.onBindViewHolder(viewHolder, 4, mutableListOf(RankedAppAdapter.PAYLOAD_RANK))
        assertEquals("5", viewHolder.binding.tvRank.text.toString())
    }
}
