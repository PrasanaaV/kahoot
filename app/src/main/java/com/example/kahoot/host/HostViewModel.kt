package com.example.kahoot.host

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.kahoot.models.Question

class HostViewModel : ViewModel() {
    private val _questions = MutableLiveData<List<Question>>(emptyList())
    val questions: LiveData<List<Question>> get() = _questions

    fun addQuestion(question: Question) {
        val updated = _questions.value?.toMutableList() ?: mutableListOf()
        updated.add(question)
        _questions.value = updated
    }

    fun clearQuestions() {
        _questions.value = emptyList()
    }
}
