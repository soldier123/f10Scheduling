package jobs;

import dto.BondSec;
import dto.IndexInfo;
import play.jobs.Job;
import play.jobs.On;

/**
 * 重新初始化股票数据
 * User: wenzhihong
 * Date: 13-3-14
 * Time: 上午10:36
 */
@On("0 40 23 * * ?")
public class SecInitDataJob extends Job {

    @Override
    public void doJob() throws Exception {
        BondSec.init();
        IndexInfo.init();
    }
}
