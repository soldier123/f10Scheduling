package controllers;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jobs.InitJobDataJob;
import jobs.JobDesc;
import play.jobs.Job;
import play.mvc.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * job任务管理
 * User: wenzhihong
 * Date: 12-12-26
 * Time: 上午10:19
 */
public class JobManage extends Controller {

    public static void list() {
        List<Class<?>> jobs = new ArrayList<Class<?>>(InitJobDataJob.jobs);
        List<Map<String, String>> detailList = Lists.newArrayList();
        for (Class<?> j : jobs) {
            Map<String, String> detail = Maps.newHashMap();
            detailList.add(detail);
            detail.put("desc", j.getAnnotation(JobDesc.class).desc());
            detail.put("className", j.getName());
        }


        render(detailList);
    }

    public static void execJob(String jobClassName) {
        boolean sucess = true;
        try {
            Class<?> aClass = Class.forName(jobClassName);
            boolean isJob = Job.class.isAssignableFrom(aClass);
            if (isJob) {
                Job j = (Job) aClass.newInstance();
                j.now();
            }
        } catch (ClassNotFoundException e) {
            sucess = false;
        } catch (InstantiationException e) {
            sucess = false;
        } catch (IllegalAccessException e) {
            sucess = false;
        }

        render(sucess);
    }
}
