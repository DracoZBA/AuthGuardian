package com.example.authguardian.ui.guardian.dashboard

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.authguardian.R
import com.example.authguardian.util.TestDataGenerator
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass.
 * Use the [DashboardFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class DashboardFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1) // Now accessible
            param2 = it.getString(ARG_PARAM2) // Now accessible
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dashboard4, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val generateDataButton: Button = view.findViewById(R.id.generate_test_data_button)

        generateDataButton.setOnClickListener {
            lifecycleScope.launch {
                Toast.makeText(requireContext(), "Generando datos de prueba...", Toast.LENGTH_SHORT).show()
                try {
                    TestDataGenerator.generateAllTestData()
                    Toast.makeText(requireContext(), "Datos de prueba generados exitosamente!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error al generar datos: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        // --- MOVE THESE CONSTANTS HERE ---
        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"
        // ----------------------------------

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment DashboardFragment.
         */
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            DashboardFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}