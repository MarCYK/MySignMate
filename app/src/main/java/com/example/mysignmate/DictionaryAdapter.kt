package com.example.mysignmate

import com.example.mysignmate.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.mysignmate.fragment.VideoFragment

class DictionaryAdapter(private val itemList: List<DictionaryItem>) : RecyclerView.Adapter<DictionaryAdapter.DictionaryViewHolder>() {

    private var filteredList: List<DictionaryItem> = itemList

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionaryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dictionary, parent, false)
        return DictionaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DictionaryViewHolder, position: Int) {
        val currentItem = filteredList[position]
        holder.word.text = currentItem.word
        holder.image.setImageResource(currentItem.imageResourceId)

        holder.itemView.setOnClickListener {
            val fragment = VideoFragment.newInstance(currentItem.videoUrl)
            fragment.show((holder.itemView.context as AppCompatActivity).supportFragmentManager, "video_dialog")
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return filteredList.size
    }

    inner class DictionaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val word: TextView = itemView.findViewById(R.id.sign_word)
        val image: ImageView = itemView.findViewById(R.id.img_sign)
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            itemList
        } else {
            itemList.filter { it.word.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
