package com.jia.academy.app.assessment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PracticePrompt(val id:String,val question:String,val explanation:String,val options:List<String>,val correctOption:Int)
data class PracticeSessionState(
    val prompts:List<PracticePrompt>,val index:Int=0,val answers:Map<String,Int> = emptyMap(),
    val submitted:Boolean=false,val completed:Boolean=false,val reviewingMistakes:Boolean=false
){
    val current:PracticePrompt? get()=prompts.getOrNull(index)
    val score:Int get()=prompts.count{answers[it.id]==it.correctOption}
    val answeredCount:Int get()=answers.size
    val accuracyPercent:Int get()=if(prompts.isEmpty())0 else score*100/prompts.size
    /** Includes unanswered questions: finishing a session should expose every learning gap. */
    val missedPrompts:List<PracticePrompt> get()=prompts.filter{answers[it.id]!=it.correctOption}
}

/** UI-independent practice reducer; persistence/network sync remains owned by repositories. */
class PracticeSessionController(prompts:List<PracticePrompt>){
    private val mutable=MutableStateFlow(PracticeSessionState(prompts=prompts.distinctBy{it.id}))
    val state:StateFlow<PracticeSessionState> = mutable.asStateFlow()
    fun answer(option:Int){val s=mutable.value;val p=s.current?:return;if(s.submitted||s.completed||option !in p.options.indices)return;mutable.value=s.copy(answers=s.answers+(p.id to option))}
    fun submit(){val s=mutable.value;if(s.current==null||s.submitted||s.completed)return;mutable.value=s.copy(submitted=true)}
    fun next(){val s=mutable.value;if(!s.submitted||s.completed)return;if(s.index+1>=s.prompts.size)mutable.value=s.copy(completed=true)else mutable.value=s.copy(index=s.index+1,submitted=false)}
    fun showMistakeReview(){val s=mutable.value;if(s.completed&&s.missedPrompts.isNotEmpty())mutable.value=s.copy(reviewingMistakes=true)}
    fun returnToSummary(){mutable.value=mutable.value.copy(reviewingMistakes=false)}
    fun reset(){mutable.value=PracticeSessionState(prompts=mutable.value.prompts)}
}
