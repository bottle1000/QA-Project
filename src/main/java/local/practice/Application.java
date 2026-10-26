package local.practice;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@SpringBootApplication
public class Application {
 public static void main(String[] args) { SpringApplication.run(Application.class,args); }
 @Bean BCryptPasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
 @Bean org.springframework.transaction.support.TransactionTemplate transactions(org.springframework.transaction.PlatformTransactionManager m) { return new org.springframework.transaction.support.TransactionTemplate(m); }
 @Bean CommandLineRunner seed(JdbcTemplate db, BCryptPasswordEncoder encoder, org.springframework.transaction.support.TransactionTemplate tx) { return args -> tx.executeWithoutResult(status -> {
  if(db.queryForObject("SELECT COUNT(*) FROM seed_state",Integer.class)>0) return;
  for(String name: new String[]{"usera","userb"}) {
   db.update("INSERT INTO members(username,password,display_name) VALUES(?,?,?)",name,encoder.encode("Practice123!"),name.equals("usera")?"사용자 A":"사용자 B");
   Long id=db.queryForObject("SELECT id FROM members WHERE username=?",Long.class,name);
   for(int i=1;i<=2;i++) db.update("INSERT INTO posts(title,body,author_id,created_at,updated_at) VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",name+"의 게시글 "+i,"안녕하세요. 로컬 게시판에 오신 것을 환영합니다.\n자유롭게 글을 작성해 보세요.",id);
  }
  db.update("INSERT INTO seed_state VALUES(1)");
 }); }
}
