package com.jia.academy.coding.api;\nimport com.jia.academy.coding.service.CodingSubmissionLifecyclePolicy;
import com.jia.academy.security.CurrentUser;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.time.Instant;import java.util.*;
import org.springframework.http.HttpStatus;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import org.springframework.jdbc.core.JdbcTemplate;
@RestController @RequestMapping("/api/v1/coding-submissions")
public class CodingSubmissionController {
 private static final UUID DEFAULT_ACADEMY=UUID.fromString("50000000-0000-0000-0000-000000000001");private final JdbcTemplate jdbc;
 public CodingSubmissionController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @PostMapping @ResponseStatus(HttpStatus.ACCEPTED) @Transactional
 public Map<String,Object> submit(@AuthenticationPrincipal CurrentUser user,@RequestBody Map<String,Object> request){
  requireUser(user);UUID exercise=uuid(request.get("exerciseId"),"exerciseId"),key=uuid(request.get("idempotencyKey"),"idempotencyKey");
  if(!"JAVA".equals(string(request.get("language"),"language")))throw bad("Only JAVA submissions are supported");
  String source=string(request.get("sourceCode"),"sourceCode");if(source.isBlank())throw bad("sourceCode must not be blank");
  if(source.getBytes(StandardCharsets.UTF_8).length>65536)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"sourceCode exceeds 64 KiB");
  UUID academy=academyId(user);
  List<Map<String,Object>> old=jdbc.query("SELECT id,status,created_at FROM coding_submissions WHERE user_id=? AND academy_id=? AND idempotency_key=?",
   (rs,n)->Map.of("submissionId",rs.getObject("id",UUID.class),"status",rs.getString("status"),"createdAt",rs.getTimestamp("created_at").toInstant().toString()),user.id(),academy,key);
  if(!old.isEmpty()){Map<String,Object> result=new LinkedHashMap<>(old.getFirst());result.put("idempotentReplay",true);result.put("executionEnabled",false);return result;}
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO coding_submissions(id,user_id,academy_id,exercise_id,language,source_code,source_sha256,idempotency_key,status,execution_mode) VALUES(?,?,?,?,?,?,?,?,'QUEUED','DISABLED')",
   id,user.id(),academy,exercise,"JAVA",source,sha256(source),key);
  return Map.of("submissionId",id,"status","QUEUED","createdAt",Instant.now().toString(),"executionEnabled",false,"idempotentReplay",false);
 }
 @GetMapping("/{submissionId}") public Map<String,Object> status(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID submissionId){
  requireUser(user);List<Map<String,Object>> rows=jdbc.query("SELECT id,status,created_at,updated_at,cancel_requested FROM coding_submissions WHERE id=? AND user_id=? AND academy_id=?",
   (rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("submissionId",rs.getObject("id",UUID.class));m.put("status",rs.getString("status"));m.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());m.put("updatedAt",rs.getTimestamp("updated_at").toInstant().toString());m.put("cancelRequested",rs.getBoolean("cancel_requested"));m.put("executionEnabled",false);m.put("message","Sandbox worker is disabled; source has not been executed.");return m;},submissionId,user.id(),academyId(user));
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Submission not found");return rows.getFirst();
 }
 @PostMapping("/{submissionId}/cancel") @Transactional public Map<String,Object> cancel(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID submissionId){
  requireUser(user);List<String> states=jdbc.query("SELECT status FROM coding_submissions WHERE id=? AND user_id=? AND academy_id=? FOR UPDATE",(rs,n)->rs.getString(1),submissionId,user.id(),academyId(user));
  if(states.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Submission not found");String status=states.getFirst();
  var decision=lifecycle.cancellation(status);
  if(decision.action()==CodingSubmissionLifecyclePolicy.Action.CANCEL_QUEUED)jdbc.update("UPDATE coding_submissions SET status='CANCELLED',updated_at=now() WHERE id=? AND status='QUEUED'",submissionId);
  else if(decision.action()==CodingSubmissionLifecyclePolicy.Action.REQUEST_RUNNING_CANCELLATION)jdbc.update("UPDATE coding_submissions SET cancel_requested=TRUE,updated_at=now() WHERE id=?",submissionId);
  return Map.of("submissionId",submissionId,"status",decision.responseStatus(),"cancelRequested",decision.cancelRequested(),"cancellationAction",decision.action().name());
 }
 private static UUID academyId(CurrentUser u){return u.academyIds()==null||u.academyIds().isEmpty()?DEFAULT_ACADEMY:u.academyIds().getFirst();}
 private static void requireUser(CurrentUser u){if(u==null||u.id()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");}
 private static UUID uuid(Object v,String f){if(v instanceof String s)try{return UUID.fromString(s);}catch(IllegalArgumentException ignored){}throw bad(f+" must be a UUID");}
 private static String string(Object v,String f){if(v instanceof String s&&!s.isBlank())return s;throw bad(f+" is required");}
 private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
 private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
