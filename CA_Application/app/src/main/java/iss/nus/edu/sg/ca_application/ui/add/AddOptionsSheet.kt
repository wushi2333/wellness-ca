// Author: Wang Songyu
package iss.nus.edu.sg.ca_application.ui.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import iss.nus.edu.sg.ca_application.R

class AddOptionsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_add_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.cardAddSleep).setOnClickListener {
            dismiss()
            AddSleepSheet().show(parentFragmentManager, "add_sleep")
        }
        view.findViewById<View>(R.id.cardAddExercise).setOnClickListener {
            dismiss()
            AddExerciseSheet().show(parentFragmentManager, "add_exercise")
        }
    }
}
