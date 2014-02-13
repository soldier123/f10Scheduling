package jobs;

import dbutils.SqlLoader;
import dto.BondSec;
import dto.IndexInfo;
import models.LstTableInfo;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;

import java.util.ArrayList;
import java.util.List;

/**
 * 初始化job数据
 * User: wenzhihong
 * Date: 12-10-12
 * Time: 下午5:50
 */
@OnApplicationStart
public class InitJobDataJob extends Job {

    //任务集合
    public static List<Class<?>> jobs = new ArrayList<Class<?>>();

    private void initJob(){
        jobs.clear();
        for (Class clazz : Play.classloader.getAllClasses()) {
            if (Job.class.isAssignableFrom(clazz)) {
                if(clazz.isAnnotationPresent(JobDesc.class)){
                    jobs.add(clazz);
                }
            }
        }
    }

    @Override
    public void doJob() throws Exception {
        LstTableInfo.init();
        initJob();
    }
}
