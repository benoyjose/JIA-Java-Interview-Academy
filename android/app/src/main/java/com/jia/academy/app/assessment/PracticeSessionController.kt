package com.jia.academy.app.assessment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
data class PracticePrompt(val id:String,val question:String,val explanation:String,val options:List<String>,val correctOption:Int)
data class PracticeSessionState(val prompts:List<PracticePrompt>,val index:Int=0,val answers:Map<String,Int> = emptyMap(),val submitted:Boolean=false,val completed:Boolean=false){
 val current:PracticePrompt? get()=prompts.getOrNull(index)
 val score:Int get()=prompts.count{answers[it.id]==it.correctOption}
}
/** UI-independent reducer; persistence/network sync is delegated to repository integration. */
class PracticeSessionController(prompts:List<PracticePrompt>){
 private val mutable=MutableStateFlow(PracticeSessionState(prompts=prompts.distinctBy{it.id}))
 val state:StateFlow<PracticeSessionState> = mutable.asStateFlow()
 fun answer(option:Int){val s=mutable.value;val p=s.current?:return;if(s.submitted||option !in p.options.indices)return;mutable.value=s.copy(answers=s.answers + (p.id to option))}
 fun submit(){val s=mutable.value;if(s.current==null||s.submitted)return;mutable.value=s.copy(submitted=true)}
 fun next(){val s=mutable.value;if(!s.submitted)return;if(s.index+1>=s.prompts.size)mutable.value=s.copy(completed=true)else mutable.value=s.copy(index=s.index+1,submitted=false)}
 fun reset(){mutable.value=PracticeSessionState(prompts=mutable.value.prompts)}
}
