package com.tomasthrawat.freemodelsfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val items: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_OTHER = 1
    }

    class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.textMessage)
    }

    class OtherHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.textMessage)
        val role: TextView = view.findViewById(R.id.textRole)
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].role == "user") TYPE_USER else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            OtherHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is UserHolder -> holder.text.text = item.content
            is OtherHolder -> {
                holder.role.text = if (item.role == "tool") "🔧 Tool" else "🤖 Assistant"
                holder.text.text = item.content
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }
}
