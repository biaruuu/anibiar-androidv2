package com.anibiar.app.social

data class FriendActivity(val user: UserProfile, val mediaId: Int, val title: String, val image: String?, val episode: Int, val progress: Float, val at: Long)
data class SharedList(val id: String, val name: String, val ownerId: String, val mine: Boolean)
data class ListItem(val mediaId: Int, val title: String, val image: String?, val addedBy: String, val votes: Int, val iVoted: Boolean)
