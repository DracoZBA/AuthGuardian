package com.example.authguardian.ui.guardian.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.authguardian.R

class EpisodeAdapter(private val episodes: List<Episode>) :
        RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val timeText: TextView = itemView.findViewById(R.id.episodeTime)
    val reasonText: TextView = itemView.findViewById(R.id.episodeReason)
}

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
    val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
    return EpisodeViewHolder(view)
}

override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
    val episode = episodes[position]
    holder.timeText.text = "Hora: ${episode.time}"
    holder.reasonText.text = "Razón: ${episode.reason}"
}

override fun getItemCount() = episodes.size
}
