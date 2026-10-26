package local.practice;

import jakarta.servlet.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BoardController {
 private final JdbcTemplate db;
 private final BCryptPasswordEncoder encoder;
 public BoardController(JdbcTemplate db, BCryptPasswordEncoder encoder) { this.db=db; this.encoder=encoder; }
 public record Member(long id,String username,String displayName) {}
 public record Post(long id,String title,String body,long authorId,String author,String created,String updated) {}
 private String date(java.sql.Timestamp t) { return t.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
 private final RowMapper<Member> memberMapper=(r,n)->new Member(r.getLong("id"),r.getString("username"),r.getString("display_name"));
 private RowMapper<Post> postMapper() { return (r,n)->new Post(r.getLong("id"),r.getString("title"),r.getString("body"),r.getLong("author_id"),r.getString("display_name"),date(r.getTimestamp("created_at")),date(r.getTimestamp("updated_at"))); }
 private Member current(HttpSession session) { return (Member)session.getAttribute("member"); }
 @ModelAttribute void globals(Model m,HttpSession s) { m.addAttribute("current",current(s)); m.addAttribute("version","0.1.0"); }
 private Post post(long id) { return db.query("SELECT p.*,m.display_name FROM posts p JOIN members m ON m.id=p.author_id WHERE p.id=?",postMapper(),id).stream().findFirst().orElse(null); }
 private String loginRequired(RedirectAttributes r) { r.addFlashAttribute("error","로그인이 필요합니다."); return "redirect:/login"; }
 private String failure(HttpServletResponse r,Model m,int status,String text) { r.setStatus(status);m.addAttribute("error",text);return "problem"; }
 @GetMapping("/") String home() { return "redirect:/posts"; }
 @GetMapping("/posts") String list(Model m) { m.addAttribute("posts",db.query("SELECT p.*,m.display_name FROM posts p JOIN members m ON m.id=p.author_id ORDER BY p.id ASC",postMapper())); return "list"; }
 @GetMapping("/signup") String signup(HttpSession s) { return current(s)==null?"signup":"redirect:/posts"; }
 @PostMapping("/signup") String register(@RequestParam String username,@RequestParam String password,@RequestParam String confirmation,@RequestParam String displayName,Model m,RedirectAttributes r,HttpSession s) {
  if(current(s)!=null) return "redirect:/posts";
  username=username.strip();displayName=displayName.strip();
  m.addAttribute("username",username);m.addAttribute("displayName",displayName);
  String error=null;
  if(!username.matches("[a-z0-9]{4,20}")) error="아이디는 영문 소문자와 숫자 4~20자로 입력하세요.";
  else if(db.queryForObject("SELECT COUNT(*) FROM members WHERE username=?",Integer.class,username)>0) error="이미 사용 중인 아이디입니다.";
  else if(password.length()<8 || password.length()>32 || password.codePoints().anyMatch(c -> Character.isWhitespace(c)||Character.isSpaceChar(c)) || !password.matches("(?s).*[a-zA-Z].*") || !password.matches("(?s).*[0-9].*")) error="비밀번호는 영문자와 숫자를 포함한 공백 없는 8~32자로 입력하세요.";
  else if(displayName.length()<2 || displayName.length()>20) error="표시 이름은 2~20자로 입력하세요.";
  if(error!=null) { m.addAttribute("error",error);return "signup"; }
  try { db.update("INSERT INTO members(username,password,display_name) VALUES(?,?,?)",username,encoder.encode(password),displayName); }
  catch(DuplicateKeyException e) { m.addAttribute("error","이미 사용 중인 아이디입니다.");return "signup"; }
  r.addFlashAttribute("notice","회원가입이 완료되었습니다.");return "redirect:/login";
 }
 @GetMapping("/login") String login(HttpSession s) { return current(s)==null?"login":"redirect:/posts"; }
 @PostMapping("/login") String authenticate(@RequestParam String username,@RequestParam String password,HttpServletRequest req,Model m) {
  if(current(req.getSession())!=null) return "redirect:/posts";
  username=username.strip();m.addAttribute("username",username);
  List<Member> found=db.query("SELECT * FROM members WHERE username=?",memberMapper,username);
  if(found.isEmpty() || !encoder.matches(password,db.queryForObject("SELECT password FROM members WHERE username=?",String.class,username))) {m.addAttribute("error","아이디 또는 비밀번호를 확인하세요.");return "login";}
  req.changeSessionId();req.getSession().setAttribute("member",found.getFirst());return "redirect:/posts";
 }
 @PostMapping("/logout") String logout(HttpSession s) {s.invalidate();return "redirect:/posts";}
 @GetMapping("/posts/new") String create(HttpSession s,Model m,RedirectAttributes r) {if(current(s)==null)return loginRequired(r); m.addAttribute("editing",false);return "editor";}
 @GetMapping("/posts/{id}") String detail(@PathVariable long id,Model m,HttpServletResponse response) {Post p=post(id);if(p==null)return failure(response,m,404,"게시글을 찾을 수 없습니다.");m.addAttribute("post",p);return "detail";}
 @GetMapping("/posts/{id}/edit") String edit(@PathVariable long id,HttpSession s,Model m,HttpServletResponse response,RedirectAttributes r) {
  if(current(s)==null)return loginRequired(r); Post p=post(id);
  if(p==null)return failure(response,m,404,"게시글을 찾을 수 없습니다.");
  if(current(s).id()!=p.authorId())return failure(response,m,403,"작성자만 변경할 수 있습니다.");
  m.addAttribute("editing",true);m.addAttribute("id",id);m.addAttribute("title",p.title());m.addAttribute("body",p.body());return "editor";
 }
 private String validate(String title,String body) {
  if(title.length()<1 || title.length()>101) return "제목은 1~100자로 입력하세요.";
  if(body.length()<1 || body.length()>2000) return "본문은 1~2000자로 입력하세요.";
  return null;
 }
 @PostMapping("/posts") String save(@RequestParam String title,@RequestParam String body,HttpSession s,Model m,RedirectAttributes r) {
  if(current(s)==null)return loginRequired(r);title=title.strip();body=body.replace("\r\n","\n").replace('\r','\n').strip();
  String error=validate(title,body);if(error!=null){m.addAttribute("error",error);m.addAttribute("editing",false);m.addAttribute("title",title);m.addAttribute("body",body);return "editor";}
  final String t=title,b=body;KeyHolder key=new GeneratedKeyHolder();
  db.update(con->{var ps=con.prepareStatement("INSERT INTO posts(title,body,author_id,created_at,updated_at) VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",new String[]{"id"});ps.setString(1,t);ps.setString(2,b);ps.setLong(3,current(s).id());return ps;},key);
  r.addFlashAttribute("notice","게시글을 작성했습니다.");return "redirect:/posts/"+key.getKey().longValue();
 }
 @PostMapping("/posts/{id}/edit") String update(@PathVariable long id,@RequestParam String title,@RequestParam String body,HttpSession s,Model m,HttpServletResponse response,RedirectAttributes r) {
  if(current(s)==null)return loginRequired(r);Post p=post(id);
  if(p==null)return failure(response,m,404,"게시글을 찾을 수 없습니다.");if(p.authorId()!=current(s).id())return failure(response,m,403,"작성자만 변경할 수 있습니다.");
  title=title.strip();body=body.replace("\r\n","\n").replace('\r','\n').strip();String error=validate(title,body);
  if(error!=null){m.addAttribute("error",error);m.addAttribute("editing",true);m.addAttribute("id",id);m.addAttribute("title",title);m.addAttribute("body",body);return "editor";}
  db.update("UPDATE posts SET title=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",title,id);
  r.addFlashAttribute("notice","게시글을 수정했습니다.");return "redirect:/posts/"+id;
 }
 @PostMapping("/posts/{id}/delete") String delete(@PathVariable long id,HttpSession s,Model m,HttpServletResponse response,RedirectAttributes r) {
  if(current(s)==null)return loginRequired(r);Post p=post(id);if(p==null)return failure(response,m,404,"게시글을 찾을 수 없습니다.");if(p.authorId()!=current(s).id())return failure(response,m,403,"작성자만 변경할 수 있습니다.");
  db.update("DELETE FROM posts WHERE id=?",id);r.addFlashAttribute("notice","게시글을 삭제했습니다.");return "redirect:/posts";
 }
}
