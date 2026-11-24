<?php
declare(strict_types=1);
namespace App\Http\Controllers\Api\V1;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Carbon\CarbonImmutable;
final class LearnerCommitmentController {
 public function show(Request $request):JsonResponse {
  $user=$request->attributes->get('jia_user');abort_if($user===null,401,'Unauthenticated');
  $input=$request->validate(['timezone'=>['required','timezone'],'goalMinutes'=>['sometimes','integer','min:5','max:240']]);
  $zone=$input['timezone'];$today=CarbonImmutable::now($zone)->toDateString();
  $row=DB::table('learner_daily_commitments')->where('user_id',$user->id)->where('local_date',$today)->first();
  $completed=(int)($row->completed_minutes??0);$goal=(int)($row->goal_minutes??$input['goalMinutes']??30);
  $dates=DB::table('learner_daily_commitments')->where('user_id',$user->id)->where('completed_minutes','>=',DB::raw('goal_minutes'))->orderByDesc('local_date')->limit(400)->pluck('local_date')->map(fn($d)=>(string)$d)->all();
  $streak=0;$cursor=CarbonImmutable::now($zone)->startOfDay();$set=array_fill_keys($dates,true);
  if(isset($set[$cursor->toDateString()])||($row===null&&isset($set[$cursor->subDay()->toDateString()]))) {if(!isset($set[$cursor->toDateString()]))$cursor=$cursor->subDay();while(isset($set[$cursor->toDateString()])){$streak++;$cursor=$cursor->subDay();}}
  $history = DB::table('learner_daily_commitments')->where('user_id',$user->id)->where('local_date','>=',$cursor->subDays(6)->toDateString())->where('local_date','<=',$today)->orderByDesc('local_date')->limit(7)->get(['goal_minutes','completed_minutes'])->map(fn($item)=>['goal_minutes'=>(int)$item->goal_minutes,'completed_minutes'=>(int)$item->completed_minutes])->all();
  $recommendation = (new LearnerGoalProgression())->recommend($goal,$history);
  return response()->json(['date'=>$today,'timezone'=>$zone,'goalMinutes'=>$goal,'completedMinutes'=>$completed,'goalMet'=>$completed>=$goal,'streakDays'=>$streak,'goalRecommendation'=>$recommendation]);
 }
 public function record(Request $request):JsonResponse {
  $user=$request->attributes->get('jia_user');abort_if($user===null,401,'Unauthenticated');
  $v=$request->validate(['timezone'=>['required','timezone'],'minutes'=>['required','integer','min:1','max:240'],'sessionId'=>['required','uuid'],'goalMinutes'=>['sometimes','integer','min:5','max:240']]);
  $date=CarbonImmutable::now($v['timezone'])->toDateString();$id=Str::uuid()->toString();$goal=$v['goalMinutes']??30;
  DB::transaction(function()use($user,$v,$date,$id,$goal):void{
   $exists=DB::table('learner_daily_commitments')->where('user_id',$user->id)->where('local_date',$date)->lockForUpdate()->first();
   if($exists===null)DB::table('learner_daily_commitments')->insert(['id'=>$id,'user_id'=>$user->id,'local_date'=>$date,'timezone'=>$v['timezone'],'goal_minutes'=>$goal,'completed_minutes'=>min(240,$v['minutes']),'sessions_completed'=>1,'created_at'=>now(),'updated_at'=>now()]);
   else DB::table('learner_daily_commitments')->where('id',$exists->id)->update(['timezone'=>$v['timezone'],'goal_minutes'=>$goal,'completed_minutes'=>min(1440,(int)$exists->completed_minutes+$v['minutes']),'sessions_completed'=>(int)$exists->sessions_completed+1,'updated_at'=>now()]);
  });
  return $this->show($request);
 }
}
