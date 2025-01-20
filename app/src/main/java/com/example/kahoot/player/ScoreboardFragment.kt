package com.example.kahoot.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.kahoot.R

class ScoreboardFragment : Fragment() {

    companion object {
        fun newInstance(quizId: String): ScoreboardFragment {
            val fragment = ScoreboardFragment()
            fragment.arguments = Bundle().apply {
                putString("quizId", quizId)
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scoreboard, container, false)
    }
}
