package com.spark.social

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Offsets advance only after success. Refresh replaces the snapshot; retry never skips a page. */
class FeedPager(private val fetch:suspend(Int)->List<JSONObject>) {
    var posts by mutableStateOf(emptyList<JSONObject>());private set
    var loading by mutableStateOf(false);private set
    var more by mutableStateOf(true);private set
    var error by mutableStateOf<String?>(null);private set
    private var offset=0
    private var failedRefresh=false
    private val lock=Mutex()
    suspend fun load(refresh:Boolean=false) {
        val reset=refresh||(error!=null&&failedRefresh)
        if(!reset&&(loading||!more))return
        lock.withLock {
            loading=true;error=null
            try {
                val start=if(reset)0 else offset
                val next=fetch(start)
                posts=if(reset)next.distinctBy {it.id()} else (posts+next).distinctBy {it.id()}
                offset=start+next.size;more=next.size==20;failedRefresh=false
            }catch(e:CancellationException) {throw e}
            catch(e:Exception) {failedRefresh=reset;error=e.message?:"Couldn't load posts. Please retry."}
            finally {loading=false}
        }
    }
}
