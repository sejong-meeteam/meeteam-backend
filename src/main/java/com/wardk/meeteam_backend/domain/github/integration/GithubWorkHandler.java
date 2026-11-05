package com.wardk.meeteam_backend.domain.github.integration;
public interface GithubWorkHandler {
    String kind();
    void handle(Job job);
    record Job(long id,String kind,Long projectId,Long memberId,long bindingVersion,String payload,int attempt) {
        public Job(long id,String kind,Long projectId,Long memberId,long bindingVersion,String payload) {
            this(id,kind,projectId,memberId,bindingVersion,payload,0);
        }
        @Override public String toString() { return "GithubJob[id="+id+", kind="+kind+"]"; }
    }
}
