package com.wardk.meeteam_backend.github.integration;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** H2 MySQL-compatibility smoke test. This is explicitly NOT a real MySQL migration verification. */
class GithubMigrationContractTest {
 @Test void exactV5V6ScriptsPreserveRowsAndEnforceIdentityAndWorkConstraints()throws Exception{
  try(var db=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE")){
   run(db,"create table member(member_id bigint primary key, name varchar(80));create table project(project_id bigint primary key, name varchar(80));"+
     "insert into member values(1,'기존 회원'),(2,'다른 회원');insert into project values(1,'기존 프로젝트'),(2,'다른 프로젝트')");
   for(String file:List.of("V5__github_account_link.sql","V6__github_project_integration.sql")){
    try(var input=getClass().getResourceAsStream("/db/migration/"+file)){assertThat(input).isNotNull();run(db,new String(input.readAllBytes(),StandardCharsets.UTF_8));}
   }
   assertThat(scalar(db,"select name from member where member_id=1")).isEqualTo("기존 회원");
   assertThat(scalar(db,"select github_link_version from member where member_id=1")).isEqualTo("0");
   run(db,"insert into member_github_account values(1,101,'first','',CURRENT_TIMESTAMP)");
   assertThatThrownBy(()->run(db,"insert into member_github_account values(2,101,'duplicate','',CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
   run(db,"insert into github_project_link(project_id,authorized_repository_ids,github_repository_id) values(1,'[]',1001)");
   assertThatThrownBy(()->run(db,"insert into github_project_link(project_id,authorized_repository_ids,github_repository_id) values(2,'[]',1001)")).isInstanceOf(SQLException.class);
   run(db,"insert into github_work(kind,binding_version,payload,dedup_key,status,attempts,next_attempt_at,created_at) values('WEBHOOK',0,'{}','delivery:unique','PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   assertThatThrownBy(()->run(db,"insert into github_work(kind,binding_version,payload,dedup_key,status,attempts,next_attempt_at,created_at) values('WEBHOOK',0,'{}','delivery:unique','PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
   assertThat(scalar(db,"select count(*) from github_collaborator_grant")).isEqualTo("0");
   run(db,"delete from member where member_id=1");assertThat(scalar(db,"select count(*) from member_github_account")).isEqualTo("0");
   run(db,"delete from project where project_id=1");assertThat(scalar(db,"select count(*) from github_project_link")).isEqualTo("0");
   assertThat(scalar(db,"select count(*) from member")).isEqualTo("1");assertThat(scalar(db,"select count(*) from project")).isEqualTo("1");
  }
 }
 static void run(Connection db,String sql)throws SQLException{
  String clean=sql.replaceAll("(?m)--[^\\r\\n]*","");
  for(String statement:clean.split(";"))if(!statement.isBlank())try(var call=db.createStatement()){call.execute(statement.trim());}
 }
 static String scalar(Connection db,String sql)throws SQLException{try(var statement=db.createStatement();var rows=statement.executeQuery(sql)){rows.next();return rows.getString(1);}}
}
