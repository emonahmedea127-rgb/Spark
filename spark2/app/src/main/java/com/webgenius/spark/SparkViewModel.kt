package com.webgenius.spark

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webgenius.spark.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class Tab { FEED, DISCOVER, CREATE, REQUESTS, PROFILE }
data class AppState(
    val busy: Boolean = false, val booting: Boolean = false, val me: Profile? = null,
    val message: String? = null, val tab: Tab = Tab.FEED, val feed: List<Post> = emptyList(),
    val more: Boolean = true, val people: List<Profile> = emptyList(),
    val requests: List<Profile> = emptyList(), val profile: Profile? = null,
    val profilePosts: List<Post> = emptyList(), val profileMore: Boolean = true,
    val relationship: Follow? = null, val commentPost: Post? = null,
    val comments: List<Comment> = emptyList(), val savedOnly: Boolean = false,
    val settings: Boolean = false, val blocked: List<String> = emptyList(), val resetPassword: Boolean = false
)
class SparkViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<SparkApplication>()
    var api by mutableStateOf(app.config()?.let { SparkApi(it, app.secrets) }); private set
    var state by mutableStateOf(AppState()); private set
    private var actionJob: Job? = null
    private var searchJob: Job? = null
    private var sessionJob: Job? = null
    private var refreshJob: Job? = null
    init { watchSession(); if (api?.session?.value != null) act { enter() } }
    private fun client() = requireNotNull(api) { "Connect your Supabase project first." }
    private fun watchSession() {
        sessionJob?.cancel(); refreshJob?.cancel()
        val current = api ?: return
        sessionJob = viewModelScope.launch {
            current.session.collectLatest { session ->
                if (session == null && state.me != null) { searchJob?.cancel(); state = AppState(message = "Signed out.") }
            }
        }
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(240_000)
                if (current.session.value != null) try { current.accessToken() } catch (_: Exception) { /* A user action displays recoverable network errors. */ }
            }
        }
    }
    fun connect(url: String, key: String) {
        try {
            val config = Config(url, key).validate()
            app.saveConfig(config); api = SparkApi(config, app.secrets); watchSession()
        } catch (e: Exception) { state = state.copy(message = e.message) }
    }
    fun disconnect() { api?.clearLocalSession(); app.clearConfig(); api = null; sessionJob?.cancel(); refreshJob?.cancel(); state = AppState() }
    private fun act(block: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true)
        actionJob = viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = state.copy(message = e.message ?: "Something went wrong. Please try again.")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }
    fun dismissMessage() { state = state.copy(message = null) }
    fun showMessage(text: String) { state = state.copy(message = text) }
    fun login(email: String, password: String, signup: Boolean) = act {
        if (signup && !client().signUp(email, password)) state = state.copy(message = "Check your email to confirm your account, then sign in.")
        else { if (!signup) client().login(email, password); enter() }
    }
    fun forgot(email: String) = act { client().recover(email); state = state.copy(message = "If this account exists, a reset link has been sent. Open it on this phone.") }
    fun recovery(code: String) {
        // A callback can arrive during session restoration. Queue it, do not discard it.
        viewModelScope.launch {
            actionJob?.join()
            if (api?.resetReady() == true) { state = state.copy(resetPassword = true); return@launch }
            act { client().finishRecovery(code); state = state.copy(resetPassword = true) }
        }
    }
    fun changePassword(password: String) = act { client().updatePassword(password); enter(); state = state.copy(resetPassword = false, message = "Password updated.") }
    private suspend fun enter() {
        client().accessToken()
        val me = client().ensureProfile()
        state = state.copy(me = me, resetPassword = client().resetReady())
        if (me.deletingAt != null) { state = state.copy(settings = true, message = "Account deletion is pending. Retry it in settings."); return }
        state = state.copy(feed = client().feed(), more = true)
    }
    fun refresh() = act { val posts = client().feed(); state = state.copy(feed = posts, more = posts.size == Rules.PAGE_SIZE) }
    fun moreFeed() = act {
        val last = state.feed.lastOrNull() ?: return@act
        val posts = client().feed(FeedCursor(last.createdAt, last.id))
        state = state.copy(feed = (state.feed + posts).distinctBy { it.id }, more = posts.size == Rules.PAGE_SIZE)
    }
    fun tab(tab: Tab) {
        if (state.busy) return
        state = state.copy(tab = tab, profile = null, settings = false, savedOnly = false)
        when (tab) { Tab.PROFILE -> state.me?.let(::openProfile); Tab.REQUESTS -> loadRequests(); else -> Unit }
    }
    fun search(term: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                state = state.copy(people = client().searchPeople(term).filter { it.id != state.me?.id })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = state.copy(message = e.message)
            }
        }
    }
    fun publish(uri: Uri, caption: String) = act {
        client().createPost(preparePhoto(app, uri), caption)
        state = state.copy(tab = Tab.FEED, feed = client().feed(), more = true, message = "Your moment is posted.")
    }
    private fun changePost(post: Post) {
        state = state.copy(feed = state.feed.map { if (it.id == post.id) post else it }, profilePosts = state.profilePosts.map { if (it.id == post.id) post else it })
    }
    fun like(post: Post) = act { client().like(post); changePost(post.copy(liked = !post.liked, likeCount = (post.likeCount + if (post.liked) -1 else 1).coerceAtLeast(0))) }
    fun save(post: Post) = act { client().savePost(post); changePost(post.copy(saved = !post.saved)); if (state.savedOnly && post.saved) state = state.copy(profilePosts = state.profilePosts.filterNot { it.id == post.id }) }
    fun remove(post: Post) = act {
        val cleaned = client().deletePost(post)
        state = state.copy(feed = state.feed.filterNot { it.id == post.id }, profilePosts = state.profilePosts.filterNot { it.id == post.id },
            message = if (cleaned) "Post deleted." else "Post hidden. Media cleanup needs a retry by the project administrator.")
    }
    fun openComments(post: Post) = act { state = state.copy(commentPost = post, comments = emptyList()); state = state.copy(comments = client().comments(post.id)) }
    fun closeComments() { state = state.copy(commentPost = null, comments = emptyList()) }
    fun comment(body: String, onSuccess: () -> Unit = {}) = act {
        val post = state.commentPost ?: return@act
        client().addComment(post.id, body)
        state = state.copy(comments = client().comments(post.id), commentPost = post.copy(commentCount = post.commentCount + 1))
        changePost(post.copy(commentCount = post.commentCount + 1))
        onSuccess()
    }
    fun removeComment(comment: Comment) = act {
        client().deleteComment(comment.id)
        state = state.copy(comments = state.comments.filterNot { it.id == comment.id })
        state.commentPost?.let { val updated = it.copy(commentCount = (it.commentCount - 1).coerceAtLeast(0)); state = state.copy(commentPost = updated); changePost(updated) }
    }
    fun openProfile(profile: Profile) = act {
        val actual = client().profiles(mapOf("id" to "eq.${profile.id}")).firstOrNull() ?: error("This profile is unavailable.")
        state = state.copy(profile = actual, profilePosts = emptyList(), relationship = null, savedOnly = false)
        val posts = client().feed(author = profile.id)
        val relation = if (profile.id == client().userId) null else client().relationship(profile.id)
        state = state.copy(profilePosts = posts, profileMore = posts.size == Rules.PAGE_SIZE, relationship = relation)
    }
    fun back() { state = state.copy(profile = null, settings = false, savedOnly = false); if (state.tab == Tab.PROFILE) state = state.copy(tab = Tab.FEED) }
    fun saved(value: Boolean) = act {
        val posts = client().feed(author = if (value) null else client().userId, savedOnly = value)
        state = state.copy(savedOnly = value, profilePosts = posts, profileMore = posts.size == Rules.PAGE_SIZE)
    }
    fun moreProfile() = act {
        val last = state.profilePosts.lastOrNull() ?: return@act
        val posts = client().feed(FeedCursor(last.createdAt, last.id), if (state.savedOnly) null else state.profile?.id, state.savedOnly)
        state = state.copy(profilePosts = (state.profilePosts + posts).distinctBy { it.id }, profileMore = posts.size == Rules.PAGE_SIZE)
    }
    fun follow() = act {
        val profile = state.profile ?: return@act
        client().follow(profile.id, state.relationship)
        val relation = client().relationship(profile.id)
        state = state.copy(relationship = relation, profilePosts = client().feed(author = profile.id), feed = client().feed())
    }
    fun loadRequests() = act { requests() }
    private suspend fun requests() {
        val ids = client().follows(true).filter { it.status == "pending" }.map { it.followerId }.take(40)
        state = state.copy(requests = if (ids.isEmpty()) emptyList() else client().profiles(mapOf("id" to "in.(${ids.joinToString(",")})")))
    }
    fun resolve(profile: Profile, accept: Boolean) = act { client().resolveFollow(profile.id, accept); requests() }
    fun block(profile: Profile) = act {
        client().block(profile.id)
        state = state.copy(profile = null, profilePosts = emptyList(), feed = state.feed.filterNot { it.authorId == profile.id },
            people = state.people.filterNot { it.id == profile.id }, message = "Account blocked. Manage blocks in settings.")
    }
    fun report(post: Post, reason: String) = act { client().report(post.id, reason); state = state.copy(message = "Report submitted for review.") }
    fun settings() = act { state = state.copy(settings = true, blocked = client().blockedIds()) }
    fun closeSettings() { state = state.copy(settings = false) }
    fun updateProfile(name: String, username: String, bio: String, privateAccount: Boolean, avatar: Uri?) = act {
        val oldPath = state.me?.avatarPath
        val newPath = avatar?.let { client().uploadAvatar(preparePhoto(app, it)) }
        val me = client().updateProfile(name, username, bio, privateAccount, newPath)
        state = state.copy(me = me, profile = if (state.profile?.id == me.id) me else state.profile, message = "Profile updated.")
        if (newPath != null && oldPath != null) try { client().removeObject("spark-avatars", oldPath) } catch (_: Exception) { /* Orphan audit covers interrupted cleanup. */ }
    }
    fun unblock(id: String) = act { client().unblock(id); state = state.copy(blocked = client().blockedIds()) }
    fun logout() = act { searchJob?.cancel(); try { client().logout() } finally { state = AppState() } }
    fun deleteAccount(password: String) = act {
        try {
            client().deleteAccount(password)
            state = AppState(message = "Your account and media have been deleted.")
        } catch (e: ApiException) {
            if (e.status == 503) try {
                client().profiles(mapOf("id" to "eq.${client().userId}")).firstOrNull()?.let { state = state.copy(me = it) }
            } catch (_: Exception) { /* Preserve the original retry message if the network is unavailable. */ }
            throw e
        }
    }
}
