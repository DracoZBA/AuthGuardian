package com.example.authguardian.ui.guardian.geofence

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.authguardian.databinding.ItemGeofenceBinding
import com.example.authguardian.models.GeofenceArea

class GeofenceAdapter(private val onDeleteClick: (String) -> Unit) :
    ListAdapter<GeofenceArea, GeofenceAdapter.GeofenceViewHolder>(GeofenceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GeofenceViewHolder {
        val binding = ItemGeofenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GeofenceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GeofenceViewHolder, position: Int) {
        val geofence = getItem(position)
        holder.bind(geofence, onDeleteClick)
    }

    class GeofenceViewHolder(private val binding: ItemGeofenceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(geofence: GeofenceArea, onDeleteClick: (String) -> Unit) {
            binding.tvGeofenceItemName.text = geofence.name
            binding.tvGeofenceItemCoords.text = "Lat: %.4f, Lng: %.4f, Rad: %.0fm".format(
                geofence.latitude,  // <--- CAMBIO AQUÍ
                geofence.longitude, // <--- CAMBIO AQUÍ
                geofence.radius     // El radio ya está bien
            )
            binding.btnRemoveGeofence.setOnClickListener {
                onDeleteClick(geofence.id)
            }
        }
    }
}

class GeofenceDiffCallback : DiffUtil.ItemCallback<GeofenceArea>() {
    override fun areItemsTheSame(oldItem: GeofenceArea, newItem: GeofenceArea): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: GeofenceArea, newItem: GeofenceArea): Boolean {
        return oldItem == newItem
    }
}