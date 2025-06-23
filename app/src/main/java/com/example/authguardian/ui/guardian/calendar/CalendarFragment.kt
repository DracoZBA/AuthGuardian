package com.example.authguardian.ui.guardian.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.authguardian.R

class CalendarFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EpisodeAdapter
    private val allEpisodes = mutableMapOf<String, MutableList<Episode>>()
    private var selectedDate = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        val addButton = view.findViewById<Button>(R.id.addEpisodeButton)
        recyclerView = view.findViewById(R.id.episodeRecyclerView)

        // RecyclerView config
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = EpisodeAdapter(emptyList())
        recyclerView.adapter = adapter

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = "$dayOfMonth/${month + 1}/$year"
            updateEpisodeList()
        }

        addButton.setOnClickListener {
            if (selectedDate.isNotEmpty()) {
                showAddEpisodeDialog()
            } else {
                Toast.makeText(requireContext(), "Selecciona una fecha primero", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun showAddEpisodeDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_episode, null)
        val timeInput = dialogView.findViewById<EditText>(R.id.timeInput)
        val reasonInput = dialogView.findViewById<EditText>(R.id.reasonInput)

        AlertDialog.Builder(context)
            .setTitle("Agregar Episodio")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val episode = Episode(
                    time = timeInput.text.toString(),
                    reason = reasonInput.text.toString(),
                    date = selectedDate
                )
                val episodes = allEpisodes.getOrPut(selectedDate) { mutableListOf() }
                episodes.add(episode)
                updateEpisodeList()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateEpisodeList() {
        val episodes = allEpisodes[selectedDate] ?: emptyList()
        adapter = EpisodeAdapter(episodes)
        recyclerView.adapter = adapter
    }
}
