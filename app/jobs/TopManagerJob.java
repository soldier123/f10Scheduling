package jobs;

import com.google.common.collect.ListMultimap;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.topmanager.HoldShare;
import dto.topmanager.HoldingChange;
import dto.topmanager.TopManager;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理高管信息
 * User: panzhiwei
 * Date: 12-11-1
 * Time: 下午3:20
 */
@On("cron.TopManagerTrigger")
@JobDesc(desc = "处理高管信息, 放入到redis上")
public class TopManagerJob extends Job {
    private static final String[] redisKeys = {
            RedisKey.TopManager.topManagerP353,
            RedisKey.TopManager.topManagerP351,
            RedisKey.TopManager.topManagerP352,
            RedisKey.TopManager.leaveOffice,
            RedisKey.TopManager.holdShare,
            RedisKey.TopManager.holdingchange
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"高管信息");
        Gson gson = CommonUtils.createGson();
        StopWatch sw  = new StopWatch("高管定时任务");

        sw.start("高管人员信息");
        topManager(gson);
        sw.stop();

        sw.start("高管离职信息");
        leaveOffice(gson);
        sw.stop();

        sw.start("高管持股");
        holdShareTop1(gson);
        sw.stop();

        sw.start("高管持股变动");
        holdingchange(gson);
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    //高管数据
    private void topManager(Gson gson){
        String[] types = new String[]{"P351", "P352", "P353"}; //三种高管类型
        String[] keys = new String[] {RedisKey.TopManager.topManagerP351, RedisKey.TopManager.topManagerP352, RedisKey.TopManager.topManagerP353};
        String sqlTpl = SqlLoader.getSqlById("topManagerJob2");
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);

            for (int i=0; i < types.length; i++) {
                ListMultimap<Long, TopManager> topMangeListMap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                        new BeanMultiMapHandler<Long, TopManager>(TopManager.class, "institutionId"),
                        types[i]);

                for (Long institutionId : topMangeListMap.keySet()) {
                    //处理每一家公司
                    List<TopManager> list = new ArrayList<TopManager>(topMangeListMap.get(institutionId));
                    //这里使用压缩的, 因为包含 resume(简历) 字段
                    RedisUtil.setGsonWithCompress(keys[i] + institutionId, list, gson);
                }
            }
        }
    }

    //离职信息
    private void leaveOffice(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("leaveOffice");
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql  = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, TopManager> leaveListMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, TopManager>(TopManager.class, "institutionId"));
            for (Long institutionId : leaveListMap.keySet()) {
                //处理每一家公司
                List<TopManager> list = new ArrayList<TopManager>(leaveListMap.get(institutionId));
                RedisUtil.setGsonWithCompress(RedisKey.TopManager.leaveOffice + institutionId, list, gson);
            }
        }
    }

    //高管持股最近1期数据
    private void holdShareTop1(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("holdShareTop1");
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, HoldShare> shareListMap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<Long, HoldShare>(HoldShare.class, "institutionId"));

            for (Long institutionId : shareListMap.keySet()) {
                //处理每一家公司
                List<HoldShare> list = new ArrayList<HoldShare>(shareListMap.get(institutionId));
                RedisUtil.set(RedisKey.TopManager.holdShare + institutionId, list, gson);
            }
        }
    }

    //高管持股变动
    private void holdingchange(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("holdingchange");
        for (String symbolGroup : BondSec.secCodeGroupArr) {
            String sql = sqlTpl.replaceAll("#symbolGroup#", symbolGroup);
            ListMultimap<String, HoldingChange> changeListMap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<String, HoldingChange>(HoldingChange.class, "symbol"));

            for(String symbol : changeListMap.keySet()){ //处理每一家公司
                List<HoldingChange> list = new ArrayList<HoldingChange>(changeListMap.get(symbol));

                RedisUtil.set(RedisKey.TopManager.holdingchange + symbol, list, gson);
            }
        }
    }

}
