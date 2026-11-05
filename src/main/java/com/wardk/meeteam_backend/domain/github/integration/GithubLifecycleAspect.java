package com.wardk.meeteam_backend.domain.github.integration;

import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccount;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubOAuthStateService.Validated;
import com.wardk.meeteam_backend.web.projectmember.dto.request.DeleteRequest;
import com.wardk.meeteam_backend.web.projectmember.dto.request.WithdrawRequest;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static com.wardk.meeteam_backend.domain.github.integration.GithubDomainEvents.*;

/** Runs INSIDE the origin transaction, after its validation succeeds. No GitHub calls occur here. */
@Aspect @Component @RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE-50)
@ConditionalOnProperty(prefix="integration.github.app",name="enabled",havingValue="true")
public class GithubLifecycleAspect {
    private final EntityManager em;
    private final ApplicationEventPublisher events;
    @Around("execution(* com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberServiceImpl.addMember(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberServiceImpl.deleteProjectMember(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.projectmember.service.ProjectMemberServiceImpl.withdraw(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.project.service.ProjectManagementServiceImpl.expelMember(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.project.service.ProjectCommandServiceImpl.delete(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.admin.AdminProjectActionService.expel(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.admin.AdminMemberActionService.withdraw(..)) || " +
            "execution(* com.wardk.meeteam_backend.global.auth.service.AuthService.withdraw(..)) || " +
            "execution(* com.wardk.meeteam_backend.global.auth.service.AuthService.deleteMember(..)) || " +
            "execution(* com.wardk.meeteam_backend.domain.github.account.GithubAccountService.connect(..))")
    public Object lifecycle(ProceedingJoinPoint call) throws Throwable {
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("GitHub lifecycle hooks require the origin transaction");
        var signature=(MethodSignature)call.getSignature();String owner=signature.getDeclaringType().getSimpleName();
        String method=signature.getName();Object[] args=call.getArgs();
        var removed=new ArrayList<MemberRemoved>();var deleted=new ArrayList<Long>();
        MemberJoined joined=null;AccountLinked linked=null;
        if(owner.equals("ProjectMemberServiceImpl")&&method.equals("addMember")) joined=new MemberJoined(number(args[0]),number(args[1]));
        else if(owner.equals("ProjectMemberServiceImpl")&&method.equals("deleteProjectMember")){
            var request=(DeleteRequest)args[0];removed.add(capture(request.getProjectId(),request.getMemberId()));
        }else if(owner.equals("ProjectMemberServiceImpl")&&method.equals("withdraw")){
            var request=(WithdrawRequest)args[0];
            var ids=em.createQuery("select m.id from Member m where m.email=:email and m.deletedAt is null",Long.class)
                    .setParameter("email",args[1]).setMaxResults(1).getResultList();
            if(!ids.isEmpty())removed.add(capture(request.getProjectId(),ids.get(0)));
        }else if(owner.equals("ProjectManagementServiceImpl")) removed.add(capture(number(args[0]),number(args[1])));
        else if(owner.equals("AdminProjectActionService")) removed.add(capture(number(args[1]),number(args[2])));
        else if(owner.equals("ProjectCommandServiceImpl"))deleted.add(number(args[0]));
        else if(owner.equals("GithubAccountService"))linked=new AccountLinked(((Validated)args[0]).memberId());
        else {
            long memberId=number(args[owner.equals("AdminMemberActionService")?1:0]);
            var projects=em.createQuery("select distinct pm.project.id from ProjectMember pm where pm.member.id=:member",Long.class)
                    .setParameter("member",memberId).getResultList();
            for(long projectId:projects)removed.add(capture(projectId,memberId));
            if(method.equals("deleteMember"))deleted.addAll(em.createQuery("select p.id from Project p where p.creator.id=:member",Long.class)
                    .setParameter("member",memberId).getResultList());
        }
        Object result=call.proceed();
        if(joined!=null)events.publishEvent(joined);
        if(linked!=null)events.publishEvent(linked);
        for(var event:removed)events.publishEvent(event);
        for(long projectId:deleted)events.publishEvent(new ProjectDeleted(projectId));
        return result;
    }
    private MemberRemoved capture(long projectId,long memberId){
        var account=em.find(MemberGithubAccount.class,memberId);
        return new MemberRemoved(projectId,memberId,account==null?null:account.getGithubUserId(),account==null?null:account.getLogin());
    }
    private static long number(Object value){return ((Number)value).longValue();}
}
