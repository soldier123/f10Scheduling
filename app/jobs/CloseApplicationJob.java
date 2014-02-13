package jobs;

import play.jobs.Job;
import util.MsgChangeRedis;

/**
 * 关闭应用程序时, 所要做的事情
 * User: wenzhihong
 * Date: 13-1-23
 * Time: 下午3:22
 */
public class CloseApplicationJob extends Job {
    @Override
    public void doJob() throws Exception {
        //销毁消息变化的redis连接
        MsgChangeRedis.destroy();
    }
}
