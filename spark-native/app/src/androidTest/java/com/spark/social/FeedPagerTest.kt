package com.spark.social
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class FeedPagerTest {
    @Test fun nextPageDoesNotDownloadEarlierPagesAgain()=runBlocking {
        val requests=mutableListOf<Int>()
        val pager=FeedPager {offset->requests+=offset;(offset until offset+20).map {json("id" to "$it")} }
        pager.load(true);pager.load();pager.load()
        assertEquals(listOf(0,20,40),requests);assertEquals(60,pager.posts.size)
    }
    @Test fun failedPageKeepsPostsAndRetriesSameOffset()=runBlocking {
        val requests=mutableListOf<Int>();var fail=true
        val pager=FeedPager {offset->requests+=offset;if(offset==20&&fail)throw IllegalStateException("offline");(offset until offset+20).map {json("id" to "$it")} }
        pager.load(true);pager.load();assertEquals(20,pager.posts.size);assertNotNull(pager.error)
        fail=false;pager.load();assertEquals(listOf(0,20,20),requests);assertEquals(40,pager.posts.size);assertNull(pager.error)
    }
    @Test fun refreshResetsOffsetAndKeepsSnapshotOnFailure()=runBlocking {
        val requests=mutableListOf<Int>();var fail=false
        val pager=FeedPager {offset->requests+=offset;if(fail)throw IllegalStateException("offline");(offset until offset+20).map {json("id" to "$it")} }
        pager.load(true);pager.load();fail=true;pager.load(true);assertEquals(40,pager.posts.size)
        fail=false;pager.load();pager.load();assertEquals(listOf(0,20,0,0,20),requests);assertEquals(40,pager.posts.size)
    }
}
