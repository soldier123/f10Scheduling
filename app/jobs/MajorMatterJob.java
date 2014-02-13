package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.majormatter.BlockTrade;
import dto.majormatter.Guarantee;
import dto.majormatter.Violation;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.List;

/**
 * 处理重大事件
 * User: panzhiwei
 * Date: 12-11-16
 * Time: 下午5:34
 */
@On("cron.MajorMatterTrigger")
@JobDesc(desc = "重大事项信息")
public class MajorMatterJob extends Job {
    private static final String[] redisKeys = {
            RedisKey.MajorMatter.violation,
            RedisKey.MajorMatter.guarantee,
            RedisKey.MajorMatter.blocktrade
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"重大事件");
        Gson gson = CommonUtils.createGson();
        StopWatch sw = new StopWatch("重大事项");
        sw.start("对外担保");
        guarantee();
        sw.stop();

        sw.start("违规处理");
        violations(gson);
        sw.stop();
        
        sw.start("大宗交易");
        blocktrade();
        sw.stop();
        
        Logger.info(sw.prettyPrint());
    }

    //违规处理
    private void violations(Gson gson) {
        String sqlTpl = SqlLoader.getSqlById("violations");
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, Violation> infos = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, Violation>(Violation.class, "institutionId"));
            for (Long institutionId : infos.keySet()) {
                RedisUtil.set(RedisKey.MajorMatter.violation + institutionId, Lists.newArrayList(infos.get(institutionId)), gson);
            }
        }
    }

    //对外担保
    private void guarantee() {
        String sqlTpl = SqlLoader.getSqlById("guarantee");
        Gson gson = CommonUtils.createGson();
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, Guarantee> infos = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, Guarantee>(Guarantee.class, "institutionId"));
            for (Long institutionId : infos.keySet()) {
                RedisUtil.set(RedisKey.MajorMatter.guarantee + institutionId, Lists.newArrayList(infos.get(institutionId)), gson);
            }
        }
    }

    //大宗交易
    private void blocktrade(){
        Gson gson = CommonUtils.createGson();
        String blockTradeSecuritySql = SqlLoader.getSqlById("blockTradeSecurity");
        String blocktradeSql = SqlLoader.getSqlById("blocktrade");
        List<Long> secIds = ExtractDbUtil.queryExtractDbWithHandler(blockTradeSecuritySql, new ColumnListHandler<Long>("secId"));
        for (Long id : secIds) {
            List<BlockTrade> tradeList = ExtractDbUtil.queryExtractDBBeanList(blocktradeSql, BlockTrade.class, id);
            String key = RedisKey.MajorMatter.blocktrade + id;
            RedisUtil.rpushWithDel(key, tradeList, gson);
        }
    }
}
