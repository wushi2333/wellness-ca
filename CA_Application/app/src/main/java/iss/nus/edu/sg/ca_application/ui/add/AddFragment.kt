// Author: Wang Songyu
package iss.nus.edu.sg.ca_application.ui.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import iss.nus.edu.sg.ca_application.R

class AddFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_add, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.cardAddSleep).setOnClickListener {
            AddSleepSheet().show(parentFragmentManager, "add_sleep")
        }
        view.findViewById<View>(R.id.cardAddExercise).setOnClickListener {
            AddExerciseSheet().show(parentFragmentManager, "add_exercise")
        }
    }
}
