package jobs;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.relatedstock.HolderItem;
import dto.relatedstock.SameInduItem;
import dto.relatedstock.SameShareHolder;
import dto.stockholdercapital.StockHolderDto;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 关联个股
 * User: liangbing
 * Date: 12-11-29
 * Time: 下午3:37
 */
@On("cron.RelatedStockTrigger")
@JobDesc(desc = "关联个股定时任务")
public class RelatedStockJob extends Job {
    private static final String[] redisKeys = {
        RedisKey.RelatedStock.top10Holders,
        RedisKey.RelatedStock.sameshareholder,
        RedisKey.RelatedStock.sameindustryholder
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"关联个股");
        StopWatch sw = new StopWatch("关联个股");

        sw.start("同股东持股");
        sameShareHolder();
        sw.stop();

        sw.start("同行业各股eps");
        sameInduEps();
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    //同行业各股eps
    private void sameInduEps() {
        Gson gson = CommonUtils.createGson();
        String sql = SqlLoader.getSqlById("sameInduEps");
        for (String indu2Code : BondSec.fetchLevelTwoInduCodeList()) { //证监会行业二级代码
            List<SameInduItem> list = ExtractDbUtil.queryExtractDBBeanList(sql, SameInduItem.class, indu2Code);
            RedisUtil.set(RedisKey.RelatedStock.sameindustryholder + indu2Code, list, gson);
        }
    }

    //同股东持股
    private void sameShareHolder() {
        Gson gson = CommonUtils.createGson();
        Type type = new TypeToken<List<StockHolderDto>>(){}.getType();
        for (Long institutionId : BondSec.institutionIdToCodeMap.keySet()) { //每个公司单独处理
            List<StockHolderDto> top10s = RedisUtil.fetchFromRedis(RedisKey.StockHolderCapital.stockHolderTop10 + institutionId, type, gson);//10大股东
            String sqlId = "top10SameShareHolder";
            List<SameShareHolder> top10Lists = fetchHolderData(institutionId, top10s, sqlId);

            List<SameShareHolder> all = Lists.newLinkedList(top10Lists);

            //10大股东(合并后的)
            List<HolderItem> holderItemList = Lists.newLinkedList();
            if(top10s != null){
                for (StockHolderDto t : top10s) {
                    HolderItem i = new HolderItem();
                    i.id = t.holderId;
                    i.name = t.holderName;
                    holderItemList.add(i);
                }
            }

            RedisUtil.set(RedisKey.RelatedStock.top10Holders + institutionId, holderItemList, gson);

            if(all.size() > 0){
                RedisUtil.set(RedisKey.RelatedStock.sameshareholder + institutionId, all, gson);
            }
        }
    }

    private List<SameShareHolder> fetchHolderData(Long institutionId, List<StockHolderDto> list, String sqlId) {
        if (list != null && list.size() > 0) {
            StringBuilder idsb = new StringBuilder();
            for (StockHolderDto t : list) {
                if(t.holderId != 0){
                    idsb.append(",");
                    idsb.append(t.holderId);
                }
            }

            if (idsb.length() > 0) {
                String ids = idsb.substring(1);
                String endDate = CommonUtils.getFormatDate("yyyy-MM-dd", list.get(0).endDate);
                String sql = SqlLoader.getSqlById(sqlId).replaceAll("#ids#", ids);
                List<SameShareHolder> top10Holders = ExtractDbUtil.queryExtractDBBeanList(sql, SameShareHolder.class, institutionId, endDate);
                return top10Holders;
            }else{
                return Lists.newArrayListWithCapacity(0);
            }
        }
        return Lists.newArrayListWithCapacity(0);
    }



}
