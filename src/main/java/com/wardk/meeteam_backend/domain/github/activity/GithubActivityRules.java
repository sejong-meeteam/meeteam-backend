package com.wardk.meeteam_backend.domain.github.activity;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
public final class GithubActivityRules {
 public static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
 private GithubActivityRules(){}
 public static LocalDate monday(Instant now){return now.atZone(SEOUL).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));}
 public static String level(Instant pushed,Instant now){if(pushed==null)return "DORMANT";return !pushed.isBefore(now.minus(Duration.ofDays(7)))?"ACTIVE":!pushed.isBefore(now.minus(Duration.ofDays(30)))?"MODERATE":"DORMANT";}
 public static Instant start(LocalDate day){return day.atStartOfDay(SEOUL).toInstant();}
 public static List<Integer> weeks(Collection<Instant> commits,Instant now){LocalDate first=monday(now).minusWeeks(7);int[] counts=new int[8];for(Instant commit:commits){long days=java.time.temporal.ChronoUnit.DAYS.between(first,commit.atZone(SEOUL).toLocalDate());if(days>=0&&days<56&&!commit.isAfter(now))counts[(int)(days/7)]++;}return Arrays.stream(counts).boxed().toList();}
 public static boolean stale(boolean draft,String review,Instant updated,Instant now){return !draft&&!"APPROVED".equals(review)&&updated!=null&&updated.isBefore(now.minus(Duration.ofHours(72)));}
 public static boolean mayCloseAbsent(int number,Instant updatedAt,Set<Integer> fetched,Instant syncStarted){return !fetched.contains(number)&&updatedAt!=null&&!updatedAt.isAfter(syncStarted);}
 public record Language(String name,double percentage){}
 public static List<Language> languages(Map<String,Long> bytes){var entries=bytes.entrySet().stream().filter(e->e.getValue()>0).sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).toList();long total=entries.stream().mapToLong(Map.Entry::getValue).sum();if(total==0)return List.of();var result=new ArrayList<Language>();long consumed=0;int tenths=0;for(int i=0;i<Math.min(5,entries.size());i++){var e=entries.get(i);int n=Math.min(1000-tenths,(int)Math.round(e.getValue()*1000.0/total));consumed+=e.getValue();tenths+=n;result.add(new Language(e.getKey(),n/10.0));}if(consumed<total)result.add(new Language("Other",(1000-tenths)/10.0));else{int i=result.size()-1;var last=result.get(i);result.set(i,new Language(last.name(),Math.round((last.percentage()+(1000-tenths)/10.0)*10)/10.0));}return result;}
}
